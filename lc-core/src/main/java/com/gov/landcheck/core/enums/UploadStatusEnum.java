package com.gov.landcheck.core.enums;

import lombok.Getter;

/**
 * Author: Administrator
 * Date: 2025/12/20
 * Description:上传状态枚举
 */
@Getter
public enum UploadStatusEnum {
    PENDING("PENDING", "待上传"),
    SUCCESS("SUCCESS", "上传成功"),
    FAILED("FAILED", "上传失败");

    private final String code;
    private final String name;

    UploadStatusEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }
}