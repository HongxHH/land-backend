package com.gov.landcheck.core.service;

import java.util.List;

import com.gov.landcheck.core.bo.dto.NotificationPushPayload;
import com.gov.landcheck.core.bo.entity.StationMessage;

/**
 * 站内消息服务：持久化、未读查询、已读确认。
 */
public interface StationMessageService {

    /**
     * 持久化一条站内广播消息。
     */
    StationMessage saveBroadcastMessage(String topicKey, NotificationPushPayload payload);

    /**
     * 按用户与项目查询未读消息。
     */
    List<StationMessage> listUnreadMessages(String userId, List<Long> projectIds, int limit);

    /**
     * 不按项目过滤：取全库最近若干条站内消息再排除已读。
     * 适用于登录后尚无项目列表时的未读拉取；部署为多租户按项目隔离时需改为带权限的项目列表查询。
     */
    List<StationMessage> listUnreadMessagesForUserAllProjects(String userId, int limit);

    /**
     * 标记消息已读。
     */
    void markRead(String userId, List<Long> messageIds);
}

