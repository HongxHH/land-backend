package com.gov.landcheck.core.config.global;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Author: Administrator
 * Date: 2025/12/19
 * Description:跨域配置
 * :
 */

@Configuration
public class CorsConfig {
    @Bean
    CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        // 允许源，这里允许所有源访问，实际应用会加以限制
        corsConfiguration.addAllowedOrigin("*");
        // 允许所有请求头
        corsConfiguration.addAllowedHeader("*");
        // 允许所有方法
        corsConfiguration.addAllowedMethod("*");
        // Sa-Token 默认头名 satoken，便于前端读取登录接口下发的令牌
        corsConfiguration.addExposedHeader("satoken");
        source.registerCorsConfiguration("/**", corsConfiguration);
        return new CorsFilter(source);
    }
}
