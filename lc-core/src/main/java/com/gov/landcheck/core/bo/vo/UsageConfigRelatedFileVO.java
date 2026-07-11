package com.gov.landcheck.core.bo.vo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 已知用途配置关联文件展示 VO
 */
@Data
@Schema(description = "用途配置关联文件")
public class UsageConfigRelatedFileVO {

    @Schema(description = "文件记录ID")
    private Long fileRecordId;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "命中该配置的户室条数")
    private Integer matchedRoomCount;

    @Schema(description = "样例用途文本（最多3条）")
    private List<String> sampleRoomUsages;

    @Schema(description = "上传时间")
    private LocalDateTime uploadTime;

    @Schema(description = "文件内容类型")
    private String fileContextType;
}
