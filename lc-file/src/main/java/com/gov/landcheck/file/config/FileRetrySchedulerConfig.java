package com.gov.landcheck.file.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 解析任务延迟重试等后台调度：有界线程池，避免无界 new Thread。
 */
@Configuration
public class FileRetrySchedulerConfig {

    public static final String PARSE_RETRY_SCHEDULER = "parseRetryScheduler";

    @Bean(name = PARSE_RETRY_SCHEDULER, destroyMethod = "shutdown")
    public ScheduledExecutorService parseRetryScheduler() {
        return Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "parse-retry");
            t.setDaemon(true);
            return t;
        });
    }
}
