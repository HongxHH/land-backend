package com.gov.landcheck.core.enums;

import lombok.Getter;

/**
 * Author: Administrator
 * Date: 2025/12/19
 * Description:解析任务状态枚举
 * :
 */

@Getter
public enum ParseJobStateEnum {

    PENDING("PENDING", "待处理"),
    RUNNING("RUNNING", "处理中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String name;

    ParseJobStateEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
