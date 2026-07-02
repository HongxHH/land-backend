package com.gov.landcheck.core.config.satoken;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

/**
 * Redis 固定窗口计数（INCR + 首次 EXPIRE），供全局限流与 {@link UserRateLimit} 共用。
 */
@Slf4j
public final class RateLimitRedisHelper {

    private static final DefaultRedisScript<Long> FIXED_WINDOW_SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            if current <= tonumber(ARGV[1]) then
                return 1
            end
            return 0
            """,
            Long.class
    );

    private RateLimitRedisHelper() {
    }

    /**
     * @return true 表示未超限（通过），false 表示超限
     */
    public static boolean tryAcquire(StringRedisTemplate template, String key, int maxAllowed, int expireSeconds) {
        try {
            int lim = Math.max(1, maxAllowed);
            int ttl = Math.max(1, expireSeconds);
            Long allowed = template.execute(
                    FIXED_WINDOW_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(lim),
                    String.valueOf(ttl));
            return allowed == null || allowed == 1L;
        } catch (Exception e) {
            log.warn("限流 Redis 异常，本次放行: key={}, {}", key, e.getMessage());
            return true;
        }
    }
}
