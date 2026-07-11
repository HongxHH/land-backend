package com.gov.landcheck.core.config.satoken;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 匿名访问路径与接口限流参数（与 application.yml 中 landcheck.security 对应）。
 */
@Data
@ConfigurationProperties(prefix = "landcheck.security")
public class LandcheckSecurityProperties {

    /**
     * 无需登录即可访问的路径（Ant 风格）。
     */
    private List<String> anonPaths = new ArrayList<>(List.of(
            "/error",
            "/auth/login",
            "/auth/register",
            "/auth/user-types",
            "/v3/api-docs/**",
            "/swagger-ui",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/doc.html",
            "/webjars/**",
            "/favicon.ico"));

    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class RateLimit {
        private boolean enabled = true;
        /** 单 IP 每秒最大请求数 */
        private int perIpPerSecond = 120;
        /**
         * 带 {@link com.gov.landcheck.core.ratelimit.UserRateLimit} 的方法：每用户每个窗口最大次数（与
         * user-window-seconds 组成固定窗口）。
         */
        private int perUserPerWindow = 60;
        /** 用户级限流窗口长度（秒），与 per-user-per-window 配套 */
        private int userWindowSeconds = 60;
        /** 不参与全局限流的路径前缀 */
        private List<String> skipPathPrefixes = new ArrayList<>(List.of(
                "/webjars/",
                "/swagger-ui",
                "/v3/api-docs",
                "/doc.html",
                "/favicon.ico",
                "/error"));
    }
}
