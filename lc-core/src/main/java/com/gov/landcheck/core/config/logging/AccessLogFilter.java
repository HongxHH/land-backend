package com.gov.landcheck.core.config.logging;

import java.io.IOException;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gov.landcheck.core.audit.OperatorContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP 访问日志：每个请求一行摘要，写入独立 ACCESS_LOG logger。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS_LOG");
    private static final long SLOW_REQUEST_MS = 3000L;
    private static final Pattern SENSITIVE_QUERY_PARAM = Pattern.compile(
            "(?i)(password|token|secret|key|satoken|authorization)=([^&]*)");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        return uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/webjars/")
                || uri.startsWith("/doc.html")
                || "/favicon.ico".equals(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startMs = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startMs;
            String traceId = TraceContext.getTraceId();
            String method = request.getMethod();
            String uri = buildRequestUri(request);
            int status = response.getStatus();
            Long userId = OperatorContext.getOperatorId();
            String userIdText = userId != null ? String.valueOf(userId) : "-";

            ACCESS_LOG.info("traceId={} method={} uri={} status={} durationMs={} userId={}",
                    traceId, method, uri, status, durationMs, userIdText);

            if (durationMs >= SLOW_REQUEST_MS) {
                log.warn("慢请求: traceId={} method={} uri={} status={} durationMs={} userId={}",
                        traceId, method, uri, status, durationMs, userIdText);
            }
        }
    }

    private static String buildRequestUri(HttpServletRequest request) {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return path;
        }
        return path + "?" + sanitizeQueryString(query);
    }

    private static String sanitizeQueryString(String query) {
        return SENSITIVE_QUERY_PARAM.matcher(query).replaceAll("$1=***");
    }
}
