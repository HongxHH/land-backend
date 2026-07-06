package com.gov.landcheck.core.config.logging;

/**
 * 捕获当前线程 traceId，供异步任务恢复。
 */
public final class ContextSnapshot {

    private final String traceId;

    private ContextSnapshot(String traceId) {
        this.traceId = traceId;
    }

    public static ContextSnapshot capture() {
        return new ContextSnapshot(TraceContext.getTraceId());
    }

    public void restore() {
        if (traceId != null && !traceId.isBlank()) {
            TraceContext.setTraceId(traceId);
        }
    }

    public String getTraceId() {
        return traceId;
    }
}
