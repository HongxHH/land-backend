package com.gov.landcheck.core.bo.dto;

import com.gov.landcheck.core.bo.vo.UsageConfigRelatedFileVO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用途配置关联文件分页结果
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用途配置关联文件分页结果")
public class UsageConfigRelatedFileQueryResultDTO extends BaseQueryResultDTO<UsageConfigRelatedFileVO> {

    @Schema(description = "命中该配置的户室总条数（跨页）")
    private Long totalMatchedRooms;
}
