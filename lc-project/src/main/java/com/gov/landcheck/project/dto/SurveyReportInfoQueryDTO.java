package com.gov.landcheck.project.dto;

import com.gov.landcheck.core.bo.dto.BaseQueryDTO;
import com.gov.landcheck.core.config.query.QueryField;
import com.gov.landcheck.core.config.query.QueryType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 实测报告信息通用查询DTO
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "实测报告信息通用查询请求")
public class SurveyReportInfoQueryDTO extends BaseQueryDTO {

    @QueryField("_id")
    @Schema(description = "实测报告ID")
    private Long surveyReportInfoId;

    @QueryField("project_id")
    @Schema(description = "项目ID")
    private Long projectId;

    @QueryField("file_record_id")
    @Schema(description = "文件记录ID")
    private Long fileRecordId;

    @QueryField(value = "building_name", type = QueryType.REGEX)
    @Schema(description = "建筑名称（支持模糊查询）")
    private String buildingName;

    @QueryField(value = "property_certificate_number", type = QueryType.REGEX)
    @Schema(description = "不动产权证编号（支持模糊查询）")
    private String propertyCertificateNumber;

    @QueryField(value = "real_estate_survey_report_number", type = QueryType.REGEX)
    @Schema(description = "房地产勘测报告书编号（支持模糊查询）")
    private String realEstateSurveyReportNumber;

    @QueryField(value = "property_area_confirmation_notice_number", type = QueryType.REGEX)
    @Schema(description = "房产面积确认告知书编号（支持模糊查询）")
    private String propertyAreaConfirmationNoticeNumber;

    @QueryField("is_verified")
    @Schema(description = "是否通过校验（0否 1是）")
    private Integer isVerified;

    @QueryField("is_parsed")
    @Schema(description = "是否已经解析（0否 1是）")
    private Integer isParsed;

}