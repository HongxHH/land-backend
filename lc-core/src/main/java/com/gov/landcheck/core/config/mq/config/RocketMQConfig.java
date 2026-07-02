package com.gov.landcheck.core.config.mq.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 公共配置入口：启用 landcheck.rocketmq 配置项。
 * 实际 Producer/Consumer 由 rocketmq-spring-boot-starter 根据 rocketmq.* 创建，
 * 本模块在此基础上提供 MessageProducerService、序列化、异常与幂等支撑。
 *
 * @author landcheck
 */
@Configuration
@EnableConfigurationProperties(RocketMQProperties.class)
@ConditionalOnProperty(prefix = "landcheck.rocketmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RocketMQConfig {
}
