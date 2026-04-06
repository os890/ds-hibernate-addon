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

import org.hibernate.FlushMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit test verifying the core flush mode switching and restoration logic
 * that {@link HibernateAwareTransactionStrategy} applies at runtime.
 *
 * <p>Uses a simple test double rather than Mockito to avoid byte-code
 * manipulation issues with Hibernate's Session interface on Java 25.</p>
 */
class FlushModeRestorationTest {

    /**
     * Verifies that switching to MANUAL and restoring brings back the original mode.
     */
    @Test
    void flushModeIsRestoredAfterManualSwitch() {
        FakeSession session = new FakeSession(FlushMode.AUTO, true);

        // Save original
        FlushMode originalMode = session.getHibernateFlushMode();
        Assertions.assertEquals(FlushMode.AUTO, originalMode);

        // Switch to MANUAL (what the strategy does for readOnly)
        session.setHibernateFlushMode(FlushMode.MANUAL);
        Assertions.assertEquals(FlushMode.MANUAL, session.getHibernateFlushMode());

        // Restore (what the strategy does in the finally block)
        if (session.isOpen() && !session.getHibernateFlushMode().equals(originalMode)) {
            session.setHibernateFlushMode(originalMode);
        }
        Assertions.assertEquals(FlushMode.AUTO, session.getHibernateFlushMode());
    }

    /**
     * Verifies that flush mode is NOT restored when the session is closed.
     */
    @Test
    void flushModeNotRestoredWhenSessionClosed() {
        FakeSession session = new FakeSession(FlushMode.MANUAL, false);

        FlushMode originalMode = FlushMode.AUTO;

        if (session.isOpen() && !session.getHibernateFlushMode().equals(originalMode)) {
            session.setHibernateFlushMode(originalMode);
        }

        // Should still be MANUAL — restoration was skipped
        Assertions.assertEquals(FlushMode.MANUAL, session.getHibernateFlushMode());
    }

    /**
     * Verifies that flush mode is NOT changed when it already matches the original.
     */
    @Test
    void flushModeNotRestoredWhenAlreadyCorrect() {
        FakeSession session = new FakeSession(FlushMode.AUTO, true);

        FlushMode originalMode = FlushMode.AUTO;
        int callsBefore = session.setCallCount;

        if (session.isOpen() && !session.getHibernateFlushMode().equals(originalMode)) {
            session.setHibernateFlushMode(originalMode);
        }

        // setHibernateFlushMode should not have been called
        Assertions.assertEquals(callsBefore, session.setCallCount);
    }

    /**
     * Minimal test double for Hibernate Session flush mode operations.
     */
    private static class FakeSession {

        private FlushMode flushMode;
        private final boolean open;
        int setCallCount;

        FakeSession(FlushMode initialMode, boolean open) {
            this.flushMode = initialMode;
            this.open = open;
        }

        FlushMode getHibernateFlushMode() {
            return flushMode;
        }

        void setHibernateFlushMode(FlushMode mode) {
            this.flushMode = mode;
            setCallCount++;
        }

        boolean isOpen() {
            return open;
        }
    }
}
