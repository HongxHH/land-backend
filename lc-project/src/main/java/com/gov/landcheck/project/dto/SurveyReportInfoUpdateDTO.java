package com.gov.landcheck.project.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 实测报告信息通用更新DTO
 *
 * @author system
 * @date 2026/02/05
 */
@Data
@Schema(description = "实测报告信息通用更新请求")
public class SurveyReportInfoUpdateDTO {

    @NotNull(message = "实测报告ID不能为空")
    @Schema(description = "实测报告ID（必填）", example = "1")
    private Long id;

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

    @Schema(description = "是否通过校验（0否 1是）", example = "0")
    private Integer isVerified;

    @Schema(description = "校验出错原因", example = "建筑面积总和不一致")
    private String verificationErrorReason;


    @Schema(description = "备注")
    private String remark;

}