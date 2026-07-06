package com.gov.landcheck.core.config.logging;

import java.util.concurrent.Callable;

import org.springframework.core.task.TaskDecorator;

/**
 * 在异步线程中恢复 traceId 上下文。
 */
public final class ContextPropagating implements TaskDecorator {

    public static final ContextPropagating INSTANCE = new ContextPropagating();

    private ContextPropagating() {
    }

    @Override
    public Runnable decorate(Runnable runnable) {
        return wrap(runnable);
    }

    public static Runnable wrap(Runnable delegate) {
        ContextSnapshot snapshot = ContextSnapshot.capture();
        return () -> runWithSnapshot(snapshot, delegate);
    }

    public static <T> Callable<T> wrap(Callable<T> delegate) {
        ContextSnapshot snapshot = ContextSnapshot.capture();
        return () -> {
            try {
                applySnapshot(snapshot);
                return delegate.call();
            } catch (Exception e) {
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException(e);
            } finally {
                TraceContext.clear();
            }
        };
    }

    private static void runWithSnapshot(ContextSnapshot snapshot, Runnable delegate) {
        try {
            applySnapshot(snapshot);
            delegate.run();
        } finally {
            TraceContext.clear();
        }
    }

    private static void applySnapshot(ContextSnapshot snapshot) {
        if (snapshot.getTraceId() != null && !snapshot.getTraceId().isBlank()) {
            snapshot.restore();
        } else {
            TraceContext.setTraceId(TraceContext.generateWorkerTraceId());
        }
    }

    public static void runWithTraceId(String traceId, Runnable runnable) {
        try {
            if (traceId != null && !traceId.isBlank()) {
                TraceContext.setTraceId(traceId);
            } else {
                TraceContext.setTraceId(TraceContext.generateSchedulerTraceId());
            }
            runnable.run();
        } finally {
            TraceContext.clear();
        }
    }
}
