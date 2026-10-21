package com.gov.landcheck.file.bundle;

/**
 * 规划好的一条压缩包路径。{@code inlineBytes} 非空时是包内生成的文本，否则从 {@code gridfsId} 读取。
 */
public record PlannedZipEntry(String path, long size, String gridfsId, byte[] inlineBytes) {

    public boolean inline() {
        return inlineBytes != null;
    }
}
