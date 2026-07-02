package com.gov.landcheck.core.config.mq.constant;

/**
 * RocketMQ Topic / Tag 常量，统一维护，供生产端与消费端复用。
 *
 * @author landcheck
 */
public final class TopicConstants {

    private TopicConstants() {
    }

    // ---------- 文件解析结果 ----------
    /** 文件解析结果推送 */
    public static final String TOPIC_FILE_PARSE_RESULT = "lc-file-parse-result";
    public static final String TAG_PARSE_COMPLETED = "completed";
    public static final String TAG_PARSE_FAILED = "failed";

    // ---------- 土地违规风险预警 ----------
    /** 土地违规风险预警 */
    public static final String TOPIC_LAND_RISK_ALERT = "lc-land-risk-alert";
    public static final String TAG_RISK_CREATE = "risk-create";
    public static final String TAG_RISK_UPDATE = "risk-update";
    public static final String TAG_RISK_CLOSE = "risk-close";
}
