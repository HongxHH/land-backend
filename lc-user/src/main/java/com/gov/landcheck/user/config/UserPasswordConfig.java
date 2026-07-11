package com.gov.landcheck.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 用户口令编码（BCrypt），供注册、改密与存量 MD5 迁移使用。
 */
@Configuration
public class UserPasswordConfig {

    @Bean
    public BCryptPasswordEncoder userPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
