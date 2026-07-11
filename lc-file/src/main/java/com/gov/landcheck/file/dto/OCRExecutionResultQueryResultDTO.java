package com.gov.landcheck.file.dto;

import com.gov.landcheck.core.bo.dto.BaseQueryResultDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * OCR执行结果查询结果DTO
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "OCR执行结果查询分页结果")
public class OCRExecutionResultQueryResultDTO extends BaseQueryResultDTO<OCRExecutionResultResponseDTO> {

    @Schema(description = "OCR执行结果列表")
    private List<OCRExecutionResultResponseDTO> records;

}