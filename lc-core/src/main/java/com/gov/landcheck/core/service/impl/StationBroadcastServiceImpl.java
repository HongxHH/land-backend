package com.gov.landcheck.core.service.impl;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.bo.dto.NotificationPushPayload;
import com.gov.landcheck.core.bo.entity.StationMessage;
import com.gov.landcheck.core.service.StationBroadcastService;
import com.gov.landcheck.core.service.StationMessageService;

/**
 * 站内广播统一入口实现：先持久化，再实时广播。
 */
@Service
public class StationBroadcastServiceImpl implements StationBroadcastService {

    private static final String TOPIC_PROJECT_PREFIX = "/topic/project/%s/%s";
    private static final String TOPIC_GLOBAL_PREFIX = "/topic/%s";

    private final SimpMessagingTemplate messagingTemplate;
    private final StationMessageService stationMessageService;

    public StationBroadcastServiceImpl(SimpMessagingTemplate messagingTemplate,
            StationMessageService stationMessageService) {
        this.messagingTemplate = messagingTemplate;
        this.stationMessageService = stationMessageService;
    }

    @Override
    public void broadcast(String topicKey, NotificationPushPayload payload) {
        if (payload == null || !StringUtils.hasText(topicKey)) {
            return;
        }

        StationMessage stationMessage = stationMessageService.saveBroadcastMessage(topicKey, payload);
        NotificationPushPayload outbound = payload.withMessageId(stationMessage.getId());

        Long projectId = outbound.getProjectId();
        String destination = projectId != null
                ? TOPIC_PROJECT_PREFIX.formatted(projectId, topicKey)
                : TOPIC_GLOBAL_PREFIX.formatted(topicKey);
        messagingTemplate.convertAndSend(destination, outbound);
    }
}
