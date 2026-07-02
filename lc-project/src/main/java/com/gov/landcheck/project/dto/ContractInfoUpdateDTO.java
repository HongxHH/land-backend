package com.gov.landcheck.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 合同信息通用更新DTO
 *
 * @author system
 * @date 2026/02/05
 */
@Data
@Schema(description = "合同信息通用更新请求")
public class ContractInfoUpdateDTO {
    @NotNull(message = "合同信息ID不能为空")
    @Schema(description = "合同信息ID（必填）", required = true, example = "1")
    private Long id;

    @Schema(description = "合同编号", example = "HT2025001")
    private String contractNumber;

    @Schema(description = "出让方（土地管理部门）", example = "XX市自然资源局")
    private String transferor;

    @Schema(description = "受让方（开发商）", example = "XX房地产开发有限公司")
    private String transferee;

    @Schema(description = "备注")
    private String remark;

}