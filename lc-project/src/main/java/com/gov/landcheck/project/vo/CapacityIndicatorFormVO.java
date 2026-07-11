package com.gov.landcheck.project.vo;

import com.gov.landcheck.core.bo.entity.CapacityIndicatorInfo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 容量指标核查表查询 VO：在实体基础上附带关联文件名（不落库）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "容量指标核查表（含关联文件名）")
public class CapacityIndicatorFormVO extends CapacityIndicatorInfo {

    @Schema(description = "关联文件的原始文件名（来自 FileRecord.originalName）")
    private String fileOriginalName;
}
