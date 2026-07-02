package com.gov.landcheck.core.config.global;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.bo.entity.UsageConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * 用途配置数据初始化器
 * 用于初始化系统默认的用途配置数据
 *
 * @author system
 * @date 2025/01/21
 */
@Slf4j
@Component
public class UsageConfigDataInitializer implements ApplicationRunner {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 应用启动后初始化默认用途配置数据
     * 使用增量检查的方式，只创建不存在的配置项
     */
    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        try {
            List<UsageConfig> defaultConfigs = createDefaultConfigs();
            int createdCount = 0;
            int skippedCount = 0;

            for (UsageConfig defaultConfig : defaultConfigs) {
                // 按用途模式检查是否已存在
                Query query = new Query(Criteria.where("usage_pattern").is(defaultConfig.getUsagePattern()));
                UsageConfig existing = mongoTemplate.findOne(query, UsageConfig.class);

                if (existing == null) {
                    // 不存在则创建
                    defaultConfig.preSave(); // 生成ID和其他必要字段
                    mongoTemplate.save(defaultConfig);
                    createdCount++;
                    log.debug("创建默认配置: {}", defaultConfig.getUsagePattern());
                } else {
                    skippedCount++;
                }
            }

            log.info("用途配置数据初始化完成，共检查 {} 项，新增 {} 项，跳过 {} 项",
                    defaultConfigs.size(), createdCount, skippedCount);
        } catch (Exception e) {
            log.error("初始化用途配置数据失败: {}", e.getMessage(), e);
            // 不抛出异常，避免影响应用启动
        }
    }

    /**
     * 创建默认的用途配置
     */
    private List<UsageConfig> createDefaultConfigs() {
        return Arrays.asList(
                // 住宅类
                createConfig("住宅", "RESIDENTIAL", "BUILDABLE", 100),
                createConfig("教育", "RESIDENTIAL", "BUILDABLE", 101),
                createConfig("幼儿园", "RESIDENTIAL", "BUILDABLE", 102),
                // 商业类
                createConfig("商业", "COMMERCIAL", "BUILDABLE", 200),
                createConfig("办公", "COMMERCIAL", "BUILDABLE", 201),

                // 物管用房类
                createConfig("物管", "MANAGEMENT", "BUILDABLE", 300),
                createConfig("物业", "MANAGEMENT", "BUILDABLE", 301),
                createConfig("物业服务用房", "MANAGEMENT", "BUILDABLE", 302),
                createConfig("物管用房", "MANAGEMENT", "BUILDABLE", 303),

                // 其他计容类
                createConfig("杂屋", "OTHER_BUILDABLE", "BUILDABLE", 400),
                createConfig("消控室", "OTHER_BUILDABLE", "BUILDABLE", 401),

                // 社区用房类（不计容）
                createConfig("社区用房", "COMMUNITY", "NON_BUILDABLE", 500),
                createConfig("居家养老服务站", "COMMUNITY", "NON_BUILDABLE", 501),
                createConfig("文化活动室", "COMMUNITY", "NON_BUILDABLE", 502),
                createConfig("社区", "COMMUNITY", "NON_BUILDABLE", 503),

                // 其他公用类（不计容）
                createConfig("公用设施", "OTHER_PUBLIC", "NON_BUILDABLE", 600),
                createConfig("车库", "OTHER_PUBLIC", "NON_BUILDABLE", 601),
                createConfig("报警间", "OTHER_PUBLIC", "NON_BUILDABLE", 602),
                createConfig("人防车库", "OTHER_PUBLIC", "NON_BUILDABLE", 603),
                createConfig("地下车库", "OTHER_PUBLIC", "NON_BUILDABLE", 604),
                createConfig("地下室楼梯", "OTHER_PUBLIC", "NON_BUILDABLE", 605),
                createConfig("垃圾站", "OTHER_PUBLIC", "NON_BUILDABLE", 607),
                createConfig("公共", "OTHER_PUBLIC", "NON_BUILDABLE", 608),
                createConfig("设备", "OTHER_PUBLIC", "NON_BUILDABLE", 609),
                createConfig("避难", "OTHER_PUBLIC", "NON_BUILDABLE", 610),
                createConfig("公卫", "OTHER_PUBLIC", "NON_BUILDABLE", 611));
    }

    /**
     * 创建用途配置对象
     */
    private UsageConfig createConfig(String pattern, String category, String areaType, Integer priority) {
        return createConfig(pattern, category, areaType, priority, 0);
    }

    /**
     * 创建用途配置对象（支持正则匹配）
     */
    private UsageConfig createConfig(String pattern, String category, String areaType, Integer priority,
            Integer isRegex) {
        UsageConfig config = new UsageConfig();
        config.setUsagePattern(pattern);
        config.setUsageCategory(category);
        config.setFloorAreaType(areaType);
        config.setPriority(priority);
        config.setIsRegex(isRegex);
        config.setStatus(1); // 启用状态
        config.setRemark("系统默认配置");
        return config;
    }
}