package com.gov.landcheck.core.audit;

import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 在 MVC 拦截器阶段解析 Sa-Token 登录态并覆盖 OperatorContext。
 * 该阶段 Sa-Token 上下文已就绪，避免在 Filter 阶段直接调用 StpUtil 触发上下文异常。
 */
@Slf4j
@Component
public class OperatorContextInterceptor implements HandlerInterceptor {

    private static final String WS_ENDPOINT_PREFIX = "/ws";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 仅处理普通 HTTP 请求；忽略异步/错误分发与 WebSocket 握手请求，避免无意义的 Sa 上下文告警噪音。
        if (request.getDispatcherType() != DispatcherType.REQUEST || isWebSocketHandshake(request)) {
            return true;
        }
        try {
            if (StpUtil.isLogin()) {
                Long operatorId = StpUtil.getLoginIdAsLong();
                Object username = StpUtil.getSession().get("username");
                OperatorContext.setOperator(operatorId, username != null ? String.valueOf(username) : null);
            }
        } catch (SaTokenContextException e) {
            // 异步二次分发、/error 转发等可能在新线程执行且未绑定 Sa 上下文；保留 Filter 阶段 X-User-Id 兜底
            log.trace("Sa-Token 上下文未初始化，跳过从登录态解析操作人: {}", e.getMessage());
        }
        return true;
    }

    private static boolean isWebSocketHandshake(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return false;
        }
        String contextPath = request.getContextPath();
        String path = (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
        return path.equals(WS_ENDPOINT_PREFIX) || path.startsWith(WS_ENDPOINT_PREFIX + "/");
    }
}
