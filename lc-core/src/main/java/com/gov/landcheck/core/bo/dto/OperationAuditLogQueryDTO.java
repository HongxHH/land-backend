package com.gov.landcheck.core.bo.dto;

import java.time.LocalDateTime;

import com.gov.landcheck.core.config.query.QueryField;
import com.gov.landcheck.core.config.query.QueryType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作审计日志通用查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "操作审计日志通用查询请求")
public class OperationAuditLogQueryDTO extends BaseQueryDTO {

    @Schema(description = "排序字段", example = "operateTime")
    private String sortField = "operateTime";

    @QueryField("operator_id")
    @Schema(description = "操作人ID")
    private Long operatorId;

    @QueryField(value = "operator_name", type = QueryType.REGEX)
    @Schema(description = "操作人姓名（模糊匹配）")
    private String operatorName;

    @QueryField("operation")
    @Schema(description = "操作类型：CREATE/UPDATE/DELETE/UPLOAD/MOVE")
    private String operation;

    @QueryField("target_type")
    @Schema(description = "目标类型：project/contract/land_parcel/room_info/survey_report/file/file_archive/usage_config")
    private String targetType;

    @QueryField("project_id")
    @Schema(description = "关联项目ID")
    private Long projectId;

    @QueryField("contract_id")
    @Schema(description = "关联合同ID")
    private Long contractId;

    @QueryField("target_id")
    @Schema(description = "目标实体主键")
    private String targetId;

    @Schema(description = "操作时间起始（含）")
    private LocalDateTime operateTimeStart;

    @Schema(description = "操作时间截止（含）")
    private LocalDateTime operateTimeEnd;
}
