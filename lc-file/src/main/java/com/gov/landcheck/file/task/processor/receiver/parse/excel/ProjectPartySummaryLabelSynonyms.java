package com.gov.landcheck.file.task.processor.receiver.parse.excel;

import org.springframework.util.StringUtils;

/**
 * 项目方汇总表标签同义词与归一化。
 */
public final class ProjectPartySummaryLabelSynonyms {

    private ProjectPartySummaryLabelSynonyms() {
    }

    public enum RowRole {
        CONTRACT, BUILDABLE, DIFFERENCE, UNKNOWN
    }

    public enum AreaCategory {
        TOTAL_BUILDING, COMMERCIAL, RESIDENTIAL, UNKNOWN
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim()
                .replace("㎡", "")
                .replace("m²", "")
                .replace(" ", "")
                .replace("（", "(")
                .replace("）", ")")
                .toLowerCase();
    }

    public static boolean containsStrongAnchor(String text) {
        String n = normalize(text);
        return n.contains("项目方声明") || n.contains("声明汇总") || n.contains("项目方");
    }

    public static RowRole detectRowRole(String text) {
        String n = normalize(text);
        if (!StringUtils.hasText(n)) {
            return RowRole.UNKNOWN;
        }
        if (n.contains("差值") || n.contains("差异") || n.contains("差额") || n.equals("a-b")) {
            return RowRole.DIFFERENCE;
        }
        if (n.contains("计容") || n.contains("可计容")) {
            return RowRole.BUILDABLE;
        }
        if (n.contains("合同") && !n.contains("差值") && !n.contains("差异")) {
            return RowRole.CONTRACT;
        }
        return RowRole.UNKNOWN;
    }

    public static AreaCategory detectAreaCategory(String text) {
        String n = normalize(text);
        if (!StringUtils.hasText(n)) {
            return AreaCategory.UNKNOWN;
        }
        if (n.contains("商业")) {
            return AreaCategory.COMMERCIAL;
        }
        if (n.contains("住宅")) {
            return AreaCategory.RESIDENTIAL;
        }
        if (n.contains("建筑") || n.contains("总计") || n.contains("合计")) {
            return AreaCategory.TOTAL_BUILDING;
        }
        return AreaCategory.UNKNOWN;
    }

    public static boolean isSummaryCategoryRow(String rowText) {
        String n = normalize(rowText);
        return n.contains("合同") && (n.contains("计容") || n.contains("建筑") || n.contains("商业") || n.contains("住宅"));
    }

    public static boolean isNumericLike(String text) {
        return ProjectPartySummaryNumberParser.parseFlexibleNumber(text) != null;
    }
}
