package com.gov.landcheck.file.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.gov.landcheck.file.config.FileProcessingConfig;
import com.gov.landcheck.file.config.FileProcessingProperties;
import com.gov.landcheck.file.dto.TaskStatusDTO;
import com.gov.landcheck.file.processing.ProcessingConcurrencyGate;
import com.gov.landcheck.file.task.thread.TaskThreadPool;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * 解析并行度 N 的统一入口：线程池 core/max 与 parse-pipeline gate 联动更新。
 */
@Slf4j
@Service
public class ParseConcurrencyService {

    private final FileProcessingProperties properties;
    private final TaskThreadPool taskThreadPool;
    private final ProcessingConcurrencyGate parsePipelineGate;

    public ParseConcurrencyService(
            FileProcessingProperties properties,
            TaskThreadPool taskThreadPool,
            @Qualifier(FileProcessingConfig.PARSE_PIPELINE_GATE) ProcessingConcurrencyGate parsePipelineGate) {
        this.properties = properties;
        this.taskThreadPool = taskThreadPool;
        this.parsePipelineGate = parsePipelineGate;
    }

    @PostConstruct
    void alignAtStartup() {
        int n = properties.getParsePool().getMaxSize();
        try {
            applyConcurrency(n, true);
            log.info("解析并行度已对齐: N={}", n);
        } catch (Exception e) {
            log.warn("启动时对齐解析并行度失败，将尝试以 gate 配置为准: {}", e.getMessage());
            int fallback = Math.max(1, parsePipelineGate.getMaxPermits());
            applyConcurrency(fallback, true);
        }
    }

    public synchronized void updateConcurrency(int requested) {
        int n = normalizeRequested(requested);
        applyConcurrency(n, false);
    }

    public void enrichThreadPoolStatus(TaskStatusDTO.ThreadPoolStatus status) {
        if (status == null) {
            return;
        }
        int limit = parsePipelineGate.getMaxPermits();
        status.setParseConcurrency(limit);
        status.setParseConcurrencyHardLimit(getHardLimit());
        status.setPipelineActivePermits(parsePipelineGate.getUsedPermits());
        status.setPipelineAvailablePermits(parsePipelineGate.availablePermits());
    }

    private int normalizeRequested(int requested) {
        if (requested < 1) {
            throw new IllegalArgumentException("parseConcurrency 必须 >= 1");
        }
        int hardLimit = getHardLimit();
        if (requested > hardLimit) {
            throw new IllegalArgumentException(
                    "parseConcurrency 不能超过上限 " + hardLimit);
        }
        return requested;
    }

    private void applyConcurrency(int n, boolean startup) {
        int current = getCurrentConcurrency();
        if (n == current && parsePipelineGate.getMaxPermits() == n) {
            persistSettings(n);
            return;
        }
        if (n < current) {
            taskThreadPool.updateConcurrency(n);
            parsePipelineGate.resizeTo(n);
        } else {
            parsePipelineGate.resizeTo(n);
            taskThreadPool.updateConcurrency(n);
        }
        persistSettings(n);
        if (!startup) {
            log.info("解析并行度已更新: N={} (pool core=max={}, gate={})",
                    n, n, parsePipelineGate.getMaxPermits());
        }
    }

    private int getCurrentConcurrency() {
        return taskThreadPool.getMaximumPoolSize();
    }

    private int getHardLimit() {
        return Math.max(1, properties.getConcurrency().getMaxParseConcurrencyLimit());
    }

    private void persistSettings(int n) {
        FileProcessingProperties.Pool parsePool = properties.getParsePool();
        parsePool.setCoreSize(n);
        parsePool.setMaxSize(n);
    }
}
