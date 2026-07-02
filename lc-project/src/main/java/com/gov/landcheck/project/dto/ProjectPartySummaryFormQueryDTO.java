package com.gov.landcheck.project.dto;

import com.gov.landcheck.core.bo.dto.BaseQueryDTO;
import com.gov.landcheck.core.config.query.QueryField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目方实测汇总主表通用查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "项目方实测汇总主表通用查询请求")
public class ProjectPartySummaryFormQueryDTO extends BaseQueryDTO {

    @QueryField("_id")
    @Schema(description = "项目方实测汇总主表ID")
    private Long projectPartySummaryFormId;

    @QueryField("project_id")
    @Schema(description = "项目ID")
    private Long projectId;

    @QueryField("file_record_id")
    @Schema(description = "文件记录ID")
    private Long fileRecordId;

    @QueryField("is_parsed")
    @Schema(description = "是否已解析回填（0否 1是）")
    private Integer isParsed;

    @QueryField("parse_status")
    @Schema(description = "解析状态：SUCCESS/PARTIAL/FAILED")
    private String parseStatus;
}
