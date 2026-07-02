package com.gov.landcheck.core.bo.entity;

import java.math.BigDecimal;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 合同文件实体类
 * 用于存储合同类型文件的特定信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "contract_info")
@Schema(description = "合同信息")
public class ContractInfo extends MongoIdEntity {


    @Field(name = "project_id")
    @Schema(description = "关联 project.id")
    private Long projectId;

    @Field(name = "file_record_id")
    @Schema(description = "关联 file_record.id")
    private Long fileRecordId;

    @Field(name = "contract_number")
    @Schema(description = "合同编号", example = "HT2025001")
    private String contractNumber;

    @Field(name = "transferor")
    @Schema(description = "出让方（土地管理部门）", example = "XX市自然资源局")
    private String transferor;

    @Field(name = "transferee")
    @Schema(description = "受让方（开发商）", example = "XX房地产开发有限公司")
    private String transferee;

    @Field(name = "total_area")
    @Schema(description = "合同约定总面积（从地块自动汇总）", example = "50000.0000")
    private BigDecimal totalArea;

    @Field(name = "residential_area")
    @Schema(description = "合同约定住宅面积（从地块自动汇总）", example = "30000.0000")
    private BigDecimal residentialArea;

    @Field(name = "commercial_area")
    @Schema(description = "合同约定商业面积（从地块自动汇总）", example = "20000.0000")
    private BigDecimal commercialArea;

    @Field(name = "remark")
    @Schema(description = "备注")
    private String remark;
}

