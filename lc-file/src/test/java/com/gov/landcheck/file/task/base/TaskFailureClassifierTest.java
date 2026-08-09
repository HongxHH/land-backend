package com.gov.landcheck.file.task.base;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskFailureClassifierTest {

    @Test
    void fillAndValidateFailuresMapToRetryableDatabaseType() {
        assertThat(TaskFailureClassifier.toRetryErrorType(new TaskException(
                TaskException.ErrorCode.FILL_FAILED,
                "FILL",
                null,
                null,
                "transient fill failure"))).isEqualTo("database");

        assertThat(TaskFailureClassifier.toRetryErrorType(new TaskException(
                TaskException.ErrorCode.VALIDATE_FAILED,
                "VALIDATE",
                null,
                null,
                "transient validate failure"))).isEqualTo("database");
    }

    @Test
    void deterministicParseDataFailureRemainsNonRetryableParseType() {
        assertThat(TaskFailureClassifier.toRetryErrorType(new TaskException(
                TaskException.ErrorCode.PARSE_DATA_INVALID,
                "PARSE",
                null,
                null,
                "missing required room data"))).isEqualTo("parse");
    }
}
