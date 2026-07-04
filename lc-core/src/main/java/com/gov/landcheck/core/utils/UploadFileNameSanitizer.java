package com.gov.landcheck.core.utils;

import java.nio.file.Paths;

/**
 * 上传原始文件名校验与净化。
 */
public final class UploadFileNameSanitizer {

    private UploadFileNameSanitizer() {
    }

    /**
     * 剥离路径分量，拒绝非法文件名；无法净化时返回 null。
     */
    public static String sanitize(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return null;
        }
        if (originalFilename.indexOf('\0') >= 0) {
            return null;
        }
        String baseName = Paths.get(originalFilename).getFileName().toString();
        if (baseName.isBlank() || ".".equals(baseName) || "..".equals(baseName)) {
            return null;
        }
        return baseName;
    }
}
