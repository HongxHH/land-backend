package com.gov.landcheck.core.service.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.gov.landcheck.core.bo.dto.NotificationPushPayload;
import com.gov.landcheck.core.bo.entity.StationMessage;
import com.gov.landcheck.core.bo.entity.UserStationMessageRead;
import com.gov.landcheck.core.service.StationMessageService;

/**
 * 站内消息服务实现。
 */
@Service
public class StationMessageServiceImpl implements StationMessageService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final MongoTemplate mongoTemplate;

    public StationMessageServiceImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public StationMessage saveBroadcastMessage(String topicKey, NotificationPushPayload payload) {
        StationMessage message = new StationMessage();
        message.setTopicKey(topicKey);
        message.setScene(payload.getScene());
        message.setTitle(payload.getTitle());
        message.setContent(payload.getContent());
        message.setBusinessId(payload.getBusinessId());
        message.setProjectId(payload.getProjectId());
        message.setFileId(payload.getFileId());
        message.setSentAt(LocalDateTime.now());
        message.preSave();
        return mongoTemplate.save(message);
    }

    @Override
    public List<StationMessage> listUnreadMessages(String userId, List<Long> projectIds, int limit) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        List<Long> filteredProjectIds = projectIds == null
                ? Collections.emptyList()
                : projectIds.stream().filter(id -> id != null && id > 0).distinct().collect(Collectors.toList());
        if (filteredProjectIds.isEmpty()) {
            return Collections.emptyList();
        }

        Query stationQuery = new Query(Criteria.where("project_id").in(filteredProjectIds))
                .with(Sort.by(Sort.Direction.DESC, "sent_at"))
                .limit(resolveLimit(limit));
        return filterUnread(userId, mongoTemplate.find(stationQuery, StationMessage.class));
    }

    @Override
    public List<StationMessage> listUnreadMessagesForUserAllProjects(String userId, int limit) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        Query stationQuery = new Query()
                .with(Sort.by(Sort.Direction.DESC, "sent_at"))
                .limit(resolveLimit(limit));
        return filterUnread(userId, mongoTemplate.find(stationQuery, StationMessage.class));
    }

    @Override
    public void markRead(String userId, List<Long> messageIds) {
        if (userId == null || userId.isBlank() || CollectionUtils.isEmpty(messageIds)) {
            return;
        }
        Set<Long> uniqueIds = messageIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(HashSet::new));
        if (uniqueIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long messageId : uniqueIds) {
            Query query = new Query(
                    Criteria.where("user_id").is(userId).and("message_id").is(messageId));
            UserStationMessageRead existing = mongoTemplate.findOne(query, UserStationMessageRead.class);
            if (existing == null) {
                existing = new UserStationMessageRead();
                existing.setUserId(userId);
                existing.setMessageId(messageId);
            }
            existing.setReadAt(now);
            existing.preSave();
            mongoTemplate.save(existing);
        }
    }

    private List<StationMessage> filterUnread(String userId, List<StationMessage> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> messageIds = candidates.stream().map(StationMessage::getId).collect(Collectors.toList());
        Query readQuery = new Query(
                Criteria.where("user_id").is(userId).and("message_id").in(messageIds));
        Set<Long> readIds = mongoTemplate.find(readQuery, UserStationMessageRead.class).stream()
                .map(UserStationMessageRead::getMessageId)
                .collect(Collectors.toSet());
        return candidates.stream()
                .filter(message -> !readIds.contains(message.getId()))
                .collect(Collectors.toList());
    }

    private int resolveLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
