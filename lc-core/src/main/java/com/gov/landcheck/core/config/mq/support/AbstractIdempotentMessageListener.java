package com.gov.landcheck.core.config.mq.support;

import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;

import com.gov.landcheck.core.config.mq.exception.MessageConsumeException;

import lombok.extern.slf4j.Slf4j;

/**
 * 带幂等支撑的消息消费基类。
 *
 * @author landcheck
 */
@Slf4j
public abstract class AbstractIdempotentMessageListener implements RocketMQListener<MessageExt> {

    /** 幂等标记默认 TTL（秒） */
    private static final long DEFAULT_IDEMPOTENT_TTL_SECONDS = 86400L;

    @Autowired(required = false)
    private IdempotentChecker idempotentChecker;

    @Override
    public void onMessage(MessageExt messageExt) {
        String key = resolveIdempotentKey(messageExt);
        if (idempotentChecker != null) {
            IdempotentResult idempotentResult = idempotentChecker.tryMarkProcessed(key, DEFAULT_IDEMPOTENT_TTL_SECONDS);
            if (idempotentResult == IdempotentResult.FAILED) {
                if (log.isDebugEnabled()) {
                    log.debug("消息已处理，跳过: msgId={}, key={}", messageExt.getMsgId(), key);
                }
                return;
            }
            if (idempotentResult == IdempotentResult.UNKNOWN) {
                throw new MessageConsumeException(
                        "幂等存储状态未知，触发消息重试: msgId=" + messageExt.getMsgId() + ", key=" + key, true);
            }
        }
        try {
            handleMessage(messageExt);
        } catch (Exception e) {
            if (e instanceof MessageConsumeException mce && !mce.isRetryable()) {
                log.error("消息消费不可重试异常，不再重试: msgId={}, key={}", messageExt.getMsgId(), key, e);
                return;
            }
            if (idempotentChecker != null) {
                // 可重试错误需要释放占位，避免后续重试被幂等误拦截
                idempotentChecker.clearProcessed(key);
            }
            log.warn("消息消费异常，将重试: msgId={}, key={}", messageExt.getMsgId(), key, e);
            throw e instanceof RuntimeException re ? re : new MessageConsumeException(e.getMessage(), e, true);
        }
    }

    /**
     * 解析幂等 key，默认使用 msgId，子类可覆盖为业务 key（如 messageExt.getKeys()）
     */
    protected String resolveIdempotentKey(MessageExt messageExt) {
        String keys = messageExt.getKeys();
        if (keys != null && !keys.isEmpty()) {
            return keys;
        }
        return messageExt.getMsgId();
    }

    /**
     * 子类实现：处理单条消息
     *
     * @param messageExt 消息
     * @throws Exception 可重试时抛出，将触发 RocketMQ 重试；不可重试时抛
     *                   MessageConsumeException(retryable=false)
     */
    protected abstract void handleMessage(MessageExt messageExt) throws Exception;
}
