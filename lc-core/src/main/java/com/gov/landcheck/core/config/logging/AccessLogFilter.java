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
 * <p>
 * 上传接口可在 Controller 中设置 {@link #ATTR_FILE_NAME} / {@link #ATTR_FILE_SIZE}，
 * 便于对照慢请求与文件体量。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class AccessLogFilter extends OncePerRequestFilter {

    /** Controller 可写入：原始文件名（已尽量脱敏换行） */
    public static final String ATTR_FILE_NAME = "landcheck.access.fileName";
    /** Controller 可写入：文件字节数 */
    public static final String ATTR_FILE_SIZE = "landcheck.access.fileSize";

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS_LOG");
    private static final long SLOW_REQUEST_MS = 3000L;
    private static final Pattern SENSITIVE_QUERY_PARAM = Pattern.compile(
            "(?i)(password|token|secret|key|satoken|authorization|ticket)=([^&]*)");

    private static boolean isSourceBundleDownload(String uri) {
        return uri != null && uri.endsWith("/source-bundle");
    }

    /**
     * 前端任务池监控等高频轮询接口，正常响应不写 access.log，避免刷屏。
     * 慢请求（≥ {@link #SLOW_REQUEST_MS}ms）仍会记录。
     */
    private static boolean isHighFrequencyPollPath(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        return "/file/task/status".equals(uri)
                || "/file/task/system-status".equals(uri)
                || uri.startsWith("/file/task/detail/");
    }

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

            String fileName = resolveAccessFileName(request);
            String fileSizeText = resolveAccessFileSize(request);

            boolean pollPath = isHighFrequencyPollPath(request.getRequestURI());
            if (!pollPath || durationMs >= SLOW_REQUEST_MS) {
                ACCESS_LOG.info(
                        "traceId={} method={} uri={} status={} durationMs={} userId={} fileName={} fileSize={}",
                        traceId, method, uri, status, durationMs, userIdText, fileName, fileSizeText);
            }

            if (durationMs >= SLOW_REQUEST_MS && !isSourceBundleDownload(request.getRequestURI())) {
                log.warn(
                        "慢请求: traceId={} method={} uri={} status={} durationMs={} userId={} fileName={} fileSize={}",
                        traceId, method, uri, status, durationMs, userIdText, fileName, fileSizeText);
            }
        }
    }

    private static String resolveAccessFileName(HttpServletRequest request) {
        Object attr = request.getAttribute(ATTR_FILE_NAME);
        if (attr == null) {
            return "-";
        }
        String name = String.valueOf(attr).replaceAll("[\\r\\n\\t]+", " ").trim();
        if (name.isEmpty()) {
            return "-";
        }
        return name.length() > 200 ? name.substring(0, 200) + "…" : name;
    }

    private static String resolveAccessFileSize(HttpServletRequest request) {
        Object attr = request.getAttribute(ATTR_FILE_SIZE);
        if (attr instanceof Number number) {
            return String.valueOf(number.longValue());
        }
        if (attr != null) {
            try {
                return String.valueOf(Long.parseLong(String.valueOf(attr)));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        long contentLength = request.getContentLengthLong();
        return contentLength >= 0 ? String.valueOf(contentLength) : "-";
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
