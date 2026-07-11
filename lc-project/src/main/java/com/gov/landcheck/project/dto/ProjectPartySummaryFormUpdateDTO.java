package com.gov.landcheck.project.dto;

import com.gov.landcheck.core.bo.entity.ProjectPartyDeclaredTotals;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 项目方实测汇总主表更新 DTO（动态字段，非空则更新）
 */
@Data
@Schema(description = "项目方实测汇总主表更新请求")
public class ProjectPartySummaryFormUpdateDTO {

    @NotNull(message = "主表ID不能为空")
    @Schema(description = "主表ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "是否已解析回填（0否 1是）")
    private Integer isParsed;

    @Schema(description = "项目方声明底部三行汇总")
    private ProjectPartyDeclaredTotals declaredTotals;

    @Schema(description = "解析状态：SUCCESS/PARTIAL/FAILED")
    private String parseStatus;

    @Schema(description = "备注")
    private String remark;
}
