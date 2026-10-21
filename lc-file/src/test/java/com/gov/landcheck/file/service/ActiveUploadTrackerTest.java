package com.gov.landcheck.file.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActiveUploadTrackerTest {

    @Test
    void bytesWithinStallWindowCountAsReceiving() {
        ActiveUploadTracker tracker = new ActiveUploadTracker();
        tracker.begin(7L);
        tracker.noteBytes(7L, 1_000L);

        assertTrue(tracker.isReceivingAt(7L, 10_000L, 10_999L));
        assertFalse(tracker.isReceivingAt(7L, 10_000L, 11_000L));
    }

    @Test
    void endDropsTheUploadEvenIfALateReadArrives() {
        ActiveUploadTracker tracker = new ActiveUploadTracker();
        tracker.begin(7L);
        tracker.end(7L);
        tracker.noteBytes(7L, 5_000L);

        assertFalse(tracker.isReceivingAt(7L, 10_000L, 5_000L));
    }
}
