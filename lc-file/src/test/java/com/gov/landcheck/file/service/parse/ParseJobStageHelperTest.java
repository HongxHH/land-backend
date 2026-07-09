package com.gov.landcheck.file.service.parse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.file.task.base.TaskData;

class ParseJobStageHelperTest {

    @Test
    void offlineRollbackSkipsCompletedFillAndValidateStages() {
        ParseJob parseJob = new ParseJob();
        parseJob.setFillStatus("SUCCESS");
        parseJob.setValidateStatus("SUCCESS");

        TaskData offlineTaskData = ParseJobStageHelper.taskDataForOfflineRollback(parseJob, null);

        assertFalse(ParseJobStageHelper.hasFillStageStarted(parseJob, offlineTaskData));
        assertFalse(ParseJobStageHelper.hasValidateStageStarted(parseJob, offlineTaskData));
    }

    @Test
    void offlineRollbackKeepsProcessingStagesRollbackable() {
        ParseJob parseJob = new ParseJob();
        parseJob.setFillStatus("PROCESSING");
        parseJob.setValidateStatus("PROCESSING");

        TaskData offlineTaskData = ParseJobStageHelper.taskDataForOfflineRollback(parseJob, null);

        assertTrue(ParseJobStageHelper.hasFillStageStarted(parseJob, offlineTaskData));
        assertTrue(ParseJobStageHelper.hasValidateStageStarted(parseJob, offlineTaskData));
    }

    @Test
    void onlineRollbackStillUsesLiveTaskContextForCompletedFill() {
        ParseJob parseJob = new ParseJob();
        parseJob.setFillStatus("SUCCESS");

        TaskData liveTaskData = new TaskData();
        liveTaskData.setFillCommandEntered(true);

        assertTrue(ParseJobStageHelper.hasFillStageStarted(parseJob, liveTaskData));
    }
}
