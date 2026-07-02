package com.gov.landcheck.core.config.cache.service;

import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CacheInvalidationService {

    private static final long DEFAULT_DELAY_MS = 500L;

    private final CacheOpsService cacheOpsService;
    private final ScheduledExecutorService delayedEvictExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "cache-delayed-evict");
        thread.setDaemon(true);
        return thread;
    });

    public CacheInvalidationService(CacheOpsService cacheOpsService) {
        this.cacheOpsService = cacheOpsService;
    }

    @PreDestroy
    public void shutdown() {
        delayedEvictExecutor.shutdown();
        try {
            if (!delayedEvictExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                delayedEvictExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            delayedEvictExecutor.shutdownNow();
        }
        log.debug("cache-delayed-evict executor shut down");
    }

    public void evictImmediately(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        keys.stream().filter(k -> k != null && !k.isBlank()).forEach(cacheOpsService::evict);
    }

    public void evictImmediatelyByPatterns(Collection<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return;
        }
        patterns.stream().filter(p -> p != null && !p.isBlank()).forEach(cacheOpsService::evictByPattern);
    }

    public void evictTwice(Collection<String> keys) {
        evictTwice(keys, DEFAULT_DELAY_MS);
    }

    public void evictTwice(Collection<String> keys, long delayMs) {
        evictImmediately(keys);
        delayedEvictExecutor.schedule(() -> {
            try {
                evictImmediately(keys);
            } catch (Exception ex) {
                log.warn("Delayed evict failed, keys={}", keys, ex);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    public void evictByPatternTwice(Collection<String> patterns) {
        evictByPatternTwice(patterns, DEFAULT_DELAY_MS);
    }

    public void evictByPatternTwice(Collection<String> patterns, long delayMs) {
        evictImmediatelyByPatterns(patterns);
        delayedEvictExecutor.schedule(() -> {
            try {
                evictImmediatelyByPatterns(patterns);
            } catch (Exception ex) {
                log.warn("Delayed pattern evict failed, patterns={}", patterns, ex);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

}
