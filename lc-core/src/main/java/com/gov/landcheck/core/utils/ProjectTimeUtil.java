package com.gov.landcheck.core.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * 项目时间工具类（存库与接口为 ISO {@code yyyy-MM-dd}，自然日）
 *
 * @author system
 * @date 2026/01/23
 */
public final class ProjectTimeUtil {

    private static final DateTimeFormatter ISO_STRICT = DateTimeFormatter.ISO_LOCAL_DATE
            .withResolverStyle(ResolverStyle.STRICT);

    private ProjectTimeUtil() {
    }

    /**
     * 校验项目时间格式：合法日历 {@code yyyy-MM-dd}。
     *
     * @param projectTime 项目时间字符串
     * @return true-格式正确，false-格式错误
     */
    public static boolean validateProjectTime(String projectTime) {
        return parseIsoDate(projectTime) != null;
    }

    /**
     * 规范化为 ISO {@code yyyy-MM-dd}，用于按字符串字典序的时间范围查询。
     *
     * @param projectTime 项目时间字符串（如 2025-11-15）
     * @return 规范化后的字符串，格式错误时返回 null
     */
    public static String normalizeToIsoDate(String projectTime) {
        LocalDate d = parseIsoDate(projectTime);
        return d == null ? null : d.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * 获取项目时间的格式说明
     *
     * @return 格式说明字符串
     */
    public static String getProjectTimeFormatDescription() {
        return "ISO 日期 yyyy-MM-dd（自然日，例如：2025-11-15）";
    }

    private static LocalDate parseIsoDate(String projectTime) {
        if (projectTime == null) {
            return null;
        }
        String trimmed = projectTime.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed, ISO_STRICT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
