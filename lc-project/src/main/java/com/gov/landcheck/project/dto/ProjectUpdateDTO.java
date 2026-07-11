package com.gov.landcheck.project.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 项目信息通用更新DTO
 *
 * @author system
 * @date 2026/02/05
 */
@Data
@Schema(description = "项目信息通用更新请求")
public class ProjectUpdateDTO {
    @NotNull(message = "项目ID不能为空")
    @Schema(description = "项目ID（必填）", example = "1")
    private Long id;

    @Schema(description = "项目名称", example = "XX住宅小区项目")
    private String projectName;

    @Schema(description = "项目时间（ISO yyyy-MM-dd 自然日）", example = "2025-11-15")
    private String projectTime;

}