package com.gov.landcheck.core.bo.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 规划复核表（文件级主表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "planning_review_form")
@Schema(description = "规划复核表主表")
public class PlanningReviewForm extends MongoIdEntity {

    @Field(name = "project_id")
    @Schema(description = "所属项目ID")
    private Long projectId;

    @Field(name = "file_record_id")
    @Schema(description = "关联 file_record.id")
    private Long fileRecordId;

    @Field(name = "is_parsed")
    @Schema(description = "是否已完成解析回填（0否 1是）")
    private Integer isParsed;

    @Field(name = "project_name")
    @Schema(description = "项目名称")
    private String projectName;

    @Field(name = "construction_unit")
    @Schema(description = "建设单位")
    private String constructionUnit;

    @Field(name = "design_unit")
    @Schema(description = "设计单位")
    private String designUnit;

    @Field(name = "construction_location")
    @Schema(description = "建设地点")
    private String constructionLocation;

    @Field(name = "land_use_nature")
    @Schema(description = "用地性质")
    private String landUseNature;

    @Field(name = "contact_person")
    @Schema(description = "联系人")
    private String contactPerson;

    @Field(name = "contact_phone")
    @Schema(description = "联系电话")
    private String contactPhone;

    @Field(name = "remarks")
    @Schema(description = "备注")
    private String remarks;
}
