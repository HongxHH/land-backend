package com.gov.landcheck.core.config.mq.support;

/**
 * 消费端幂等「占位」结果，用于区分成功占位、已处理与存储层异常。
 * <p>
 * <b>fail-closed（Redis 实现）</b>：存储异常时返回 {@link #UNKNOWN}，由
 * {@link com.gov.landcheck.core.config.mq.support.AbstractIdempotentMessageListener}
 * 抛出可重试异常，
 * 触发 MQ 重试，避免在无法确认占位时重复执行业务逻辑（与原先 Redis 异常返回「成功占位」的 fail-open 语义不同）。
 * </p>
 * <p>
 * <b>fail-open（NoOp 实现）</b>：无幂等存储时恒为 {@link #SUCCESS}，等价于不启用幂等。
 * </p>
 *
 * @author landcheck
 */
public enum IdempotentResult {

    /** 当前消费者原子占位成功，可继续消费业务逻辑 */
    SUCCESS,

    /** 该幂等 key 已被处理（或已被其他实例占位），应跳过本次消费 */
    FAILED,

    /**
     * 无法向存储层确认占位结果（如 Redis 故障）。不应执行业务逻辑，应触发消息重试或交由 MQ 重投递。
     */
    UNKNOWN
}
