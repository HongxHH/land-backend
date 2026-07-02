package com.gov.landcheck.core.bo.entity;

import java.math.BigDecimal;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 地块信息实体类
 * 用于存储合同中各个地块的详细信息
 *
 * @author system
 * @date 2026/02/05
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "land_parcel")
@Schema(description = "地块信息")
public class LandParcel extends MongoIdEntity {

    @Field(name = "contract_id")
    @Schema(description = "关联合同ID", required = true)
    private Long contractId;

    @Field(name = "parcel_code")
    @Schema(description = "地块编号", example = "A")
    private String parcelCode; // A, B, C, D, E 等

    @Field(name = "parcel_name")
    @Schema(description = "地块名称", example = "A地块")
    private String parcelName;

    @Field(name = "planned_use")
    @Schema(description = "规划用途", example = "住宅")
    private String plannedUse;

    @Field(name = "total_area")
    @Schema(description = "地块总面积（㎡）", example = "25000.0000")
    private BigDecimal totalArea;

    @Field(name = "residential_area")
    @Schema(description = "住宅面积（㎡）", example = "15000.0000")
    private BigDecimal residentialArea;

    @Field(name = "commercial_area")
    @Schema(description = "商业面积（㎡）", example = "10000.0000")
    private BigDecimal commercialArea;

    //容积率
    @Field(name = "floor_area_ratio")
    @Schema(description = "容积率", example = "2.0")
    private BigDecimal floorAreaRatio;

    //商住比
    @Field(name = "commercial_residential_ratio")
    @Schema(description = "商住比", example = "0.5")
    private BigDecimal commercialResidentialRatio;

    @Field(name = "remark")
    @Schema(description = "备注")
    private String remark;
}