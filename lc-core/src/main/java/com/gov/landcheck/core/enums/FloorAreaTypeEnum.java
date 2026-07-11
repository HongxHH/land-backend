package com.gov.landcheck.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 面积类型枚举
 *
 * @author system
 * @date 2025/01/21
 */
@Getter
@AllArgsConstructor
public enum FloorAreaTypeEnum {

    BUILDABLE("BUILDABLE", "计容"),
    NON_BUILDABLE("NON_BUILDABLE", "不计容"),
    UNKNOWN("UNKNOWN", "未知");

    /**
     * 类型编码
     */
    private final String code;

    /**
     * 类型名称
     */
    private final String name;

    /**
     * 根据编码获取枚举
     */
    public static FloorAreaTypeEnum getByCode(String code) {
        for (FloorAreaTypeEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return UNKNOWN;
    }
}
