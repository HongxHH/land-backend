package com.gov.landcheck.core.config.logging;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 从请求头读取或生成 X-Request-Id，写入 MDC 并在响应头回传。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(TraceContext.HEADER_NAME);
        String traceId = TraceContext.normalizeTraceId(incoming);
        TraceContext.setTraceId(traceId);
        response.setHeader(TraceContext.HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
