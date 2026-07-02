package com.gov.landcheck.file.dto;

import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.dto.BaseQueryResultDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 解析数据头表查询结果DTO
 *
 * @author system
 * @date 2026/01/24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "解析数据头表查询分页结果")
public class ParsedDataHeaderQueryResultDTO extends BaseQueryResultDTO<ParsedDataHeader> {

    @Schema(description = "解析数据头列表")
    private List<ParsedDataHeader> records;

}