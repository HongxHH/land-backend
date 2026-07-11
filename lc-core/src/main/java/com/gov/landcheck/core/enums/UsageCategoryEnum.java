package com.gov.landcheck.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用途类别枚举
 *
 * @author system
 * @date 2025/01/21
 */
@Getter
@AllArgsConstructor
public enum UsageCategoryEnum {

    RESIDENTIAL("RESIDENTIAL", "住宅", "BUILDABLE"),
    COMMERCIAL("COMMERCIAL", "商业", "BUILDABLE"),
    MANAGEMENT("MANAGEMENT", "物管用房", "BUILDABLE"),
    OTHER_BUILDABLE("OTHER_BUILDABLE", "其他计容", "BUILDABLE"),
    COMMUNITY("COMMUNITY", "社区用房", "NON_BUILDABLE"),
    OTHER_PUBLIC("OTHER_PUBLIC", "其他公用", "NON_BUILDABLE"),
    UNKNOWN("UNKNOWN", "未知", "UNKNOWN");

    /**
     * 类别编码
     */
    private final String code;

    /**
     * 类别名称
     */
    private final String name;

    /**
     * 面积类型：BUILDABLE(计容), NON_BUILDABLE(不计容), UNKNOWN(未知)
     */
    private final String floorAreaType;

    /**
     * 根据编码获取枚举
     */
    public static UsageCategoryEnum getByCode(String code) {
        for (UsageCategoryEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return UNKNOWN;
    }

    /**
     * 判断是否为计容类别
     */
    public boolean isBuildable() {
        return "BUILDABLE".equals(this.floorAreaType);
    }

    /**
     * 判断是否为不计容类别
     */
    public boolean isNonBuildable() {
        return "NON_BUILDABLE".equals(this.floorAreaType);
    }
}
