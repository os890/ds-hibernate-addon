package org.os890.ds.addon.hibernate.impl;

import org.apache.deltaspike.core.util.ProxyUtils;
import org.apache.deltaspike.jpa.impl.entitymanager.EntityManagerMetadata;
import org.apache.deltaspike.jpa.impl.transaction.EnvironmentAwareTransactionStrategy;
import org.apache.deltaspike.jpa.impl.transaction.context.EntityManagerEntry;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.ejb.HibernateEntityManager;

import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Alternative;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.interceptor.InvocationContext;
import javax.persistence.EntityTransaction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import static javax.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

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
    protected void beforeProceed(InvocationContext invocationContext, EntityManagerEntry entityManagerEntry, EntityTransaction transaction) {
        super.beforeProceed(invocationContext, entityManagerEntry, transaction);

        Class targetClass = ProxyUtils.getUnproxiedClass(invocationContext.getTarget().getClass());
        if (targetClass == null) {
            targetClass = invocationContext.getMethod().getDeclaringClass();
        }

        EntityManagerMetadata entityManagerMetadata = new EntityManagerMetadata();
        entityManagerMetadata.readFrom(targetClass, beanManager);
        entityManagerMetadata.readFrom(invocationContext.getMethod(), beanManager);

        Session hibernateSession = entityManagerEntry.getEntityManager().unwrap(HibernateEntityManager.class).getSession();
        FlushMode flushModeBefore = hibernateSession.getFlushMode();

        if (entityManagerMetadata.isReadOnly()) {
            if (entityManagerMetadata.getQualifiers().length == 1 && Any.class.equals(entityManagerMetadata.getQualifiers()[0]) ||
                Arrays.asList(entityManagerMetadata.getQualifiers()).contains(entityManagerEntry.getQualifier())) {
                hibernateSession.setFlushMode(FlushMode.MANUAL);
            }
        }

        flushModeEntryStack.get().peek().add(new FlushModeEntry(hibernateSession, flushModeBefore));
    }

    private class FlushModeEntry {
        private final Session hibernateSession;
        private final FlushMode flushModeBefore;

        FlushModeEntry(Session hibernateSession, FlushMode flushModeBefore) {
            this.hibernateSession = hibernateSession;
            this.flushModeBefore = flushModeBefore;
        }

        void restoreFlushMode() {
            if (hibernateSession.isOpen() && !hibernateSession.getFlushMode().equals(flushModeBefore)) {
                hibernateSession.setFlushMode(flushModeBefore);
            }
        }
    }
}
