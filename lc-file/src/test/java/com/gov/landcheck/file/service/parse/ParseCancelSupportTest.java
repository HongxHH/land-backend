package com.gov.landcheck.file.service.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.enums.ParseJobStateEnum;

class ParseCancelSupportTest {

    @Test
    void isUserCancelledTreatsRecoveredFailedUserCancelAsCancelled() {
        ParseJob parseJob = new ParseJob();
        parseJob.setJobStatus(ParseJobStateEnum.FAILED);
        parseJob.setCancelRequested(true);
        parseJob.setCancelReason(ParseCancelSupport.REASON_USER_ACTIVE);

        assertThat(ParseCancelSupport.isUserCancelled(parseJob)).isTrue();
    }

    @Test
    void isUserCancelledRejectsSystemFailures() {
        ParseJob parseJob = new ParseJob();
        parseJob.setJobStatus(ParseJobStateEnum.FAILED);
        parseJob.setCancelRequested(false);
        parseJob.setCancelReason("任务因程序重启而被中断");

        assertThat(ParseCancelSupport.isUserCancelled(parseJob)).isFalse();
    }
}
