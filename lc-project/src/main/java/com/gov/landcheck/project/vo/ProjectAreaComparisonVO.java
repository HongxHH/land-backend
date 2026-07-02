package com.gov.landcheck.project.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "项目三组来源面积对比")
public class ProjectAreaComparisonVO {

    @Schema(description = "组1：系统计算口径")
    private AreaTripleLinesVO systemCalculated;

    @Schema(description = "组2：项目方声明口径")
    private AreaTripleLinesVO projectPartyDeclared;

    @Schema(description = "组3：规划复核口径")
    private AreaTripleLinesVO planningCalculated;

    @Schema(description = "组4：容量指标核查口径")
    private AreaTripleLinesVO capacityIndicatorCalculated;

    @Schema(description = "一致性提示列表")
    private List<String> consistencyFlags;

    @Schema(description = "数据完整性")
    private AreaComparisonCompletenessVO dataCompleteness;
}
