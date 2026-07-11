package com.gov.landcheck.file.task.processor.receiver.parse.excel;

import java.math.BigDecimal;

import org.springframework.util.StringUtils;

/**
 * 项目方汇总数值解析（Excel 单元格 / LLM 文本通用）。
 */
public final class ProjectPartySummaryNumberParser {

    private ProjectPartySummaryNumberParser() {
    }

    public static BigDecimal parseFlexibleNumber(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim()
                .replace("㎡", "")
                .replace("m²", "")
                .replace("，", ",")
                .replace(",", "")
                .replace(" ", "");
        if ("--".equals(normalized) || "-".equals(normalized) || "null".equalsIgnoreCase(normalized)) {
            return null;
        }
        if (normalized.startsWith("(") && normalized.endsWith(")")) {
            normalized = "-" + normalized.substring(1, normalized.length() - 1);
        }
        try {
            return new BigDecimal(normalized);
        } catch (Exception ex) {
            return null;
        }
    }

    public static BigDecimal parseCellValue(Object cellValue) {
        if (cellValue == null) {
            return null;
        }
        if (cellValue instanceof BigDecimal bd) {
            return bd;
        }
        if (cellValue instanceof java.math.BigInteger bi) {
            return new BigDecimal(bi);
        }
        if (cellValue instanceof Number number) {
            if (number instanceof Long || number instanceof Integer || number instanceof Short
                    || number instanceof Byte) {
                return BigDecimal.valueOf(number.longValue());
            }
            return BigDecimal.valueOf(number.doubleValue());
        }
        return parseFlexibleNumber(String.valueOf(cellValue));
    }
}
