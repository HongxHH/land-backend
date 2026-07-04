package com.gov.landcheck.file.service.parse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ParseRollbackSummaryTest {

    @Test
    void merge_accumulatesFailures() {
        ParseRollbackSummary a = new ParseRollbackSummary();
        a.recordSuccess("PREPROCESS: ok");
        a.recordFailure("OCR", "gridfs missing");

        ParseRollbackSummary b = new ParseRollbackSummary();
        b.recordFailure("PARSE", "timeout");

        a.merge(b);
        assertTrue(a.hasFailures());
        assertTrue(a.getFailuresView().size() >= 2);
    }

    @Test
    void successOnly_hasNoFailures() {
        ParseRollbackSummary summary = new ParseRollbackSummary();
        summary.recordSuccess("FILL: ok");
        assertFalse(summary.hasFailures());
    }
}
