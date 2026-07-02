package com.gov.landcheck.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "三组来源数据完整性")
public class AreaComparisonCompletenessVO {

    @Schema(description = "系统计算口径是否可用")
    private Boolean systemCalculatedAvailable;

    @Schema(description = "项目方声明口径是否可用")
    private Boolean projectPartyDeclaredAvailable;

    @Schema(description = "规划口径是否可用")
    private Boolean planningCalculatedAvailable;

    @Schema(description = "容量指标核查口径是否可用")
    private Boolean capacityIndicatorCalculatedAvailable;
}
