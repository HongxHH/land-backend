package com.gov.landcheck.core.service;

import com.gov.landcheck.core.bo.dto.NotificationPushPayload;

/**
 * 站内广播统一入口：先持久化，再 WebSocket 广播。
 */
public interface StationBroadcastService {

    /**
     * 按 topic 键广播站内消息。
     *
     * @param topicKey 广播主题键，见
     *                 {@link com.gov.landcheck.core.config.notification.BroadcastTopicConstants}
     * @param payload  消息内容
     */
    void broadcast(String topicKey, NotificationPushPayload payload);
}
