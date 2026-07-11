package com.gov.landcheck.project.dto;

import com.gov.landcheck.core.bo.entity.ContractInfo;
import com.gov.landcheck.core.bo.dto.BaseQueryResultDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 合同信息查询结果DTO
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "合同信息查询分页结果")
public class ContractInfoQueryResultDTO extends BaseQueryResultDTO<ContractInfo> {

    @Schema(description = "合同信息列表")
    private List<ContractInfo> records;

}