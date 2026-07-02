package com.gov.landcheck.core.bo.entity;

import java.math.BigDecimal;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;
import com.gov.landcheck.core.enums.PlanningReviewAreaCategory;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 规划复核表数据行
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "planning_review_row")
@CompoundIndexes({
        @CompoundIndex(name = "idx_file_record", def = "{'file_record_id': 1}"),
        @CompoundIndex(name = "idx_form", def = "{'planning_review_form_id': 1}")
})
@Schema(description = "规划复核表行")
public class PlanningReviewRow extends MongoIdEntity {

    @Field(name = "project_id")
    private Long projectId;

    @Field(name = "file_record_id")
    private Long fileRecordId;

    @Field(name = "planning_review_form_id")
    private Long planningReviewFormId;

    @Field(name = "row_index")
    @Schema(description = "表格序号")
    private Integer rowIndex;

    @Field(name = "engineering_project")
    @Schema(description = "工程项目（楼栋/建筑名称）")
    private String engineeringProject;

    @Field(name = "building_nature_raw")
    @Schema(description = "建筑性质（原文）")
    private String buildingNatureRaw;

    @Field(name = "area_category")
    @Schema(description = "归并后的面积类别")
    private PlanningReviewAreaCategory areaCategory;

    @Field(name = "construction_nature")
    @Schema(description = "建设性质")
    private String constructionNature;

    @Field(name = "building_count")
    @Schema(description = "栋数")
    private Integer buildingCount;

    @Field(name = "above_ground_floors")
    private Integer aboveGroundFloors;

    @Field(name = "below_ground_floors")
    private Integer belowGroundFloors;

    @Field(name = "height_m")
    @Schema(description = "高度（m）")
    private BigDecimal heightM;

    @Field(name = "base_area_m2")
    @Schema(description = "基底面积（㎡）")
    private BigDecimal baseAreaM2;

    @Field(name = "residential_residential_area")
    private BigDecimal residentialResidentialArea;

    @Field(name = "residential_hotel_apartment_area")
    private BigDecimal residentialHotelApartmentArea;

    @Field(name = "residential_other_area")
    private BigDecimal residentialOtherArea;

    /** 非居住-地上-商业 */
    @Field(name = "nr_above_commercial")
    private BigDecimal nrAboveCommercial;

    @Field(name = "nr_above_garage")
    private BigDecimal nrAboveGarage;

    @Field(name = "nr_above_other")
    private BigDecimal nrAboveOther;

    @Field(name = "nr_below_commercial")
    private BigDecimal nrBelowCommercial;

    @Field(name = "nr_below_supporting")
    private BigDecimal nrBelowSupporting;

    @Field(name = "nr_below_other")
    private BigDecimal nrBelowOther;

    @Field(name = "above_ground_area")
    private BigDecimal aboveGroundArea;

    @Field(name = "below_ground_area")
    private BigDecimal belowGroundArea;

    @Field(name = "total_area")
    private BigDecimal totalArea;

    @Field(name = "far_above_ground")
    @Schema(description = "计容面积-地上")
    private BigDecimal farAboveGround;

    @Field(name = "far_below_ground")
    @Schema(description = "计容面积-地下")
    private BigDecimal farBelowGround;
}
