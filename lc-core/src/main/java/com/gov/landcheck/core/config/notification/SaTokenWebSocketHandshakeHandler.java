package com.gov.landcheck.core.config.notification;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * 将握手阶段校验通过的 loginId 绑定为 WebSocket Principal。
 */
@Component
public class SaTokenWebSocketHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        Object loginId = attributes.get(WebSocketAuthConstants.LOGIN_ID);
        if (loginId == null) {
            return () -> "anonymous";
        }
        String userId = String.valueOf(loginId);
        return () -> userId;
    }
}
