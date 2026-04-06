/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.os890.ds.addon.hibernate.impl;

import org.apache.deltaspike.core.util.ProxyUtils;
import org.apache.deltaspike.jpa.impl.entitymanager.EntityManagerMetadata;
import org.apache.deltaspike.jpa.impl.transaction.EnvironmentAwareTransactionStrategy;
import org.apache.deltaspike.jpa.impl.transaction.context.EntityManagerEntry;
import org.hibernate.FlushMode;
import org.hibernate.Session;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.interceptor.InvocationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * DeltaSpike transaction strategy that switches the Hibernate session to
 * {@link FlushMode#MANUAL} when {@code @Transactional(readOnly = true)}
 * is used, including support for nested transaction constellations.
 *
 * <p>This strategy automatically activates as a CDI {@link Alternative}
 * with priority {@code LIBRARY_BEFORE}.</p>
 */
@Dependent
@Priority(LIBRARY_BEFORE)
@Alternative
public class HibernateAwareTransactionStrategy extends EnvironmentAwareTransactionStrategy {

    @Inject
    private BeanManager beanManager;

    private static ThreadLocal<Stack<List<FlushModeEntry>>> flushModeEntryStack = new ThreadLocal<>();

    @Override
    public Object execute(InvocationContext invocationContext) throws Exception {
        Stack<List<FlushModeEntry>> currentFlushModeEntryStack = flushModeEntryStack.get();

        if (currentFlushModeEntryStack == null) {
            currentFlushModeEntryStack = new Stack<>();
            flushModeEntryStack.set(currentFlushModeEntryStack);
        }
        currentFlushModeEntryStack.push(new ArrayList<>());

        try {
            return super.execute(invocationContext);
        } finally {
            for (FlushModeEntry flushModeEntry : flushModeEntryStack.get().pop()) {
                flushModeEntry.restoreFlushMode();
            }
            if (flushModeEntryStack.get().isEmpty()) {
                flushModeEntryStack.set(null);
                flushModeEntryStack.remove();
            }
        }
    }

    @Override
    protected void beforeProceed(InvocationContext invocationContext,
                                 EntityManagerEntry entityManagerEntry,
                                 jakarta.persistence.EntityTransaction transaction) {
        super.beforeProceed(invocationContext, entityManagerEntry, transaction);

        Class<?> targetClass = ProxyUtils.getUnproxiedClass(invocationContext.getTarget().getClass());
        if (targetClass == null) {
            targetClass = invocationContext.getMethod().getDeclaringClass();
        }

        EntityManagerMetadata entityManagerMetadata = new EntityManagerMetadata();
        entityManagerMetadata.readFrom(targetClass, beanManager);
        entityManagerMetadata.readFrom(invocationContext.getMethod(), beanManager);

        Session hibernateSession = entityManagerEntry.getEntityManager().unwrap(Session.class);
        FlushMode flushModeBefore = hibernateSession.getHibernateFlushMode();

        if (entityManagerMetadata.isReadOnly()) {
            if (entityManagerMetadata.getQualifiers().length == 1 && Any.class.equals(entityManagerMetadata.getQualifiers()[0])
                    || Arrays.asList(entityManagerMetadata.getQualifiers()).contains(entityManagerEntry.getQualifier())) {
                hibernateSession.setHibernateFlushMode(FlushMode.MANUAL);
            }
        }

        flushModeEntryStack.get().peek().add(new FlushModeEntry(hibernateSession, flushModeBefore));
    }

    /**
     * Holds a reference to a Hibernate session and its original flush mode
     * so it can be restored after the transaction completes.
     */
    private class FlushModeEntry {

        private final Session hibernateSession;
        private final FlushMode flushModeBefore;

        FlushModeEntry(Session hibernateSession, FlushMode flushModeBefore) {
            this.hibernateSession = hibernateSession;
            this.flushModeBefore = flushModeBefore;
        }

        void restoreFlushMode() {
            if (hibernateSession.isOpen() && !hibernateSession.getHibernateFlushMode().equals(flushModeBefore)) {
                hibernateSession.setHibernateFlushMode(flushModeBefore);
            }
        }
    }
}
