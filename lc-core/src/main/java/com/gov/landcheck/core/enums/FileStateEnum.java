package com.gov.landcheck.core.enums;

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

    private final String code;
    private final String name;

    FileStateEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
