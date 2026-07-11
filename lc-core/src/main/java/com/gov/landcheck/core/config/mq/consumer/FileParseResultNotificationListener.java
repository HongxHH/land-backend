package com.gov.landcheck.core.config.mq.consumer;

import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.config.mq.constant.TopicConstants;
import com.gov.landcheck.core.config.mq.dto.FileParseResultMessage;
import com.gov.landcheck.core.config.mq.serializer.MessageSerializer;
import com.gov.landcheck.core.config.mq.support.AbstractIdempotentMessageListener;
import com.gov.landcheck.core.config.notification.BroadcastTopicConstants;
import com.gov.landcheck.core.service.StationBroadcastService;
import com.gov.landcheck.core.service.station.StationBroadcastPayloadBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件解析结果通知消费者：统一转为站内广播（含持久化）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "landcheck.rocketmq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(topic = TopicConstants.TOPIC_FILE_PARSE_RESULT, consumerGroup = "lc-file-parse-notify-consumer")
public class FileParseResultNotificationListener extends AbstractIdempotentMessageListener {

    private final MessageSerializer messageSerializer;
    private final StationBroadcastService stationBroadcastService;

    public FileParseResultNotificationListener(MessageSerializer messageSerializer,
            StationBroadcastService stationBroadcastService) {
        this.messageSerializer = messageSerializer;
        this.stationBroadcastService = stationBroadcastService;
    }

    @Override
    protected void handleMessage(MessageExt messageExt) throws Exception {
        FileParseResultMessage payload = messageSerializer.deserialize(messageExt.getBody(),
                FileParseResultMessage.class);
        if (payload == null) {
            log.warn("解析结果消息反序列化失败: msgId={}", messageExt.getMsgId());
            return;
        }
        stationBroadcastService.broadcast(
                BroadcastTopicConstants.FILE_UPDATES,
                StationBroadcastPayloadBuilder.fromFileParseResult(payload));
    }
}
