package com.gov.landcheck.core.config.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * MQ 消息配置（landcheck.mq）：生产端限流、消费端幂等。
 */
@Data
@ConfigurationProperties(prefix = "landcheck.mq")
public class MqMessagingProperties {

    /** 生产端限流 */
    private RateLimit rateLimit = new RateLimit();

    /** 消费端幂等 */
    private Idempotent idempotent = new Idempotent();

    @Data
    public static class RateLimit {
        private boolean enabled = true;
        private double tps = 10.0;
    }

    @Data
    public static class Idempotent {
        private boolean enabled = true;
        private long ttlSeconds = 86400L;
        private String keyPrefix = "mq:idempotent:";
    }
}
