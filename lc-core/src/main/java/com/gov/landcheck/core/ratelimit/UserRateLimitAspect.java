package com.gov.landcheck.core.ratelimit;

import cn.dev33.satoken.stp.StpUtil;
import com.gov.landcheck.core.config.satoken.LandcheckSecurityProperties;
import com.gov.landcheck.core.config.satoken.RateLimitRedisHelper;
import com.gov.landcheck.core.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;

/**
 * 处理 {@link UserRateLimit}：按登录用户 id + 维度 + 时间桶固定窗口计数。
 */
@Aspect
@Component
@Order(50)
@RequiredArgsConstructor
public class UserRateLimitAspect {

    private static final String REDIS_KEY_PREFIX = "landcheck:rl:user:";

    private final LandcheckSecurityProperties securityProperties;
    private final StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(anno)")
    public Object around(ProceedingJoinPoint pjp, UserRateLimit anno) throws Throwable {
        LandcheckSecurityProperties.RateLimit rl = securityProperties.getRateLimit();
        if (!rl.isEnabled()) {
            return pjp.proceed();
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return pjp.proceed();
        }
        int window = anno.windowSeconds() > 0 ? anno.windowSeconds() : Math.max(1, rl.getUserWindowSeconds());
        int max = anno.maxRequests() > 0 ? anno.maxRequests() : Math.max(1, rl.getPerUserPerWindow());
        String dim = anno.value();
        if (dim == null || dim.isBlank()) {
            Method m = ((MethodSignature) pjp.getSignature()).getMethod();
            dim = m.getDeclaringClass().getSimpleName() + "#" + m.getName();
        } else {
            dim = dim.replaceAll("[^a-zA-Z0-9_-]", "_");
        }
        long epoch = Instant.now().getEpochSecond();
        long bucket = epoch / window;
        String key = REDIS_KEY_PREFIX + loginId + ":" + dim + ":" + bucket;
        if (!RateLimitRedisHelper.tryAcquire(stringRedisTemplate, key, max, window + 1)) {
            throw new RateLimitExceededException(anno.message());
        }
        return pjp.proceed();
    }
}
