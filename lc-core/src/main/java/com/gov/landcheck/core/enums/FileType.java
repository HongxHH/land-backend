package com.gov.landcheck.core.enums;

/**
 * 文件类型枚举（文件格式）
 * 用于标识文件的物理格式，如 PDF、Excel 等
 *
 * @author system
 * @date 2025/12/19
 */
public enum FileType {
    /**
     * PDF 文档
     */
    PDF("PDF", ".pdf"),

    /**
     * Excel 文件（XLS）
     */
    XLS("XLS", ".xls"),

    /**
     * Excel 文件（XLSX）
     */
    XLSX("XLSX", ".xlsx"),

    /**
     * Word 文档（DOC）
     */
    DOC("DOC", ".doc"),

    /**
     * Word 文档（DOCX）
     */
    DOCX("DOCX", ".docx"),

    /**
     * PNG 图片
     */
    PNG("PNG", ".png"),

    /**
     * JPEG 图片
     */
    JPEG("JPEG", ".jpeg", ".jpg"),

    /**
     * GIF 图片
     */
    GIF("GIF", ".gif"),

    /**
     * 未知类型
     */
    UNKNOWN("UNKNOWN");

    private final String code;
    private final String[] extensions;

    FileType(String code, String... extensions) {
        this.code = code;
        this.extensions = extensions;
    }

    public String getCode() {
        return code;
    }

    public String[] getExtensions() {
        return extensions;
    }

    /**
     * 根据文件后缀名获取文件类型
     *
     * @param suffixName 文件后缀名（带点号，如 ".pdf"）
     * @return 文件类型
     */
    public static FileType getFileType(String suffixName) {
        if (suffixName == null || suffixName.isEmpty()) {
            return UNKNOWN;
        }

        String lowerSuffix = suffixName.toLowerCase();
        for (FileType type : values()) {
            if (type == UNKNOWN) {
                continue;
            }
            for (String ext : type.extensions) {
                if (ext.equalsIgnoreCase(lowerSuffix)) {
                    return type;
                }
            }
        }
        return UNKNOWN;
    }

    /**
     * 根据文件名获取文件类型
     *
     * @param fileName 文件名
     * @return 文件类型
     */
    public static FileType getFileTypeByFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return UNKNOWN;
        }

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return UNKNOWN;
        }

        String suffix = "." + fileName.substring(lastDotIndex + 1);
        return getFileType(suffix);
    }
}
