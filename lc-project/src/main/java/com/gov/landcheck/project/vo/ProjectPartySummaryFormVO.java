package com.gov.landcheck.project.vo;

import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目方实测汇总主表查询 VO：在实体基础上附带关联文件名（不落库）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "项目方实测汇总主表（含关联文件名）")
public class ProjectPartySummaryFormVO extends ProjectPartySurveySummaryForm {

    @Schema(description = "关联文件的原始文件名（来自 FileRecord.originalName）")
    private String fileOriginalName;
}
