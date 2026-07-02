package com.gov.landcheck.file.processing;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * 全局 Semaphore 并发闸门，限制重资源任务同时执行数。
 */
@Slf4j
public final class ProcessingConcurrencyGate {

    private final String name;
    private final Semaphore semaphore;
    private final long acquireTimeoutMs;

    public ProcessingConcurrencyGate(String name, int maxConcurrent, long acquireTimeoutMs) {
        this.name = name;
        if (maxConcurrent <= 0) {
            this.semaphore = null;
        } else {
            this.semaphore = new Semaphore(maxConcurrent, true);
        }
        this.acquireTimeoutMs = acquireTimeoutMs <= 0 ? 30_000L : acquireTimeoutMs;
    }

    public boolean tryAcquire() {
        if (semaphore == null) {
            return true;
        }
        try {
            boolean ok = semaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
            if (!ok) {
                log.warn("{} concurrency gate: acquire timed out after {} ms", name, acquireTimeoutMs);
            }
            return ok;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{} concurrency gate: interrupted while acquiring", name);
            return false;
        }
    }

    public void release() {
        if (semaphore != null) {
            semaphore.release();
        }
    }

    public boolean hasAvailablePermit() {
        return semaphore == null || semaphore.availablePermits() > 0;
    }

    public int availablePermits() {
        return semaphore == null ? Integer.MAX_VALUE : semaphore.availablePermits();
    }
}
