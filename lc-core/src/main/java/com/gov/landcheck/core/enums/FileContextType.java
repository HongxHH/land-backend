package com.gov.landcheck.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件内容类型枚举
 * 用于标识文件的内容类型，如合同、数据文件、规划条件书等
 *
 * @author system
 * @date 2025/12/19
 */
@Getter
@AllArgsConstructor
public enum FileContextType {

    /**
     * 合同（出让合同等）
     */
    CONTRACT("CONTRACT", "合同"),

    /**
     * 数据文件（实测数据、规划数据等结构化数据文件）
     */
    DATA_FILE("DATA_FILE", "数据文件"),

    /**
     * 实测报告
     */
    SURVEY_REPORT("SURVEY_REPORT", "实测报告"),

    /**
     * 规划复核表
     */
    PLANNING_REVIEW("PLANNING_REVIEW", "规划复核表"),

    /**
     * 容量指标核查表
     */
    CAPACITY_INDICATOR("CAPACITY_INDICATOR", "容量指标核查表"),

    /**
     * 项目方实测汇总表
     */
    PROJECT_PARTY_SURVEY_SUMMARY("PROJECT_PARTY_SURVEY_SUMMARY", "项目方实测汇总表"),

    /**
     * 其他文件
     */
    OTHER("OTHER", "其他文件");

    private final String code; // 编码
    private final String name; // 名称

    /**
     * 根据编码获取枚举
     */
    public static FileContextType getByCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (FileContextType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 检查文件内容类型是否为有效的上传类型
     */
    public boolean isValidForUpload() {
        return this == CONTRACT
                || this == SURVEY_REPORT
                || this == PLANNING_REVIEW
                || this == CAPACITY_INDICATOR
                || this == PROJECT_PARTY_SURVEY_SUMMARY
                || this == OTHER;
    }

    /**
     * 获取所有有效的上传类型
     */
    public static FileContextType[] getValidUploadTypes() {
        return new FileContextType[] { CONTRACT, SURVEY_REPORT, PLANNING_REVIEW, CAPACITY_INDICATOR,
                PROJECT_PARTY_SURVEY_SUMMARY, OTHER };
    }

    /**
     * 检查物理文件格式是否满足该内容类型的解析要求。
     * OTHER 不限制格式；未知扩展名对需解析类型视为不支持。
     */
    public boolean isSupportedFileType(FileType fileType) {
        if (this == OTHER) {
            return true;
        }
        if (fileType == null || fileType == FileType.UNKNOWN) {
            return false;
        }
        return switch (this) {
            case CONTRACT, SURVEY_REPORT, PLANNING_REVIEW, CAPACITY_INDICATOR -> fileType == FileType.PDF;
            case PROJECT_PARTY_SURVEY_SUMMARY -> fileType == FileType.XLS || fileType == FileType.XLSX;
            default -> false;
        };
    }

    /**
     * 不支持格式时的用户提示（不含类型名称前缀）。
     */
    public String getSupportedFormatHint() {
        return switch (this) {
            case CONTRACT, SURVEY_REPORT, PLANNING_REVIEW, CAPACITY_INDICATOR -> "仅支持 PDF 文件";
            case PROJECT_PARTY_SURVEY_SUMMARY -> "仅支持 Excel 文件（.xls / .xlsx）";
            default -> "";
        };
    }

    /**
     * 上传后自动提交解析的内容类型（与
     * {@link com.gov.landcheck.file.service.parse.AutoParseSubmissionService} 对齐）。
     */
    public boolean isAutoParseContext() {
        return this == CONTRACT
                || this == SURVEY_REPORT
                || this == PLANNING_REVIEW
                || this == CAPACITY_INDICATOR
                || this == PROJECT_PARTY_SURVEY_SUMMARY;
    }

    public static boolean isAutoParseContext(FileContextType contextType) {
        return contextType != null && contextType.isAutoParseContext();
    }
}
