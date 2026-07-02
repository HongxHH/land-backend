package com.gov.landcheck.core.bo.entity;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用途配置实体类
 * 用于管理用途与面积类别的映射关系
 *
 * @author system
 * @date 2025/01/21
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "usage_config")
@Schema(description = "用途配置")
public class UsageConfig extends MongoIdEntity {

    /**
     * 用途名称/匹配模式
     * 例如："住宅"、"商业"、"物业.*"
     */
    @Field(name = "usage_pattern")
    @Indexed
    @Schema(description = "用途名称/匹配模式", example = "住宅")
    private String usagePattern;

    /**
     * 用途类别
     * RESIDENTIAL: 住宅
     * COMMERCIAL: 商业
     * MANAGEMENT: 物管用房
     * OTHER_BUILDABLE: 其他计容
     * COMMUNITY: 社区用房
     * OTHER_PUBLIC: 其他公用
     */
    @Field(name = "usage_category")
    @Schema(description = "用途类别", example = "RESIDENTIAL")
    private String usageCategory;

    /**
     * 面积类型
     * BUILDABLE: 计容
     * NON_BUILDABLE: 不计容
     */
    @Field(name = "floor_area_type")
    @Schema(description = "面积类型：BUILDABLE(计容)/NON_BUILDABLE(不计容)", example = "BUILDABLE")
    private String floorAreaType;

    /**
     * 是否使用正则匹配
     * 0: 包含匹配（roomUsage.contains(pattern)）
     * 1: 正则匹配（Pattern.compile(pattern).matcher(roomUsage).find()）
     */
    @Field(name = "is_regex")
    @Schema(description = "是否使用正则匹配（0否 1是）", example = "0")
    private Integer isRegex;

    /**
     * 匹配优先级，数值越小优先级越高
     */
    @Field(name = "priority")
    @Schema(description = "匹配优先级，数值越小优先级越高", example = "100")
    private Integer priority;

    /**
     * 状态
     * 0: 禁用
     * 1: 启用
     */
    @Field(name = "status")
    @Schema(description = "状态（0禁用 1启用）", example = "1")
    private Integer status;

    /**
     * 备注说明
     */
    @Field(name = "remark")
    @Schema(description = "备注说明")
    private String remark;
}
