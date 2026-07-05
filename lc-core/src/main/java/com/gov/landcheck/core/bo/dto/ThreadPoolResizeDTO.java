package com.gov.landcheck.core.bo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "解析并行度调参请求（线程池 core/max 与 parse-pipeline gate 联动）")
public class ThreadPoolResizeDTO {

    @NotNull
    @Min(1)
    @Schema(description = "并行解析路数 N（同时等于线程池 core/max 与管道 gate 许可数）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Integer parseConcurrency;
}
