package com.gov.landcheck.project.dto;

import com.gov.landcheck.core.bo.dto.BaseQueryDTO;
import com.gov.landcheck.core.config.query.QueryField;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 容量指标核查表通用查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "容量指标核查表通用查询请求")
public class CapacityIndicatorFormQueryDTO extends BaseQueryDTO {

    @QueryField("_id")
    @Schema(description = "容量指标核查表ID")
    private Long capacityIndicatorFormId;

    @QueryField("project_id")
    @Schema(description = "项目ID")
    private Long projectId;

    @QueryField("file_record_id")
    @Schema(description = "文件记录ID")
    private Long fileRecordId;

    @QueryField("is_parsed")
    @Schema(description = "是否已解析回填（0否 1是）")
    private Integer isParsed;
}
