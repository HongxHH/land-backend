package com.gov.landcheck.core.config.mq;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MQ 消息相关配置入口。
 */
@Configuration
@EnableConfigurationProperties(MqMessagingProperties.class)
public class MqMessagingConfig {
}
