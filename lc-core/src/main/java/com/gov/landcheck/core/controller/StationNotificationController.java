package com.gov.landcheck.core.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.dto.StationMessageReadRequestDTO;
import com.gov.landcheck.core.bo.dto.StationMessageVO;
import com.gov.landcheck.core.bo.entity.StationMessage;
import com.gov.landcheck.core.service.StationMessageService;

import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 站内消息接口：未读查询、已读确认。
 */
@Tag(name = "站内消息", description = "站内消息未读查询与已读确认")
@RestController
@RequestMapping("/station-notification")
public class StationNotificationController {

    private final StationMessageService stationMessageService;

    public StationNotificationController(StationMessageService stationMessageService) {
        this.stationMessageService = stationMessageService;
    }

    @Operation(summary = "查询未读消息", description = "需登录，用户身份来自 Sa-Token。传 projectIds 时仅查这些项目下的未读；不传或为空时查最近站内消息中的未读。")
    @GetMapping("/unread")
    public AjaxJson listUnread(@RequestParam(value = "projectIds", required = false) String projectIds,
                               @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        String userId = resolveLoginUserIdString();
        if (userId == null) {
            return AjaxJson.getError("未登录或无法解析当前用户");
        }
        List<Long> projectIdList = parseProjectIds(projectIds);
        List<StationMessage> unread = projectIdList.isEmpty()
                ? stationMessageService.listUnreadMessagesForUserAllProjects(userId, limit)
                : stationMessageService.listUnreadMessages(userId, projectIdList, limit);
        List<StationMessageVO> data = unread.stream()
                .map(this::toVO)
                .toList();
        return AjaxJson.getSuccessData(data);
    }

    @Operation(summary = "标记已读", description = "需登录；用户身份来自 Sa-Token。")
    @PostMapping("/read")
    public AjaxJson markRead(@Valid @RequestBody StationMessageReadRequestDTO request) {
        String userId = resolveLoginUserIdString();
        if (userId == null) {
            return AjaxJson.getError("未登录或无法解析当前用户");
        }
        stationMessageService.markRead(userId, request.getMessageIds());
        return AjaxJson.getSuccess();
    }

    /**
     * 与 {@link com.gov.landcheck.user.controller.AuthController} 中 {@code StpUtil.login(user.getId())} 一致，使用数字 id 的字符串形式。
     */
    private static String resolveLoginUserIdString() {
        if (!StpUtil.isLogin()) {
            return null;
        }
        return String.valueOf(StpUtil.getLoginIdAsLong());
    }

    private List<Long> parseProjectIds(String projectIds) {
        if (projectIds == null || projectIds.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(projectIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(this::safeParseLong)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private Long safeParseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private StationMessageVO toVO(StationMessage message) {
        return StationMessageVO.builder()
                .id(message.getId())
                .scene(message.getScene())
                .title(message.getTitle())
                .content(message.getContent())
                .businessId(message.getBusinessId())
                .projectId(message.getProjectId())
                .fileId(message.getFileId())
                .topicKey(message.getTopicKey())
                .sentAt(message.getSentAt())
                .build();
    }
}

