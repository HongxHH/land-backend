package com.gov.landcheck.core.bo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "线程池动态调参请求")
public class ThreadPoolResizeDTO {

    @NotNull
    @Min(1)
    @Schema(description = "核心线程数", requiredMode = Schema.RequiredMode.REQUIRED, example = "6")
    private Integer corePoolSize;

    @NotNull
    @Min(1)
    @Schema(description = "最大线程数", requiredMode = Schema.RequiredMode.REQUIRED, example = "8")
    private Integer maximumPoolSize;
}
