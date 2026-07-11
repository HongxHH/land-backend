package com.gov.landcheck.file.task.result;

/**
 * 任务结果摘要：由任务自身产出，供统一发布器发送到 MQ。
 */
public record TaskResultSummary(
        String topic,
        String tag,
        String key,
        Object payload,
        String rateLimitKey
) {

    public static TaskResultSummary none() {
        return new TaskResultSummary(null, null, null, null, null);
    }

    public boolean shouldPublish() {
        return topic != null && !topic.isBlank()
                && key != null && !key.isBlank()
                && payload != null;
    }
}

