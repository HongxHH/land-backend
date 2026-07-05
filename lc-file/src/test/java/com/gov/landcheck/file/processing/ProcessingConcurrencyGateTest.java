package com.gov.landcheck.file.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProcessingConcurrencyGateTest {

    @Test
    void expandIncreasesMaxPermits() {
        ProcessingConcurrencyGate gate = new ProcessingConcurrencyGate("test", 2, 1_000L);
        gate.resizeTo(4);
        assertEquals(4, gate.getMaxPermits());
        assertEquals(4, gate.availablePermits());
    }

    @Test
    void shrinkWhenIdleSucceeds() {
        ProcessingConcurrencyGate gate = new ProcessingConcurrencyGate("test", 4, 1_000L);
        gate.resizeTo(2);
        assertEquals(2, gate.getMaxPermits());
        assertEquals(2, gate.availablePermits());
    }

    @Test
    void shrinkWhileInUseRejected() {
        ProcessingConcurrencyGate gate = new ProcessingConcurrencyGate("test", 2, 1_000L);
        assertTrue(gate.tryAcquire());
        assertTrue(gate.tryAcquire());
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> gate.resizeTo(1));
        assertTrue(ex.getMessage().contains("无法缩容"));
        gate.release();
        gate.release();
    }

    @Test
    void acquireAndReleaseTracksUsedPermits() {
        ProcessingConcurrencyGate gate = new ProcessingConcurrencyGate("test", 2, 1_000L);
        assertTrue(gate.tryAcquire());
        assertEquals(1, gate.getUsedPermits());
        gate.release();
        assertEquals(0, gate.getUsedPermits());
    }
}
