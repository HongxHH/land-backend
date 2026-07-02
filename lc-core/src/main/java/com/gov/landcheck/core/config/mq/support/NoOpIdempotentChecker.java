package com.gov.landcheck.core.config.mq.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 幂等校验空实现：不校验、不标记，适用于不要求幂等的消费者或测试。
 * <p>
 * <b>策略（fail-open）</b>：{@link #tryMarkProcessed} 恒返回
 * {@link IdempotentResult#SUCCESS}，等价于不启用幂等。
 * </p>
 */
@Component
@ConditionalOnMissingBean(IdempotentChecker.class)
public class NoOpIdempotentChecker implements IdempotentChecker {

    @Override
    public boolean isProcessed(String idempotentKey) {
        return false;
    }

    @Override
    public void markProcessed(String idempotentKey, long ttlSeconds) {
        // no-op
    }

    @Override
    public IdempotentResult tryMarkProcessed(String idempotentKey, long ttlSeconds) {
        return IdempotentResult.SUCCESS;
    }
}
