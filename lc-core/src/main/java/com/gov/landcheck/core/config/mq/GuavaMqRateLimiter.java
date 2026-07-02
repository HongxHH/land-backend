package com.gov.landcheck.core.config.mq;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.google.common.util.concurrent.RateLimiter;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Guava RateLimiter 的 MQ 生产端全局限流。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "landcheck.mq.rate-limit", name = "enabled", havingValue = "true")
public class GuavaMqRateLimiter implements MqRateLimiter {

    private final MqMessagingProperties properties;
    private volatile RateLimiter globalLimiter;

    public GuavaMqRateLimiter(MqMessagingProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        globalLimiter = RateLimiter.create(properties.getRateLimit().getTps());
        log.debug("MQ 生产限流已启用: tps={}", properties.getRateLimit().getTps());
    }

    @Override
    public boolean tryAcquire(String key) {
        if (!properties.getRateLimit().isEnabled()) {
            return true;
        }
        return globalLimiter != null && globalLimiter.tryAcquire();
    }
}
