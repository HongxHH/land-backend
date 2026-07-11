package com.gov.landcheck.core.config.mq.support;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.config.mq.MqMessagingProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Redis 的消费端幂等校验：同一幂等 key 在 TTL 内只处理一次。
 */
@Slf4j
@Component
@ConditionalOnBean(RedisTemplate.class)
@ConditionalOnProperty(prefix = "landcheck.mq.idempotent", name = "enabled", havingValue = "true")
public class RedisIdempotentChecker implements IdempotentChecker {

    private static final String VALUE_PLACEHOLDER = "1";

    private final RedisTemplate<String, Object> redisTemplate;
    private final MqMessagingProperties properties;

    public RedisIdempotentChecker(RedisTemplate<String, Object> redisTemplate,
            MqMessagingProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    private String fullKey(String idempotentKey) {
        return properties.getIdempotent().getKeyPrefix() + idempotentKey;
    }

    @Override
    public boolean isProcessed(String idempotentKey) {
        String key = fullKey(idempotentKey);
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("幂等校验 Redis 查询异常: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public void markProcessed(String idempotentKey, long ttlSeconds) {
        String key = fullKey(idempotentKey);
        try {
            redisTemplate.opsForValue().set(key, VALUE_PLACEHOLDER, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("幂等标记 Redis 写入异常: key={}, error={}", key, e.getMessage());
        }
    }

    @Override
    public IdempotentResult tryMarkProcessed(String idempotentKey, long ttlSeconds) {
        String key = fullKey(idempotentKey);
        try {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(key, VALUE_PLACEHOLDER, ttlSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ok) ? IdempotentResult.SUCCESS : IdempotentResult.FAILED;
        } catch (Exception e) {
            log.warn("幂等原子占位 Redis 写入异常: key={}, error={}", key, e.getMessage());
            return IdempotentResult.UNKNOWN;
        }
    }

    @Override
    public void clearProcessed(String idempotentKey) {
        String key = fullKey(idempotentKey);
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("幂等标记 Redis 删除异常: key={}, error={}", key, e.getMessage());
        }
    }
}
