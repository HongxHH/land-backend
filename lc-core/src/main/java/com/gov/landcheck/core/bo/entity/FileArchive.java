package com.gov.landcheck.core.bo.entity;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;
import com.gov.landcheck.core.enums.FileContextType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 归档文件夹实体
 * 每个项目默认存在多类归档夹：合同、实测报告、规划复核表、项目方实测汇总表、其他未归档文件等。

 *
 * @author system
 * @date 2026/02/05
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@Document(collection = "file_archive")
@Schema(description = "归档文件夹")
public class FileArchive extends MongoIdEntity {

    @Field(name = "project_id")
    @Indexed
    @Schema(description = "所属项目ID")
    private Long projectId;

    @Field(name = "name")
    @Schema(description = "归档夹名称", example = "合同")
    private String name;

    @Field(name = "kind")
    @Schema(description = "归档类型，与 FileContextType 对应：CONTRACT/SURVEY_REPORT/PLANNING_REVIEW/PROJECT_PARTY_SURVEY_SUMMARY/OTHER")
    private FileContextType kind;

    @Field(name = "is_default")
    @Schema(description = "是否系统默认归档夹：true=默认不可删，false=用户新建可删")
    private Boolean isDefault;

    @Field(name = "sort_order")
    @Schema(description = "同项目下排序，数值越小越靠前", example = "0")
    private Integer sortOrder;
}
