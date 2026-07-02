package com.gov.landcheck.core.bo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 通用查询DTO基类
 * 包含分页和排序的基础字段
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@Schema(description = "通用查询请求基类")
public class BaseQueryDTO {

    @Schema(description = "页码（从1开始）", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小", example = "20")
    private Integer pageSize = 20;

    @Schema(description = "排序字段", example = "createTime")
    private String sortField = "createTime";

    @Schema(description = "排序方向（asc/desc）", example = "desc")
    private String sortDirection = "desc";

}