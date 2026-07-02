package com.gov.landcheck.core.bo.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 通用查询结果DTO基类
 * 包含分页结果的基础字段
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@Schema(description = "通用查询分页结果基类")
public class BaseQueryResultDTO<T> {

    @Schema(description = "数据列表")
    private List<T> records;

    @Schema(description = "当前页码")
    private Integer current;

    @Schema(description = "每页大小")
    private Integer size;

    @Schema(description = "总记录数")
    private Long total;

    @Schema(description = "总页数")
    private Integer pages;

}