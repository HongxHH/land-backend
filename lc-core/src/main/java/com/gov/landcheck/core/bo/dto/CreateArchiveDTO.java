package com.gov.landcheck.core.bo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建归档夹请求 DTO
 * 用户自定义归档夹（如「现场图片」「红线文件」），kind 固定为 OTHER。
 *
 * @author system
 * @date 2026/02/05
 */
@Data
@Schema(description = "新建归档夹请求")
public class CreateArchiveDTO {

    @NotNull(message = "项目ID不能为空")
    @Schema(description = "项目ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long projectId;

    @NotBlank(message = "归档夹名称不能为空")
    @Size(max = 64, message = "归档夹名称长度不能超过64")
    @Schema(description = "归档夹名称", example = "现场图片", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "排序值，数值越小越靠前；不传则追加到末尾")
    private Integer sortOrder;
}
