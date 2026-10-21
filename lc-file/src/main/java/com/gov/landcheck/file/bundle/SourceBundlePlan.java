package com.gov.landcheck.file.bundle;

import java.util.List;

/**
 * 一次项目原件打包的规划结果。条目只含路径和长度，不含文件内容。
 */
public record SourceBundlePlan(
        Status status,
        String projectName,
        String downloadFileName,
        int fileCount,
        long totalBytes,
        int missingCount,
        long zipBytes,
        String rejectMessage,
        List<PlannedZipEntry> entries) {

    public enum Status {
        READY,
        EMPTY,
        MISSING_PROJECT,
        REJECTED
    }

    public static SourceBundlePlan missingProject() {
        return new SourceBundlePlan(Status.MISSING_PROJECT, null, null, 0, 0, 0, 0, "项目不存在", List.of());
    }

    public static SourceBundlePlan rejected(String projectName, String message) {
        return new SourceBundlePlan(Status.REJECTED, projectName, null, 0, 0, 0, 0, message, List.of());
    }

    public boolean downloadable() {
        return status == Status.READY;
    }
}
