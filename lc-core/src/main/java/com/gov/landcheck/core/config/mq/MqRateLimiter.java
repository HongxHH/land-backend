package com.gov.landcheck.core.config.mq;

/**
 * MQ 生产端发送前限流器。
 */
public interface MqRateLimiter {

    /**
     * @param key 限流维度 key（当前实现为全局限流，参数保留扩展）
     * @return true 表示允许发送
     */
    boolean tryAcquire(String key);
}
