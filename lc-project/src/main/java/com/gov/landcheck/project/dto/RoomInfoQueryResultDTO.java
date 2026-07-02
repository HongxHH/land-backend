package com.gov.landcheck.project.dto;

import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.dto.BaseQueryResultDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 户室信息查询结果DTO
 *
 * @author system
 * @date 2026/01/26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "户室信息查询分页结果")
public class RoomInfoQueryResultDTO extends BaseQueryResultDTO<RoomInfo> {

    @Schema(description = "户室信息列表")
    private List<RoomInfo> records;

}