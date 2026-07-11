package com.gov.landcheck.core.config.satoken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.landcheck.core.bo.R.AjaxJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * 基于 Redis 的简单全局限流：按客户端 IP、每秒固定窗口计数。
 */
@RequiredArgsConstructor
public class ApiRateLimitInterceptor implements HandlerInterceptor {

    private static final String REDIS_KEY_PREFIX = "landcheck:rl:ip:";

    private final LandcheckSecurityProperties securityProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {
        LandcheckSecurityProperties.RateLimit rl = securityProperties.getRateLimit();
        if (!rl.isEnabled()) {
            return true;
        }
        String path = normalizedPath(request);
        for (String prefix : rl.getSkipPathPrefixes()) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        String ip = clientIp(request);
        long epochSecond = Instant.now().getEpochSecond();
        String key = REDIS_KEY_PREFIX + ip + ":" + epochSecond;
        int limit = Math.max(1, rl.getPerIpPerSecond());
        if (!RateLimitRedisHelper.tryAcquire(stringRedisTemplate, key, limit, 2)) {
            response.setStatus(429);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            AjaxJson body = AjaxJson.get(429, "请求过于频繁，请稍后再试");
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return false;
        }
        return true;
    }

    private static String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        if (uri.isEmpty()) {
            return "/";
        }
        return uri;
    }

    private static String clientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
