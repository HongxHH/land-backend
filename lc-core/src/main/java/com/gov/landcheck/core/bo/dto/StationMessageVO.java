package com.gov.landcheck.core.bo.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 站内未读消息视图。
 */
@Data
@Builder
@Schema(description = "站内未读消息")
public class StationMessageVO {

    private Long id;
    private String scene;
    private String title;
    private String content;
    private String businessId;
    private Long projectId;
    private Long fileId;
    private String topicKey;
    private LocalDateTime sentAt;
}

