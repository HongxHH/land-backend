package com.gov.landcheck.project.dto;

import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.bo.dto.BaseQueryResultDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 实测报告信息查询结果DTO
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "实测报告信息查询分页结果")
public class SurveyReportInfoQueryResultDTO extends BaseQueryResultDTO<SurveyReportInfo> {

    @Schema(description = "实测报告信息列表")
    private List<SurveyReportInfo> records;

}