package com.gov.landcheck.file.service.parse;

import org.springframework.util.StringUtils;

import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.core.enums.ParseJobStateEnum;

/**
 * 解析取消语义判定：区分用户主动取消与系统/删除等场景，供自动重提交守卫使用。
 */
public final class ParseCancelSupport {

    public static final String REASON_USER_CANCEL = "user_cancel";
    public static final String REASON_USER_MANUAL = "用户手动取消";
    public static final String REASON_USER_ACTIVE = "用户主动取消";

    private ParseCancelSupport() {
    }

    /**
     * 最新 ParseJob 是否为用户主动取消且尚未手动重新发起解析。
     */
    public static boolean isUserCancelled(ParseJob parseJob) {
        if (parseJob == null) {
            return false;
        }
        return ParseJobStateEnum.CANCELLED.equals(parseJob.getJobStatus())
                && parseJob.isCancelRequested()
                && isUserInitiatedReason(parseJob.getCancelReason());
    }

    public static boolean isUserInitiatedReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        String normalized = reason.trim();
        if (REASON_USER_CANCEL.equalsIgnoreCase(normalized)) {
            return true;
        }
        return REASON_USER_MANUAL.equals(normalized) || REASON_USER_ACTIVE.equals(normalized);
    }
}
