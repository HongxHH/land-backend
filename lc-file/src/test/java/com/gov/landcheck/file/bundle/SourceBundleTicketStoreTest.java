package com.gov.landcheck.file.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SourceBundleTicketStoreTest {

    @Test
    void ticketIsSingleUseAndBoundToTheProject() {
        SourceBundleTicketStore store = new SourceBundleTicketStore();
        String token = store.issue(7L, "审核员", 15L);

        assertEquals(7L, store.peek(token, 15L).userId());
        assertEquals(7L, store.consume(token, 15L).userId());
        assertNull(store.consume(token, 15L));

        String other = store.issue(7L, "审核员", 15L);
        assertNull(store.peek(other, 16L));
        assertNull(store.consume(other, 15L));
    }

    @Test
    void expiredTicketCannotBeConsumed() {
        SourceBundleTicketStore store = new SourceBundleTicketStore();
        String token = store.issue(7L, "审核员", 15L, 0);
        assertNull(store.consume(token, 15L));
    }

    @Test
    void oneUserCannotHoldTwoDownloads() {
        SourceBundleDownloadGate gate = new SourceBundleDownloadGate();
        assertTrue(gate.tryAcquire(3L));
        assertTrue(gate.isBusy(3L));
        assertFalse(gate.tryAcquire(3L));
        gate.release(3L);
        assertTrue(gate.tryAcquire(3L));
    }
}
