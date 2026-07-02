package com.gov.landcheck.core.config.mq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * RocketMQ 项目封装配置（landcheck.rocketmq），与 rocketmq.name-server、rocketmq.producer.group 等配合使用。
 *
 * @author landcheck
 */
@Data
@ConfigurationProperties(prefix = "landcheck.rocketmq")
public class RocketMQProperties {

    /** 是否启用本项目的 MQ 封装 */
    private boolean enabled = true;

    /** 序列化方式：json / jackson */
    private String serializer = "json";

    private Producer producer = new Producer();
    private Consumer consumer = new Consumer();

    @Data
    public static class Producer {
        /** 发送超时时间（毫秒） */
        private int sendTimeoutMs = 5000;
        /** 同步发送失败重试次数 */
        private int retryTimesWhenSendFailed = 3;
    }

    @Data
    public static class Consumer {
    }
}
