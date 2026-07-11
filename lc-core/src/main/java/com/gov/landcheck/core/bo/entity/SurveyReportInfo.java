package com.gov.landcheck.core.bo.entity;

import java.math.BigDecimal;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 实测报告信息实体类
 *
 * @author system
 * @date 2025/12/19
 */

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "survey_report_info")
@Schema(description = "实测报告信息")
public class SurveyReportInfo extends MongoIdEntity {

    @Field(name = "project_id")
    @Schema(description = "所属项目ID")
    private Long projectId;

    @Field(name = "file_record_id")
    @Schema(description = "关联 file_record.id")
    private Long fileRecordId;

    @Field(name = "building_name")
    @Schema(description = "建筑名称", example = "1号楼")
    private String buildingName;

    @Field(name = "phase")
    @Schema(description = "期数", example = "1")
    private Integer phase;

    @Field(name = "property_certificate_number")
    @Schema(description = "不动产权证编号", example = "1234567890")
    private String propertyCertificateNumber;

    @Field(name = "real_estate_survey_report_number")
    @Schema(description = "房地产勘测报告书编号", example = "1234567890")
    private String realEstateSurveyReportNumber;

    @Field(name = "property_area_confirmation_notice_number")
    @Schema(description = "房产面积确认告知书编号", example = "1234567890")
    private String propertyAreaConfirmationNoticeNumber;

    @Field(name = "contract_approval_number")
    @Schema(description = "合同/批文编号（项目主合同合同编号的快照，与主合同同步）", example = "高新2021003号")
    private String contractApprovalNumber;

    // 实测报告总建筑面积
    @Field(name = "actual_total_building_area")
    @Schema(description = "实测报告总建筑面积（㎡）", example = "10000.0000")
    private BigDecimal actualTotalBuildingArea;

    @Field(name = "actual_residential_area")
    @Schema(description = "实测报告住宅面积（㎡）", example = "10000.0000")
    private BigDecimal actualResidentialArea;

    @Field(name = "actual_commercial_area")
    @Schema(description = "实测报告商业面积（㎡）", example = "10000.0000")
    private BigDecimal actualCommercialArea;

    @Field(name = "actual_management_area")
    @Schema(description = "实测报告物管用房面积（㎡）", example = "10000.0000")
    private BigDecimal actualManagementRoomArea;

    @Field(name = "actual_other_buildable_area")
    @Schema(description = "实测报告其他计容面积（㎡）", example = "10000.0000")
    private BigDecimal actualOtherBuildableArea;

    @Field(name = "actual_community_area")
    @Schema(description = "实测报告社区用房面积（㎡）", example = "10000.0000")
    private BigDecimal actualCommunityArea;

    @Field(name = "actual_other_public_area")
    @Schema(description = "实测报告其他公用面积（㎡）", example = "10000.0000")
    private BigDecimal actualOtherPublicArea;

    // ==================== 计容/不计容面积字段 ====================

    @Field(name = "total_buildable_area")
    @Schema(description = "计容面积（住宅+商业+物管用房+其他计容）（㎡）", example = "10000.0000")
    private BigDecimal totalBuildableArea;

    @Field(name = "total_non_buildable_area")
    @Schema(description = "不计容面积（社区用房+其他公用）（㎡）", example = "10000.0000")
    private BigDecimal totalNonBuildableArea;

    @Field(name = "pending_confirm_area")
    @Schema(description = "待确认面积（未知用途 + 用途缺失面积总和）（㎡）", example = "10000.0000")
    private BigDecimal pendingConfirmArea;

    @Field(name = "has_unknown_usage")
    @Schema(description = "是否存在待确认用途（未知用途或用途缺失，0否 1是）", example = "0")
    private Integer hasUnknownUsage;

    @Field(name = "unknown_usages")
    @Schema(description = "待确认用途列表（JSON格式，房间号->用途或（用途缺失））", example = "{\"101\":\"科技馆\"}")
    private String unknownUsages;

    @Field(name = "unknown_usage_count")
    @Schema(description = "待确认用途的户室数量（含用途缺失）", example = "5")
    private Integer unknownUsageCount;

    // ==================== 累加字段 ====================
    @Field(name = "room_info_building_area_sum")
    @Schema(description = "户室面积对照表的建筑面积总和（㎡）,来自累加", example = "10000.0000")
    private BigDecimal roomInfoBuildingAreaSum;

    @Field(name = "room_info_inner_area_sum")
    @Schema(description = "户室面积对照表的套内面积总和（㎡）", example = "10000.0000")
    private BigDecimal roomInfoInnerAreaSum;

    @Field(name = "room_info_balcony_area_sum")
    @Schema(description = "户室面积对照表的阳台面积总和（㎡）", example = "10000.0000")
    private BigDecimal roomInfoBalconyAreaSum;

    @Field(name = "room_info_shared_area_sum")
    @Schema(description = "户室面积对照表的分摊面积总和（㎡）", example = "10000.0000")
    private BigDecimal roomInfoSharedAreaSum;

    @Field(name = "room_info_building_area_sum_from_ocr")
    @Schema(description = "户室面积对照表的建筑面积总和（㎡）,来自ocr识别", example = "10000.0000")
    private BigDecimal roomInfoBuildingAreaSumFromOcr;

    @Field(name = "room_info_inner_area_sum_from_ocr")
    @Schema(description = "户室面积对照表的套内面积总和（㎡）,来自ocr识别", example = "10000.0000")
    private BigDecimal roomInfoInnerAreaSumFromOcr;

    @Field(name = "room_info_balcony_area_sum_from_ocr")
    @Schema(description = "户室面积对照表的阳台面积总和（㎡）,来自ocr识别", example = "10000.0000")
    private BigDecimal roomInfoBalconyAreaSumFromOcr;

    @Field(name = "room_info_shared_area_sum_from_ocr")
    @Schema(description = "户室面积对照表的分摊面积总和（㎡）,来自ocr识别", example = "10000.0000")
    private BigDecimal roomInfoSharedAreaSumFromOcr;

    @Field(name = "is_verified")
    @Schema(description = "是否通过校验（0否 1是）", example = "0")
    private Integer isVerified;

    // 校验出错原因
    @Field(name = "verification_error_reason")
    @Schema(description = "校验出错原因", example = "建筑面积总和不一致")
    private String verificationErrorReason;

    // 默认为0
    @Field(name = "is_parsed")
    @Schema(description = "是否已经解析（0否 1是）", example = "0")
    private Integer isParsed;

    @Field(name = "remark")
    @Schema(description = "备注")
    private String remark;

}
