package com.gov.landcheck.core.enums;

import java.util.EnumSet;
import java.util.Set;

import lombok.Getter;

/**
 * Author: Administrator
 * Date: 2025/12/19
 * Description:文件状态枚举
 * :
 */

@Getter
public enum FileStateEnum {
    UPLOADING("UPLOADING", "上传中"),
    WAITING_POST_PROCESS("WAITING_POST_PROCESS", "等待后处理"),
    WAITING_PARSE("WAITING_PARSE", "等待解析"),
    PENDING("PENDING", "解析排队中"),
    PARSING("PARSING", "解析中"),
    UPLOAD_FAIL("UPLOAD_FAIL", "上传失败"),
    PARSE_FAIL("PARSE_FAIL", "解析失败"),
    PARSE_COMPLETE("PARSE_COMPLETE", "解析完成"),
    UNPARSEABLE("UNPARSEABLE", "不可解析"),
    AUDITING("AUDITING", "待审核"),
    AUDIT_PASS("AUDIT_PASS", "审核通过"),
    AUDIT_FAIL("AUDIT_FAIL", "审核不通过");

    private static final Set<FileStateEnum> BLOCKED_DELETE_STATES = EnumSet.of(
            UPLOADING, WAITING_POST_PROCESS, AUDITING);

    private final String code;
    private final String name;

    FileStateEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /** 绝对不可删除（须等待过程结束或由专门流程处理） */
    public static boolean isBlockedForDelete(FileStateEnum state) {
        return state != null && BLOCKED_DELETE_STATES.contains(state);
    }

    public static boolean isParseActive(FileStateEnum state) {
        return state == PENDING || state == PARSING;
    }

    public static String blockedDeleteReason(FileStateEnum state) {
        if (state == null) {
            return "缺少文件状态，暂不可删除";
        }
        return switch (state) {
            case UPLOADING -> "文件正在上传中，请稍后再删除";
            case WAITING_POST_PROCESS -> "文件正在后处理中，请稍后再删除";
            case AUDITING -> "文件正在审核中，请稍后再删除";
            default -> "文件当前状态不可删除";
        };
    }
}
