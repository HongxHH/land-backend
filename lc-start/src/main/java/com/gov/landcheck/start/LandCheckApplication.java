package com.gov.landcheck.start;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j;

/**
 * 使用 {@code scanBasePackages} 统一扫描各业务模块，避免与单独 {@code @ComponentScan} 叠加时
 * 覆盖默认行为导致部分包未被扫描（表现为某 {@code @Service} Bean 缺失）。
 */
@SpringBootApplication(scanBasePackages = "com.gov.landcheck")
@EnableKnife4j
public class LandCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(LandCheckApplication.class, args);
    }
}
