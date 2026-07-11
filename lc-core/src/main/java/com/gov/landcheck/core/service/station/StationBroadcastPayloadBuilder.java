package com.gov.landcheck.core.service.station;

import org.springframework.util.StringUtils;

import com.gov.landcheck.core.bo.dto.NotificationPushPayload;
import com.gov.landcheck.core.config.mq.dto.FileParseResultMessage;
import com.gov.landcheck.core.enums.NotificationSceneEnum;

/**
 * 将 MQ 业务消息转换为站内广播载荷。
 */
public final class StationBroadcastPayloadBuilder {

    private StationBroadcastPayloadBuilder() {
    }

    public static NotificationPushPayload fromFileParseResult(FileParseResultMessage message) {
        NotificationSceneEnum sceneEnum = "success".equalsIgnoreCase(message.getStatus())
                ? NotificationSceneEnum.PARSE_SUCCESS
                : NotificationSceneEnum.PARSE_FAILED;
        return NotificationPushPayload.builder()
                .scene(sceneEnum.getCode())
                .title(sceneEnum.getTitle())
                .content(buildParseContent(message, sceneEnum))
                .businessId(message.getTaskId())
                .projectId(message.getProjectId())
                .fileId(message.getFileId())
                .timestamp(message.getCompletedAt() != null ? message.getCompletedAt() : System.currentTimeMillis())
                .build();
    }

    private static String buildParseContent(FileParseResultMessage message, NotificationSceneEnum sceneEnum) {
        String projectLabel = displayName(message.getProjectName(), message.getProjectId());
        String fileLabel = displayName(message.getFileName(), message.getFileId());
        if (sceneEnum == NotificationSceneEnum.PARSE_SUCCESS) {
            return String.format("任务 %s 解析成功。文件：%s，项目：%s。",
                    message.getTaskId(), fileLabel, projectLabel);
        }
        String errorMessage = StringUtils.hasText(message.getErrorMessage()) ? message.getErrorMessage() : "未知";
        return String.format("任务 %s 解析失败。文件：%s，项目：%s。错误：%s",
                message.getTaskId(), fileLabel, projectLabel, errorMessage);
    }

    private static String displayName(String name, Object fallbackId) {
        if (StringUtils.hasText(name)) {
            return name;
        }
        return fallbackId == null ? "" : String.valueOf(fallbackId);
    }
}
