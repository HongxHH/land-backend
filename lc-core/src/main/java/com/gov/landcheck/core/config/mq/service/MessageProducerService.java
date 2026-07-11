package com.gov.landcheck.core.config.mq.service;

import com.gov.landcheck.core.config.mq.constant.TopicConstants;
import com.gov.landcheck.core.config.mq.exception.MessageSendException;

/**
 * 消息生产端统一接口：发送到指定 Topic/Tag，带业务 key，支持超时与重试（由实现层根据配置完成）。
 *
 * @author landcheck
 */
public interface MessageProducerService {

    /**
     * 同步发送消息（带重试与超时）
     *
     * @param topic   Topic
     * @param tag     Tag，可为 null
     * @param key     业务 key，用于追踪与幂等，可为 null
     * @param payload 消息体，由统一序列化器序列化
     * @throws MessageSendException 发送失败（含重试耗尽）
     */
    void send(String topic, String tag, String key, Object payload);

    /**
     * 同步发送消息，无 Tag
     *
     * @param topic   Topic
     * @param key     业务 key，可为 null
     * @param payload 消息体
     */
    default void send(String topic, String key, Object payload) {
        send(topic, null, key, payload);
    }

    /**
     * 推送文件解析结果至指定接收端（Topic: lc-file-parse-result）
     *
     * @param fileId    文件 ID，作为 key
     * @param projectId 项目 ID，可为 null
     * @param tag       completed / failed / partial
     * @param payload   消息体（如解析结果摘要）
     */
    default void sendFileParseResult(String fileId, String projectId, String tag, Object payload) {
        String key = projectId != null ? projectId + ":" + fileId : fileId;
        send(TopicConstants.TOPIC_FILE_PARSE_RESULT, tag, key, payload);
    }

    /**
     * 下发土地违规风险预警（Topic: lc-land-risk-alert）
     *
     * @param riskId  风险 ID，作为 key
     * @param tag     risk-create / risk-update / risk-close
     * @param payload 消息体
     */
    default void sendLandRiskAlert(String riskId, String tag, Object payload) {
        send(TopicConstants.TOPIC_LAND_RISK_ALERT, tag, riskId, payload);
    }
}
