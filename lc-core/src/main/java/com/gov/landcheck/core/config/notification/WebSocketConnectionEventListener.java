package com.gov.landcheck.core.config.notification;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 监听 WebSocket 连接/断开/订阅事件，便于开发环境排障。
 */
@Slf4j
@Component
public class WebSocketConnectionEventListener {

    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.debug("[WebSocket] 客户端连接成功: sessionId={}, user={}",
                accessor.getSessionId(), resolveDisplayUser(accessor));
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.debug("[WebSocket] 客户端断开连接: sessionId={}, user={}",
                accessor.getSessionId(), resolveDisplayUser(accessor));
    }

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.debug("[WebSocket] 客户端订阅: sessionId={}, user={}, destination={}",
                accessor.getSessionId(), resolveDisplayUser(accessor), accessor.getDestination());
    }

    private static String resolveDisplayUser(StompHeaderAccessor accessor) {
        String loginId = WebSocketAuthSupport.resolveLoginId(accessor);
        return StringUtils.hasText(loginId) ? loginId : "anonymous";
    }
}
