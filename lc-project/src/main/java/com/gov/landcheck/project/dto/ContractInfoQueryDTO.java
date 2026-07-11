package com.gov.landcheck.project.dto;

import com.gov.landcheck.core.bo.dto.BaseQueryDTO;
import com.gov.landcheck.core.config.query.QueryField;
import com.gov.landcheck.core.config.query.QueryType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 合同信息通用查询DTO
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "合同信息通用查询请求")
public class ContractInfoQueryDTO extends BaseQueryDTO {

    @QueryField("_id")
    @Schema(description = "合同ID")
    private Long contractId;

    @QueryField("project_id")
    @Schema(description = "项目ID")
    private Long projectId;

    @QueryField("file_record_id")
    @Schema(description = "文件记录ID")
    private Long fileRecordId;

    @QueryField(value = "contract_number", type = QueryType.REGEX)
    @Schema(description = "合同编号（支持模糊查询）")
    private String contractNumber;

    @QueryField(value = "transferor", type = QueryType.REGEX)
    @Schema(description = "出让方（支持模糊查询）")
    private String transferor;

    @QueryField(value = "transferee", type = QueryType.REGEX)
    @Schema(description = "受让方（支持模糊查询）")
    private String transferee;

    @QueryField(value = "planned_use", type = QueryType.REGEX)
    @Schema(description = "规划用途（支持模糊查询）")
    private String plannedUse;

}