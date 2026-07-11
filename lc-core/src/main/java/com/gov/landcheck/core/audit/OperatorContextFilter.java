package com.gov.landcheck.core.audit;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * 从请求头 X-User-Id 解析当前操作人并写入 OperatorContext，请求结束后清除。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OperatorContextFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Filter 阶段仅做透传头兜底，避免在 Sa-Token 上下文未就绪时调用 StpUtil
            String userId = request.getHeader(HEADER_USER_ID);
            OperatorContext.setOperatorFromHeader(userId);
            filterChain.doFilter(request, response);
        } finally {
            OperatorContext.clear();
        }
    }
}
