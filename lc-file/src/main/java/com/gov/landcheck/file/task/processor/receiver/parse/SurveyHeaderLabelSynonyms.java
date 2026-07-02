package com.gov.landcheck.file.task.processor.receiver.parse;

import java.util.Arrays;
import java.util.List;

import org.springframework.util.StringUtils;

/**
 * 实测报告头部字段标签同义词与归一化。
 */
public final class SurveyHeaderLabelSynonyms {

    public enum HeaderField {
        PROPERTY_AREA_CONFIRMATION_NOTICE("property_area_confirmation_notice_number"),
        REAL_ESTATE_SURVEY_REPORT("real_estate_survey_report_number"),
        BUILDING_NAME("building_name"),
        PROPERTY_CERTIFICATE("property_certificate");

        private final String normalizedKey;

        HeaderField(String normalizedKey) {
            this.normalizedKey = normalizedKey;
        }

        public String normalizedKey() {
            return normalizedKey;
        }
    }

    private static final List<String> NOTICE_LABELS = Arrays.asList(
            "确认告知书编号",
            "房产面积确认告知书编号",
            "面积确认告知书编号",
            "告知书编号");

    private static final List<String> REPORT_LABELS = Arrays.asList(
            "分栋报告编号",
            "测绘业务编号",
            "房地产勘测报告书编号",
            "勘测报告书编号",
            "报告书编号");

    private static final List<String> BUILDING_NAME_LABELS = Arrays.asList(
            "房屋坐落",
            "建筑名称");

    /** 短于该长度的标签仅允许精确匹配，避免「坐落」等误命中 */
    private static final int MIN_FUZZY_LABEL_LENGTH = 4;

    private static final List<String> CERTIFICATE_LABELS = Arrays.asList(
            "土地权属来源证明材料",
            "不动产权证编号",
            "权属来源证明材料");

    private SurveyHeaderLabelSynonyms() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim()
                .replace("\r", "")
                .replace("\n", "")
                .replace("\t", "")
                .replace("㎡", "")
                .replace("m²", "")
                .replace(" ", "")
                .replace("（", "(")
                .replace("）", ")")
                .toLowerCase();
    }

    public static List<String> labelsFor(HeaderField field) {
        return switch (field) {
            case PROPERTY_AREA_CONFIRMATION_NOTICE -> NOTICE_LABELS;
            case REAL_ESTATE_SURVEY_REPORT -> REPORT_LABELS;
            case BUILDING_NAME -> BUILDING_NAME_LABELS;
            case PROPERTY_CERTIFICATE -> CERTIFICATE_LABELS;
        };
    }

    public static boolean cellMatchesLabel(String cellText, String label) {
        if (!StringUtils.hasText(cellText) || !StringUtils.hasText(label)) {
            return false;
        }
        String normalizedCell = normalize(cellText);
        String normalizedLabel = normalize(label);
        if (normalizedCell.equals(normalizedLabel)) {
            return true;
        }
        if (normalizedLabel.length() < MIN_FUZZY_LABEL_LENGTH) {
            return false;
        }
        return normalizedCell.contains(normalizedLabel)
                && Math.abs(normalizedCell.length() - normalizedLabel.length()) <= 2;
    }

    public static HeaderField matchField(String cellText) {
        if (!StringUtils.hasText(cellText)) {
            return null;
        }
        HeaderField bestField = null;
        int bestLabelLength = 0;
        for (HeaderField field : HeaderField.values()) {
            for (String label : labelsFor(field)) {
                if (!cellMatchesLabel(cellText, label)) {
                    continue;
                }
                int labelLength = normalize(label).length();
                if (labelLength > bestLabelLength) {
                    bestLabelLength = labelLength;
                    bestField = field;
                }
            }
        }
        return bestField;
    }

    /** 户室表列头归一化（P1 列映射加固） */
    public static boolean headerMatchesAlias(String headerText, String alias) {
        if (!StringUtils.hasText(headerText) || !StringUtils.hasText(alias)) {
            return false;
        }
        String normalizedHeader = normalize(headerText);
        String normalizedAlias = normalize(alias);
        if (normalizedHeader.equals(normalizedAlias)) {
            return true;
        }
        if (normalizedHeader.contains(normalizedAlias) || normalizedAlias.contains(normalizedHeader)) {
            return Math.abs(normalizedHeader.length() - normalizedAlias.length()) <= 2;
        }
        return false;
    }
}
