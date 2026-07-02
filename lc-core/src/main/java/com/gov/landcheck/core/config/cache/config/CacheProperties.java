package com.gov.landcheck.core.config.cache.config;

import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * Redis 缓存硬编码参数（不再从 application.yml 读取）。
 */
@Component
@Getter
public class CacheProperties {

    private final boolean enabled = true;

    private final String env = "local";

    private final Key key = new Key();

    private final long defaultTtlSeconds = 300L;

    private final double jitterRatio = 0.15D;

    private final Lock lock = new Lock();

    private final Ttl ttl = new Ttl();

    @Getter
    public static class Key {
        private final String version = "v1";
    }

    @Getter
    public static class Lock {
        private final long waitMs = 80L;
        private final long leaseMs = 800L;
    }

    @Getter
    public static class Ttl {
        private final Project project = new Project();
    }

    @Getter
    public static class Project {
        private final long byId = 600L;
        private final long byRelation = 300L;
        private final long query = 120L;
    }
}
