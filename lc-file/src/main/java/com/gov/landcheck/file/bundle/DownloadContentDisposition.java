package com.gov.landcheck.file.bundle;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 附件下载的 Content-Disposition。中文文件名走 RFC 5987，避免把登录票据写进这个头。
 */
public final class DownloadContentDisposition {

    private DownloadContentDisposition() {
    }

    public static String attachment(String filename) {
        String safeName = filename == null || filename.isBlank() ? "sources.zip" : filename;
        String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        String fallback = isAsciiFilename(safeName) ? safeName : "sources.zip";
        return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }

    private static boolean isAsciiFilename(String filename) {
        for (int i = 0; i < filename.length(); i++) {
            char ch = filename.charAt(i);
            if (ch < 0x20 || ch >= 0x7f || ch == '"' || ch == '\\') {
                return false;
            }
        }
        return true;
    }
}
