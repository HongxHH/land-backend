package com.gov.landcheck.core.bo.entity;

import java.math.BigDecimal;

import com.gov.landcheck.core.enums.FloorAreaTypeEnum;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "room_info")
@Schema(description = "户室面积对照表")
public class RoomInfo extends MongoIdEntity {

    @Field(name = "project_id")
    @Schema(description = "所属项目ID")
    private Long projectId;

    @Field(name = "file_record_id")
    @Schema(description = "关联的文件id")
    private Long fileRecordId;

    @Field(name = "survey_report_info_id")
    @Schema(description = "关联 survey_report_info.id")
    private Long surveyReportInfoId;

    @Field(name = "room_level")
    @Schema(description = "层次", example = "1")
    private String roomLevel;

    @Field(name = "room_number")
    @Schema(description = "户室号", example = "101")
    private String roomNumber;

    @Field(name = "building_area")
    @Schema(description = "建筑面积", example = "100.00")
    private BigDecimal buildingArea;

    @Field(name = "inner_area")
    @Schema(description = "套内面积", example = "90.00")
    private BigDecimal innerArea;
    
    @Field(name = "balcony_area")
    @Schema(description = "阳台面积", example = "10.00")
    private BigDecimal balconyArea;
    
    @Field(name = "shared_area")
    @Schema(description = "分摊面积", example = "10.00")
    private BigDecimal sharedArea;
    
    @Field(name = "room_structure")
    @Schema(description = "户室结构", example = "1室1厅1卫")
    private String roomStructure;
    
    @Field(name = "room_usage")
    @Schema(description = "用途", example = "住宅")
    private String roomUsage;
    
    @Field(name = "remark")
    @Schema(description = "备注", example = "备注")
    private String remark;

    @Field(name = "is_calculate")
    @Schema(description = "是否参与计算（0否 1是）", example = "1")
    private Integer isCalculate;

    // ==================== 用途分类字段 ====================

    @Field(name = "usage_category")
    @Schema(description = "用途类别 ：RESIDENTIAL/COMMERCIAL/MANAGEMENT/OTHER_BUILDABLE/COMMUNITY/OTHER_PUBLIC/UNKNOWN", example = "RESIDENTIAL")
    private String usageCategory;

    @Field(name = "floor_area_type")
    @Schema(description = "面积类型：BUILDABLE(计容)/NON_BUILDABLE(不计容)/UNKNOWN(未知)", example = "BUILDABLE")
    private FloorAreaTypeEnum floorAreaType;

}
