package com.gov.landcheck.core.config.mq.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件解析结果消息体，用于 MQ Topic lc-file-parse-result。
 * 供生产者（TaskThreadPool 回调）与消费者（通知监听器）共用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件解析结果消息体")
public class FileParseResultMessage {

    @Schema(description = "任务ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String taskId;

    @Schema(description = "文件ID")
    private Long fileId;

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "文件原始名称")
    private String fileName;

    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "状态：success / failed", requiredMode = Schema.RequiredMode.REQUIRED, example = "success")
    private String status;

    @Schema(description = "失败时的错误信息")
    private String errorMessage;

    @Schema(description = "完成时间戳（毫秒）")
    private Long completedAt;

    public static FileParseResultMessage success(String taskId, Long fileId, Long projectId,
            String fileName, String projectName) {
        return FileParseResultMessage.builder()
                .taskId(taskId)
                .fileId(fileId)
                .projectId(projectId)
                .fileName(fileName)
                .projectName(projectName)
                .status("success")
                .completedAt(System.currentTimeMillis())
                .build();
    }

    public static FileParseResultMessage failed(String taskId, Long fileId, Long projectId,
            String fileName, String projectName, String errorMessage) {
        return FileParseResultMessage.builder()
                .taskId(taskId)
                .fileId(fileId)
                .projectId(projectId)
                .fileName(fileName)
                .projectName(projectName)
                .status("failed")
                .errorMessage(errorMessage)
                .completedAt(System.currentTimeMillis())
                .build();
    }

    public static FileParseResultMessage success(String taskId, Long fileId, Long projectId) {
        return success(taskId, fileId, projectId, null, null);
    }

    public static FileParseResultMessage failed(String taskId, Long fileId, Long projectId, String errorMessage) {
        return failed(taskId, fileId, projectId, null, null, errorMessage);
    }
}
