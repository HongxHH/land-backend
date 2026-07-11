package com.gov.landcheck.file.dto;

import java.util.List;

import com.gov.landcheck.core.bo.dto.BaseQueryResultDTO;
import com.gov.landcheck.file.vo.FileRecordVO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件查询结果DTO（每条为 FileRecordVO，实测报告类型含校验状态）
 *
 * @author system
 * @date 2026/01/24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "文件查询分页结果")
public class FileQueryResultDTO extends BaseQueryResultDTO<FileRecordVO> {

    @Schema(description = "文件列表")
    private List<FileRecordVO> records;

}