package com.gov.landcheck.core.service;

import java.util.List;

import com.gov.landcheck.core.bo.dto.UsageConfigRelatedFileQueryResultDTO;
import com.gov.landcheck.core.bo.entity.UsageConfig;

/**
 * 用途配置服务接口
 * 负责用途配置的查询和管理
 *
 * @author system
 * @date 2025/01/21
 */
public interface UsageConfigService {

    /**
     * 根据用途名称匹配配置
     * 支持包含匹配和正则匹配
     *
     * @param usageName 用途名称
     * @return 匹配的用途配置，如果未匹配到返回null
     */
    UsageConfig matchUsageConfig(String usageName);

    /**
     * 获取所有启用的用途配置
     * 按优先级排序（数值越小优先级越高）
     *
     * @return 用途配置列表
     */
    List<UsageConfig> getAllEnabledConfigs();

    /**
     * 根据ID获取用途配置
     *
     * @param id 配置ID
     * @return 用途配置
     */
    UsageConfig getById(Long id);

    /**
     * 新增用途配置
     *
     * @param usageConfig 用途配置（id 将被忽略）
     * @return 保存后的配置
     */
    UsageConfig create(UsageConfig usageConfig);

    /**
     * 更新用途配置
     *
     * @param usageConfig 用途配置（需带 id）
     * @return 保存后的配置
     */
    UsageConfig update(UsageConfig usageConfig);

    /**
     * 保存用途配置（内部使用，create/update 会调用）
     *
     * @param usageConfig 用途配置
     * @return 保存后的配置
     */
    UsageConfig save(UsageConfig usageConfig);

    /**
     * 批量保存用途配置
     *
     * @param usageConfigs 用途配置列表
     * @return 保存后的配置列表
     */
    List<UsageConfig> saveAll(List<UsageConfig> usageConfigs);

    /**
     * 删除用途配置
     *
     * @param id 配置ID
     */
    void deleteById(Long id);

    /**
     * 根据用途类别获取配置列表
     *
     * @param usageCategory 用途类别
     * @return 配置列表
     */
    List<UsageConfig> getByUsageCategory(String usageCategory);

    /**
     * 根据面积类型获取配置列表
     *
     * @param floorAreaType 面积类型
     * @return 配置列表
     */
    List<UsageConfig> getByFloorAreaType(String floorAreaType);

    /**
     * 检查用途名称是否已存在
     *
     * @param usageName 用途名称
     * @return true如果存在，false如果不存在
     */
    boolean existsByUsageName(String usageName);

    /**
     * 根据用途名称查找配置
     *
     * @param usageName 用途名称
     * @return 用途配置，如果不存在返回null
     */
    UsageConfig findByUsageName(String usageName);

    /**
     * 分页查询命中指定用途配置的文件列表
     */
    UsageConfigRelatedFileQueryResultDTO listRelatedFiles(UsageConfig usageConfig, Integer pageNum, Integer pageSize,
            String keyword);
}