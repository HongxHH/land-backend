package com.gov.landcheck.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "建筑/商业/住宅三行面积对比")
public class AreaTripleLinesVO {

    @Schema(description = "建筑面积行")
    private AreaLineVO totalBuilding;

    @Schema(description = "商业面积行")
    private AreaLineVO commercial;

    @Schema(description = "住宅面积行")
    private AreaLineVO residential;
}
