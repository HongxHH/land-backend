package com.gov.landcheck.core.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用户级固定窗口限流（Redis）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserRateLimit {

    /**
     * 业务维度，拼入 Redis key；空则使用 {@code 类名#方法名}。
     */
    String value() default "";

    /** 窗口内最大次数，≤0 表示使用配置 {@code per-user-per-window} */
    int maxRequests() default -1;

    /** 窗口长度（秒），≤0 表示使用配置 {@code user-window-seconds} */
    int windowSeconds() default -1;

    String message() default "操作过于频繁，请稍后再试";
}
