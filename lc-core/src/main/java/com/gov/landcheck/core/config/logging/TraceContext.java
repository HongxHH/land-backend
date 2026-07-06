package com.gov.landcheck.core.config.logging;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * 请求链路追踪上下文，与 SLF4J MDC 同步。
 */
public final class TraceContext {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "traceId";

    private static final int MAX_TRACE_ID_LENGTH = 128;

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void setTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        String normalized = traceId.trim();
        HOLDER.set(normalized);
        MDC.put(MDC_KEY, normalized);
    }

    public static String getTraceId() {
        String traceId = HOLDER.get();
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return MDC.get(MDC_KEY);
    }

    public static void clear() {
        HOLDER.remove();
        MDC.remove(MDC_KEY);
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 规范化客户端或内部传入的 traceId：去空白/控制字符并限制长度。
     * 无效输入时返回新生成的 traceId。
     */
    public static String normalizeTraceId(String raw) {
        if (raw == null || raw.isBlank()) {
            return generateTraceId();
        }
        String normalized = raw.trim().replaceAll("[\\r\\n\\t]", "");
        if (normalized.length() > MAX_TRACE_ID_LENGTH) {
            normalized = normalized.substring(0, MAX_TRACE_ID_LENGTH);
        }
        if (normalized.isBlank()) {
            return generateTraceId();
        }
        return normalized;
    }

    public static String generateWorkerTraceId() {
        return "worker-" + UUID.randomUUID();
    }

    public static String generateSchedulerTraceId() {
        return "scheduler-" + UUID.randomUUID();
    }
}
