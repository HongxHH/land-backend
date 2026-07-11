package com.gov.landcheck.file.dto;

import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.bo.dto.BaseQueryResultDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 解析任务查询结果DTO
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "解析任务查询分页结果")
public class ParseJobQueryResultDTO extends BaseQueryResultDTO<ParseJob> {

    @Schema(description = "解析任务列表")
    private List<ParseJob> records;

}