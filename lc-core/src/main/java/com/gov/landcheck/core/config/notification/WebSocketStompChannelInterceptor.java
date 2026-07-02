package com.gov.landcheck.core.config.notification;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * STOMP 入站鉴权：CONNECT 绑定登录用户，SUBSCRIBE 校验 topic 权限。
 */
@Slf4j
@Component
public class WebSocketStompChannelInterceptor implements ChannelInterceptor {

    private static final Pattern PROJECT_TOPIC = Pattern.compile("^/topic/project/(\\d+)/([a-z0-9-]+)$");
    private static final Pattern GLOBAL_TOPIC = Pattern.compile("^/topic/file-updates$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setLeaveMutable(true);
            bindAuthenticatedUser(accessor);
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscribe(accessor);
        }
        return message;
    }

    private void bindAuthenticatedUser(StompHeaderAccessor accessor) {
        String loginId = WebSocketAuthSupport.resolveLoginId(accessor);
        if (!StringUtils.hasText(loginId)) {
            throw new IllegalStateException("WebSocket CONNECT 缺少有效登录态");
        }
        accessor.setUser(WebSocketAuthSupport.toPrincipal(loginId));
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            sessionAttributes.put(WebSocketAuthConstants.LOGIN_ID, loginId);
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String userId = WebSocketAuthSupport.resolveLoginId(accessor);
        if (!StringUtils.hasText(userId)) {
            rejectSubscribe(accessor, null, "未登录用户禁止订阅 WebSocket topic");
        }
        if (accessor.getUser() == null || "anonymous".equals(accessor.getUser().getName())) {
            accessor.setLeaveMutable(true);
            accessor.setUser(WebSocketAuthSupport.toPrincipal(userId));
        }

        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            rejectSubscribe(accessor, userId, "WebSocket 订阅目标为空");
        }

        Long projectId = resolveProjectId(destination);
        if (projectId == null && !GLOBAL_TOPIC.matcher(destination).matches()) {
            rejectSubscribe(accessor, userId, "非法 WebSocket 订阅目标: " + destination);
        }
        if (projectId != null) {
            String topicKey = resolveProjectTopicKey(destination);
            if (!isAllowedTopicKey(topicKey)) {
                rejectSubscribe(accessor, userId, "非法 WebSocket 订阅 topic: " + topicKey);
            }
        }
        log.debug("WebSocket 订阅通过: userId={}, destination={}", userId, destination);
    }

    private void rejectSubscribe(StompHeaderAccessor accessor, String userId, String reason) {
        log.warn("[WebSocket] 订阅拒绝: sessionId={}, userId={}, destination={}, reason={}",
                accessor.getSessionId(), userId, accessor.getDestination(), reason);
        throw new IllegalStateException(reason);
    }

    private static Long resolveProjectId(String destination) {
        Matcher matcher = PROJECT_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String resolveProjectTopicKey(String destination) {
        Matcher matcher = PROJECT_TOPIC.matcher(destination);
        return matcher.matches() ? matcher.group(2) : null;
    }

    private static boolean isAllowedTopicKey(String topicKey) {
        return BroadcastTopicConstants.FILE_UPDATES.equals(topicKey);
    }

}
