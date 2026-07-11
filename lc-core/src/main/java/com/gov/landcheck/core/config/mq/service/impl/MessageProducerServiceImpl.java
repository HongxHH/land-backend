package com.gov.landcheck.core.config.mq.service.impl;

import java.util.Objects;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.config.mq.config.RocketMQProperties;
import com.gov.landcheck.core.config.mq.exception.MessageSendException;
import com.gov.landcheck.core.config.mq.serializer.MessageSerializer;
import com.gov.landcheck.core.config.mq.service.MessageProducerService;

import lombok.extern.slf4j.Slf4j;

/**
 * 消息生产端实现：统一序列化、超时与重试，封装 RocketMQTemplate。
 *
 * @author landcheck
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "landcheck.rocketmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MessageProducerServiceImpl implements MessageProducerService {

    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMQProperties properties;
    private final MessageSerializer messageSerializer;

    @Autowired
    public MessageProducerServiceImpl(
            RocketMQTemplate rocketMQTemplate,
            RocketMQProperties properties,
            MessageSerializer messageSerializer) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
        this.messageSerializer = messageSerializer;
    }

    @Override
    public void send(String topic, String tag, String key, Object payload) {
        Objects.requireNonNull(topic, "topic");
        byte[] bodyBytes = messageSerializer.serialize(payload);
        if (bodyBytes == null) {
            throw new MessageSendException("消息序列化失败: payload=" + (payload != null ? payload.getClass().getName() : "null"));
        }
        Message msg = new Message(topic, tag != null ? tag : "", bodyBytes);
        if (key != null && !key.isEmpty()) {
            msg.setKeys(key);
        }
        int timeoutMs = properties.getProducer().getSendTimeoutMs();
        int maxRetry = properties.getProducer().getRetryTimesWhenSendFailed();
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                SendResult result = rocketMQTemplate.getProducer().send(msg, timeoutMs);

                log.debug("消息发送成功: topic={}, tag={}, key={}, msgId={}", topic, tag, key, result.getMsgId());

                return;
            } catch (Exception e) {
                lastException = e;
                boolean retryable = isRetryable(e);
                log.warn("消息发送失败(attempt={}/{}): topic={}, tag={}, key={}, retryable={}",
                        attempt, maxRetry, topic, tag, key, retryable, e);
                if (!retryable || attempt == maxRetry) {
                    break;
                }
            }
        }
        throw new MessageSendException(
                "消息发送失败: topic=" + topic + ", tag=" + tag + ", key=" + key,
                lastException,
                false);
    }

    private static boolean isRetryable(Exception e) {
        if (e instanceof MQClientException) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("timed out") || msg.contains("connection") || msg.contains("network"))) {
                return true;
            }
        }
        Throwable cause = e.getCause();
        if (cause instanceof Exception) {
            return isRetryable((Exception) cause);
        }
        return false;
    }
}
