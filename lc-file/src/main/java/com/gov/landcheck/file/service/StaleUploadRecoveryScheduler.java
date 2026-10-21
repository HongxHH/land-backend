package com.gov.landcheck.file.service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.gov.landcheck.file.config.FileProcessingProperties;
import com.gov.landcheck.file.config.FileRetrySchedulerConfig;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * 周期性扫描超时的上传中 / 后处理中文件并标为 UPLOAD_FAIL。
 */
@Slf4j
@Component
public class StaleUploadRecoveryScheduler {

    private final FileProcessingProperties properties;
    private final StaleUploadRecoveryService recoveryService;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean tickRunning = new AtomicBoolean(false);

    public StaleUploadRecoveryScheduler(
            FileProcessingProperties properties,
            StaleUploadRecoveryService recoveryService,
            @Qualifier(FileRetrySchedulerConfig.PARSE_RETRY_SCHEDULER) ScheduledExecutorService scheduler) {
        this.properties = properties;
        this.recoveryService = recoveryService;
        this.scheduler = scheduler;
    }

    @PostConstruct
    void schedule() {
        FileProcessingProperties.StaleUpload cfg = properties.getStaleUpload();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }
        long intervalMs = cfg.getScanIntervalMs();
        scheduler.scheduleWithFixedDelay(this::tick, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("僵死上传状态定时扫描已启动: intervalMs={}", intervalMs);
    }

    void tick() {
        if (!tickRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            recoveryService.recoverTimedOut();
        } catch (Exception e) {
            log.warn("僵死上传状态定时扫描失败: {}", e.getMessage(), e);
        } finally {
            tickRunning.set(false);
        }
    }
}
