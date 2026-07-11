package com.gov.landcheck.core.bo.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 站内消息已读请求。
 */
@Data
@Schema(description = "站内消息已读请求")
public class StationMessageReadRequestDTO {

    @NotEmpty(message = "messageIds 不能为空")
    @Schema(description = "消息ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> messageIds;
}

