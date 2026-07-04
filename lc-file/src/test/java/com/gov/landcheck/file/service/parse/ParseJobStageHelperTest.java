package com.gov.landcheck.file.service.parse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.gov.landcheck.core.bo.entity.ParseJob;

class ParseJobStageHelperTest {

    @Test
    void hasFillStageStarted_whenFillStatusProcessing_returnsTrue() {
        ParseJob job = new ParseJob();
        job.setFillStatus("PROCESSING");
        assertTrue(ParseJobStageHelper.hasFillStageStarted(job, null));
    }

    @Test
    void hasFillStageStarted_whenFillPending_returnsFalse() {
        ParseJob job = new ParseJob();
        job.setFillStatus("PENDING");
        assertFalse(ParseJobStageHelper.hasFillStageStarted(job, null));
    }

    @Test
    void hasValidateStageStarted_whenValidateFailed_returnsTrue() {
        ParseJob job = new ParseJob();
        job.setValidateStatus("FAILED");
        assertTrue(ParseJobStageHelper.hasValidateStageStarted(job, null));
    }

    @Test
    void taskDataForOfflineRollback_setsFillEnteredWhenFillStarted() {
        ParseJob job = new ParseJob();
        job.setFillStatus("PROCESSING");
        var taskData = ParseJobStageHelper.taskDataForOfflineRollback(job,
                new com.gov.landcheck.core.bo.entity.FileRecord());
        assertTrue(taskData.isFillCommandEntered());
    }
}
