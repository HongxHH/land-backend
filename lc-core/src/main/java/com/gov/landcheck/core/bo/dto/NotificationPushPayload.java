package com.gov.landcheck.core.bo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 推送到前端的通知载荷（WebSocket 或轮询接口返回）。
 *
 * @author landcheck
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPushPayload {

    @Schema(description = "场景编码，取值见 NotificationSceneEnum")
    private String scene;
    private String title;
    private String content;
    private String businessId;
    private Long projectId;
    private Long fileId;
    @Schema(description = "站内消息ID（用于前端已读确认）")
    private Long messageId;
    private Long timestamp;

    public NotificationPushPayload withMessageId(Long messageId) {
        return NotificationPushPayload.builder()
                .scene(scene)
                .title(title)
                .content(content)
                .businessId(businessId)
                .projectId(projectId)
                .fileId(fileId)
                .messageId(messageId)
                .timestamp(timestamp)
                .build();
    }
}
