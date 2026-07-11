package com.gov.landcheck.core.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统用户类型（与 {@code sys_user.user_type} 存储值一致，供 Sa-Token 角色与业务判断使用）。
 */
public final class UserTypeConstants {

    /** 超级管理员 */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    /** 开发人员 */
    public static final String DEVELOPER = "DEVELOPER";
    /** 普通用户（自助注册默认） */
    public static final String USER = "USER";

    /**
     * 历史数据可能存在的旧值，仅用于展示或兼容读取，新建/更新不应再写入。
     */
    public static final String LEGACY_DEPT_USER = "DEPT_USER";

    /** 已废弃的历史「管理员」角色，登录与会话按普通用户处理。 */
    public static final String LEGACY_ADMIN = "ADMIN";

    private static final Set<String> ALL_MODERN = Set.of(SUPER_ADMIN, DEVELOPER, USER);

    private UserTypeConstants() {
    }

    /**
     * 创建/更新用户时可写入的类型（三选一）。
     */
    public static boolean isAssignable(String userType) {
        return userType != null && ALL_MODERN.contains(userType);
    }

    /**
     * 是否可识别（含历史 DEPT_USER、ADMIN）。
     */
    public static boolean isKnown(String userType) {
        if (userType == null) {
            return false;
        }
        return ALL_MODERN.contains(userType) || LEGACY_DEPT_USER.equals(userType) || LEGACY_ADMIN.equals(userType);
    }

    /**
     * 写入 Sa-Token 会话 / 角色鉴权用的类型（历史值归一为 USER）。
     */
    public static String normalizeForSession(String userType) {
        if (userType == null || userType.isBlank()) {
            return USER;
        }
        if (LEGACY_DEPT_USER.equals(userType) || LEGACY_ADMIN.equals(userType)) {
            return USER;
        }
        return userType;
    }

    public static String displayLabel(String userType) {
        if (userType == null) {
            return "";
        }
        return switch (userType) {
            case SUPER_ADMIN -> "超级管理员";
            case DEVELOPER -> "开发人员";
            case USER, LEGACY_DEPT_USER, LEGACY_ADMIN -> "普通用户";
            default -> userType;
        };
    }

    /**
     * 供前端下拉等：固定顺序。
     */
    public static List<Map<String, String>> options() {
        List<Map<String, String>> list = new ArrayList<>();
        list.add(option(SUPER_ADMIN, "超级管理员"));
        list.add(option(DEVELOPER, "开发人员"));
        list.add(option(USER, "普通用户"));
        return list;
    }

    private static Map<String, String> option(String value, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("label", label);
        return m;
    }
}
