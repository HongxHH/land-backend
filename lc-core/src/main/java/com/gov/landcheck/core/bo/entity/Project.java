package com.gov.landcheck.core.bo.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 开发项目基本信息实体类
 *
 * @author system
 * @date 2025/12/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "project")
@Schema(description = "开发项目基本信息")
public class Project extends MongoIdEntity {

    @Field(name = "project_name")
    @Schema(description = "项目名称", example = "XX住宅小区项目")
    private String projectName;

    @Field(name = "project_time")
    @Schema(description = "项目时间（业务日期，ISO 自然日）", example = "2025-11-15")
    private String projectTime;

}
