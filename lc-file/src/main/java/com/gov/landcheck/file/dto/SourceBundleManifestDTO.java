package com.gov.landcheck.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 项目原件打包前的清单，只含数量和体积，不含文件内容。
 */
@Data
@Builder
@Schema(description = "项目原件打包清单")
public class SourceBundleManifestDTO {

    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "可打入压缩包的原件数量")
    private int fileCount;

    @Schema(description = "可打入压缩包的原件总字节数")
    private long totalBytes;

    @Schema(description = "存储中已缺失、不会打入原件的数量")
    private int missingCount;

    @Schema(description = "压缩包精确字节数；没有可下载原件时为 0")
    private long zipBytes;
}
