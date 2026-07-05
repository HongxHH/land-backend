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
    private Semaphore semaphore;
    private final long acquireTimeoutMs;
    private volatile int maxPermits;

    public ProcessingConcurrencyGate(String name, int maxConcurrent, long acquireTimeoutMs) {
        this.name = name;
        this.acquireTimeoutMs = acquireTimeoutMs <= 0 ? 30_000L : acquireTimeoutMs;
        this.maxPermits = Math.max(0, maxConcurrent);
        this.semaphore = createSemaphore(this.maxPermits);
    }

    public boolean tryAcquire() {
        Semaphore current = semaphore;
        if (current == null) {
            return true;
        }
        try {
            boolean ok = current.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
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
        Semaphore current = semaphore;
        if (current != null) {
            current.release();
        }
    }

    public int availablePermits() {
        Semaphore current = semaphore;
        return current == null ? Integer.MAX_VALUE : current.availablePermits();
    }

    public int getMaxPermits() {
        return maxPermits;
    }

    public int getUsedPermits() {
        if (maxPermits <= 0 || semaphore == null) {
            return 0;
        }
        return Math.max(0, maxPermits - semaphore.availablePermits());
    }

    /**
     * 动态调整许可上限。扩容立即生效；缩容仅在空闲许可足够回收时允许。
     */
    public synchronized void resizeTo(int newMaxPermits) {
        if (newMaxPermits < 1) {
            throw new IllegalArgumentException(name + " concurrency gate: newMaxPermits must be >= 1");
        }
        if (newMaxPermits == maxPermits) {
            return;
        }
        if (semaphore == null) {
            maxPermits = newMaxPermits;
            semaphore = createSemaphore(newMaxPermits);
            log.info("{} concurrency gate resized: maxPermits -> {}", name, newMaxPermits);
            return;
        }
        if (newMaxPermits > maxPermits) {
            int delta = newMaxPermits - maxPermits;
            maxPermits = newMaxPermits;
            semaphore.release(delta);
            log.info("{} concurrency gate expanded: maxPermits +{} -> {}", name, delta, newMaxPermits);
            return;
        }
        int delta = maxPermits - newMaxPermits;
        int drained = semaphore.drainPermits();
        int inUse = maxPermits - drained;
        if (inUse > newMaxPermits) {
            if (drained > 0) {
                semaphore.release(drained);
            }
            throw new IllegalStateException(String.format(
                    "%s 并发闸门无法缩容至 %d：当前 %d 路任务占用中",
                    name, newMaxPermits, inUse));
        }
        maxPermits = newMaxPermits;
        int toRelease = newMaxPermits - inUse;
        if (toRelease > 0) {
            semaphore.release(toRelease);
        }
        log.info("{} concurrency gate shrunk: maxPermits -> {}, inUse={}", name, newMaxPermits, inUse);
    }

    private static Semaphore createSemaphore(int permits) {
        if (permits <= 0) {
            return null;
        }
        return new Semaphore(permits, true);
    }
}
