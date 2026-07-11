package com.gov.landcheck.file.dto;

import org.springframework.web.multipart.MultipartFile;

import com.gov.landcheck.core.enums.FileContextType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单文件上传请求
 */
@Data
@Schema(description = "单文件上传请求")
public class FileUploadDTO {

    @NotNull(message = "项目ID不能为空")
    @Schema(description = "项目ID")
    private Long projectId;

    @NotNull(message = "文件内容类型不能为空")
    @Schema(description = "文件内容类型")
    private FileContextType fileContextType;

    @NotNull(message = "文件不能为空")
    @Schema(description = "上传文件")
    private MultipartFile file;

    @Schema(description = "期数（实测文件时必填）")
    private Integer phase;

    @Schema(description = "指定归档夹ID（可选；仅 fileContextType=OTHER 时生效）")
    private Long archiveId;

    @Schema(description = "当前操作者ID（服务端注入）")
    private Long operatorId;

    @Schema(description = "当前操作者名称（服务端注入）")
    private String operatorName;
}
