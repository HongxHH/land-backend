package com.gov.landcheck.core.config.mq.support;

/**
 * 消费端幂等校验：判断消息是否已处理、标记已处理。
 * 可由 Redis/DB 等实现，供 {@link AbstractIdempotentMessageListener} 使用。
 * <p>
 * {@link #tryMarkProcessed(String, long)} 的语义与异常策略由各实现类在 JavaDoc 中说明；调用方应对
 * {@link IdempotentResult#UNKNOWN} 做重试或拒绝处理，避免在不确定占位时执行业务写操作。
 * </p>
 *
 * @author landcheck
 */
public interface IdempotentChecker {

    /**
     * 判断该幂等 key 是否已处理
     *
     * @param idempotentKey 幂等 key（如 messageId 或业务 key）
     * @return true 表示已处理，应跳过消费
     */
    boolean isProcessed(String idempotentKey);

    /**
     * 标记该幂等 key 已处理
     *
     * @param idempotentKey 幂等 key
     * @param ttlSeconds    标记过期时间（秒），过期后可再次消费
     */
    void markProcessed(String idempotentKey, long ttlSeconds);

    /**
     * 原子地尝试占位「将处理该幂等 key」：
     * <ul>
     * <li>{@link IdempotentResult#SUCCESS}：当前调用方获得处理权，应继续消费。</li>
     * <li>{@link IdempotentResult#FAILED}：已被处理或占位，应跳过消费。</li>
     * <li>{@link IdempotentResult#UNKNOWN}：存储层异常，不应执行业务逻辑，应触发重试。</li>
     * </ul>
     *
     * @param idempotentKey 幂等 key
     * @param ttlSeconds    占位 TTL（秒）
     * @return 占位结果，永不为 null
     */
    IdempotentResult tryMarkProcessed(String idempotentKey, long ttlSeconds);

    /**
     * 清理幂等占位（用于可重试失败时释放）。
     */
    default void clearProcessed(String idempotentKey) {
        // default no-op
    }
}
