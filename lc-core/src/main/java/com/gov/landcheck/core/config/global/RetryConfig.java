package com.gov.landcheck.core.config.global;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Configuration;

import lombok.Getter;

/**
 * 解析任务重试硬编码参数（不再从 application.yml 读取）。
 */
@Getter
@Configuration
public class RetryConfig {

    /** 最大重试次数 */
    private final int maxAttempts = 2;

    /** 初始延迟时间（毫秒） */
    private final long initialDelayMs = 2000L;

    /** 退避倍数 */
    private final double backoffMultiplier = 2.0D;

    /** 最大延迟时间（毫秒） */
    private final long maxDelayMs = 300_000L;

    /** 错误类型到重试策略的映射 */
    private final Map<String, Boolean> retryableErrors = new HashMap<>();

    public RetryConfig() {
        retryableErrors.put("timeout", true);
        retryableErrors.put("connection", true);
        retryableErrors.put("network", true);
        retryableErrors.put("database", true);
        retryableErrors.put("io", true);

        retryableErrors.put("parse", false);
        retryableErrors.put("permission", false);
        retryableErrors.put("validation", false);
        retryableErrors.put("invalid", false);
        retryableErrors.put("unknown", false);
    }

    public boolean isRetryable(String errorType) {
        return retryableErrors.getOrDefault(errorType, false);
    }

    public long calculateDelay(int attemptCount) {
        long delay = (long) (initialDelayMs * Math.pow(backoffMultiplier, attemptCount - 1));
        return Math.min(delay, maxDelayMs);
    }
}
