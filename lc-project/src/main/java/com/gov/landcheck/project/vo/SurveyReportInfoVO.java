package com.gov.landcheck.project.vo;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 实测报告信息视图对象
 * 用于向前端返回实测报告查询结果
 *
 * @author system
 * @date 2026/01/22
 */
@Data
@Schema(description = "实测报告信息视图对象")
public class SurveyReportInfoVO {

    @Schema(description = "实测报告ID", example = "1")
    private Long id;

    @Schema(description = "关联的文件ID", example = "1")
    private Long fileRecordId;

    @Schema(description = "建筑名称", example = "1号楼")
    private String buildingName;

    @Schema(description = "期数", example = "1")
    private Integer phase;

    @Schema(description = "不动产权证编号", example = "1234567890")
    private String propertyCertificateNumber;

    @Schema(description = "房地产勘测报告书编号", example = "1234567890")
    private String realEstateSurveyReportNumber;

    @Schema(description = "房产面积确认告知书编号", example = "1234567890")
    private String propertyAreaConfirmationNoticeNumber;

    @Schema(description = "合同/批文编号（来自项目主合同合同编号快照）", example = "高新2021003号")
    private String contractApprovalNumber;

    @Schema(description = "实测报告总建筑面积（㎡）", example = "10000.0000")
    private BigDecimal actualTotalBuildingArea;

    @Schema(description = "实测报告住宅面积（㎡）", example = "8000.0000")
    private BigDecimal actualResidentialArea;

    @Schema(description = "实测报告商业面积（㎡）", example = "2000.0000")
    private BigDecimal actualCommercialArea;

    @Schema(description = "实测报告物管用房面积（㎡）", example = "500.0000")
    private BigDecimal actualManagementRoomArea;

    @Schema(description = "实测报告其他计容面积（㎡）", example = "200.0000")
    private BigDecimal actualOtherBuildableArea;

    @Schema(description = "实测报告社区用房面积（㎡）", example = "300.0000")
    private BigDecimal actualCommunityArea;

    @Schema(description = "实测报告其他公用面积（㎡）", example = "100.0000")
    private BigDecimal actualOtherPublicArea;

    @Schema(description = "计容面积（住宅+商业+物管用房+其他计容）（㎡）", example = "10700.0000")
    private BigDecimal totalBuildableArea;

    @Schema(description = "不计容面积（社区用房+其他公用）（㎡）", example = "400.0000")
    private BigDecimal totalNonBuildableArea;

    @Schema(description = "待确认面积（未知用途面积总和）（㎡）", example = "0.0000")
    private BigDecimal pendingConfirmArea;

    @Schema(description = "是否存在未知用途（0否 1是）", example = "0")
    private Integer hasUnknownUsage;

    @Schema(description = "未知用途列表（JSON格式）", example = "[\"科技馆\", \"展览厅\"]")
    private String unknownUsages;

    @Schema(description = "未知用途的户室数量", example = "5")
    private Integer unknownUsageCount;

    @Schema(description = "户室面积对照表的建筑面积总和（㎡）,来自累加", example = "10000.0000")
    private BigDecimal roomInfoBuildingAreaSum;

    @Schema(description = "户室面积对照表的套内面积总和（㎡）", example = "8000.0000")
    private BigDecimal roomInfoInnerAreaSum;

    @Schema(description = "户室面积对照表的阳台面积总和（㎡）", example = "1000.0000")
    private BigDecimal roomInfoBalconyAreaSum;

    @Schema(description = "户室面积对照表的分摊面积总和（㎡）", example = "2000.0000")
    private BigDecimal roomInfoSharedAreaSum;

    @Schema(description = "户室面积对照表的建筑面积总和（㎡）,来自OCR识别", example = "10000.0000")
    private BigDecimal roomInfoBuildingAreaSumFromOcr;

    @Schema(description = "户室面积对照表的套内面积总和（㎡）,来自OCR识别", example = "8000.0000")
    private BigDecimal roomInfoInnerAreaSumFromOcr;

    @Schema(description = "户室面积对照表的阳台面积总和（㎡）,来自OCR识别", example = "1000.0000")
    private BigDecimal roomInfoBalconyAreaSumFromOcr;

    @Schema(description = "户室面积对照表的分摊面积总和（㎡）,来自OCR识别", example = "2000.0000")
    private BigDecimal roomInfoSharedAreaSumFromOcr;

    @Schema(description = "是否通过校验（0否 1是）", example = "1")
    private Integer isVerified;

    @Schema(description = "校验出错原因", example = "建筑面积总和不一致")
    private String verificationErrorReason;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "关联文件的原始文件名（来自 FileRecord.originalName）")
    private String fileOriginalName;
}