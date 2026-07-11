package com.gov.landcheck.core.config.notification;

import java.security.Principal;
import java.util.Map;

import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.util.StringUtils;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;

/**
 * 从 STOMP 会话中解析 WebSocket 登录用户。
 */
public final class WebSocketAuthSupport {

    private WebSocketAuthSupport() {
    }

    public static String resolveLoginId(StompHeaderAccessor accessor) {
        String fromUser = fromPrincipal(accessor.getUser());
        if (StringUtils.hasText(fromUser)) {
            return fromUser;
        }
        String fromSession = fromSessionAttributes(accessor.getSessionAttributes());
        if (StringUtils.hasText(fromSession)) {
            return fromSession;
        }
        return fromConnectToken(accessor);
    }

    public static Principal toPrincipal(String loginId) {
        if (!StringUtils.hasText(loginId)) {
            return null;
        }
        String normalized = loginId.trim();
        return () -> normalized;
    }

    private static String fromPrincipal(Principal principal) {
        if (principal == null) {
            return null;
        }
        String name = principal.getName();
        return "anonymous".equals(name) ? null : name;
    }

    private static String fromSessionAttributes(Map<String, Object> sessionAttributes) {
        if (sessionAttributes == null || sessionAttributes.isEmpty()) {
            return null;
        }
        Object loginId = sessionAttributes.get(WebSocketAuthConstants.LOGIN_ID);
        return loginId == null ? null : String.valueOf(loginId);
    }

    private static String fromConnectToken(StompHeaderAccessor accessor) {
        String token = accessor.getFirstNativeHeader(StpUtil.getTokenName());
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Object loginId = StpUtil.getLoginIdByToken(token.trim());
            return loginId == null ? null : String.valueOf(loginId);
        } catch (NotLoginException ex) {
            return null;
        }
    }
}
