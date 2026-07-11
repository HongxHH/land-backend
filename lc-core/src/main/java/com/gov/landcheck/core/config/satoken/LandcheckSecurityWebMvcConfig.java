package com.gov.landcheck.core.config.satoken;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.landcheck.core.audit.OperatorContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册 Sa-Token 登录校验与全局限流拦截器。
 */
@Configuration
@EnableConfigurationProperties(LandcheckSecurityProperties.class)
@RequiredArgsConstructor
public class LandcheckSecurityWebMvcConfig implements WebMvcConfigurer {

    private final LandcheckSecurityProperties landcheckSecurityProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final OperatorContextInterceptor operatorContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(
                        new ApiRateLimitInterceptor(landcheckSecurityProperties, stringRedisTemplate, objectMapper))
                .addPathPatterns("/**")
                .order(Ordered.HIGHEST_PRECEDENCE);

        String[] anon = landcheckSecurityProperties.getAnonPaths().toArray(String[]::new);
        // 数值更小者优先 preHandle：须先于 OperatorContextInterceptor，保证登录校验与上下文顺序稳定
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(anon)
                .order(Ordered.LOWEST_PRECEDENCE - 10);

        // 在 SaInterceptor 之后执行，使用已就绪的 Sa-Token 上下文覆盖操作人信息
        registry.addInterceptor(operatorContextInterceptor)
                .addPathPatterns("/**")
                .order(Ordered.LOWEST_PRECEDENCE);
    }
}
