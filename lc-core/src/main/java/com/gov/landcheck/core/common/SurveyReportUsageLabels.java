package com.gov.landcheck.core.common;

/**
 * 实测报告用途计算相关展示标签。
 */
public final class SurveyReportUsageLabels {

    /** 户室用途 OCR/解析缺失时的待确认占位文案 */
    public static final String MISSING_USAGE_LABEL = "（用途缺失）";

    private SurveyReportUsageLabels() {
    }

    public static boolean isBlankUsage(String usage) {
        return usage == null || usage.trim().isEmpty();
    }
}
