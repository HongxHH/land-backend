package com.gov.landcheck.file.dto;

import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.core.bo.dto.BaseQueryResultDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 解析数据明细查询结果DTO
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "解析数据明细查询分页结果")
public class ParsedDataItemQueryResultDTO extends BaseQueryResultDTO<ParsedDataItem> {

    @Schema(description = "解析数据明细列表")
    private List<ParsedDataItem> records;

}