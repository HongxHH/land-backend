package com.gov.landcheck.core.bo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新归档夹请求 DTO
 * 仅允许修改归档夹名称和排序值，归档夹由路径参数 archiveId 指定。
 *
 * @author system
 * @date 2026/03/03
 */
@Data
@Schema(description = "更新归档夹请求（仅名称与排序可修改）")
public class UpdateArchiveDTO {

    @NotNull(message = "归档夹ID不能为空")
    @Schema(description = "归档夹ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long archiveId;

    @NotNull(message = "项目ID不能为空")
    @Schema(description = "项目ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectId;

    @Size(max = 64, message = "归档夹名称长度不能超过64")
    @Schema(description = "归档夹名称，不传则不修改")
    private String name;

    @Schema(description = "排序值，数值越小越靠前；不传则不修改")
    private Integer sortOrder;
}
