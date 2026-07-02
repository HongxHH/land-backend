package com.gov.landcheck.file.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 提交解析任务的结果：用于「可解析且无进行中任务则提交」的复用方法返回值。
 */
@Getter
@AllArgsConstructor
public class SubmitParseResult {

    private final boolean submitted;
    private final String taskId;
    private final String errorCode;
    private final String errorMessage;

    public static SubmitParseResult success(String taskId) {
        return new SubmitParseResult(true, taskId, null, null);
    }

    public static SubmitParseResult fail(String errorCode, String errorMessage) {
        return new SubmitParseResult(false, null, errorCode, errorMessage);
    }
}
