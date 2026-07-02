package com.gov.landcheck.file.dto;

import com.gov.landcheck.core.bo.dto.BaseQueryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 解析数据明细查询DTO
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "解析数据明细查询请求")
public class ParsedDataItemQueryDTO extends BaseQueryDTO {

    @Schema(description = "关联解析数据头表ID")
    private Long headerId;

    @Schema(description = "数据类别（支持模糊查询）")
    private String dataCategory;

    @Schema(description = "原始字段名（支持模糊查询）")
    private String fieldKey;

    @Schema(description = "字段值（支持模糊查询）")
    private String fieldValue;

    @Schema(description = "标准化后字段名（支持模糊查询）")
    private String normalizedKey;

    @Schema(description = "标准化后字符串值（支持模糊查询）")
    private String normalizedValue;

    @Schema(description = "字段类型（NUMBER/STRING/DATE/BOOLEAN/JSON）")
    private String fieldType;

    @Schema(description = "提取方法（OCR/LLM/MANUAL）")
    private String extractionMethod;

    @Schema(description = "数据来源（OCR/LLM/MANUAL）")
    private String dataSource;

    @Schema(description = "验证状态（PENDING/VALID/INVALID）")
    private String validationStatus;

    @Schema(description = "是否手动修改过（0否 1是）")
    private Integer isModified;

}