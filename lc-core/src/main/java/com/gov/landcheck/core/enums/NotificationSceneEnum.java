package com.gov.landcheck.core.enums;

import lombok.Getter;

/**
 * 站内广播场景枚举。
 */
@Getter
public enum NotificationSceneEnum {

    /** 文件解析成功 */
    PARSE_SUCCESS("PARSE_SUCCESS", "文件解析完成"),

    /** 文件解析失败 */
    PARSE_FAILED("PARSE_FAILED", "文件解析失败");

    private final String code;
    private final String title;

    NotificationSceneEnum(String code, String title) {
        this.code = code;
        this.title = title;
    }
}
