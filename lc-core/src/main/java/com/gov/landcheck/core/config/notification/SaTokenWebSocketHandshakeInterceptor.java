package com.gov.landcheck.core.config.notification;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 握手阶段校验 Sa-Token。
 * 前端需在 SockJS URL 上携带 {@code satoken} 查询参数（与 REST 请求头同名）。
 */
@Slf4j
@Component
public class SaTokenWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        String token = resolveToken(httpRequest);
        if (!StringUtils.hasText(token)) {
            log.debug("WebSocket 握手拒绝：缺少 token");
            return false;
        }
        try {
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId == null) {
                log.debug("WebSocket 握手拒绝：token 无对应登录态");
                return false;
            }
            attributes.put(WebSocketAuthConstants.LOGIN_ID, String.valueOf(loginId));
            return true;
        } catch (NotLoginException ex) {
            log.debug("WebSocket 握手拒绝：token 无效");
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private static String resolveToken(HttpServletRequest request) {
        String tokenName = StpUtil.getTokenName();
        String token = request.getParameter(tokenName);
        if (StringUtils.hasText(token)) {
            return token.trim();
        }
        token = request.getHeader(tokenName);
        return StringUtils.hasText(token) ? token.trim() : null;
    }
}
