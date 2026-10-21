package com.gov.landcheck.file.bundle;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;

import com.gov.landcheck.core.bo.entity.FileArchive;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.enums.FileContextType;

/**
 * 把项目原件排成压缩包目录。只使用元数据和 GridFS 长度，不读取文件内容。
 */
public final class SourceBundlePlanner {

    private static final String MISSING_LIST_NAME = "_缺失清单.txt";
    private static final String UNNAMED_PROJECT = "未命名项目";
    private static final String UNNAMED_FILE = "未命名文件";
    private static final String UNNAMED_FOLDER = "未命名归档夹";

    private SourceBundlePlanner() {
    }

    public static SourceBundlePlan plan(String projectName, List<FileArchive> archives, List<FileRecord> files,
            Map<String, Long> gridfsLengths) {
        String root = sanitizeSegment(projectName, UNNAMED_PROJECT, 80);
        Map<Long, String> folders = folderNames(archives);
        List<FileRecord> ordered = new ArrayList<>(files == null ? List.of() : files);
        ordered.sort(FILE_ORDER);

        Set<String> usedPaths = new HashSet<>();
        List<Assigned> present = new ArrayList<>();
        List<String> missingPaths = new ArrayList<>();
        for (FileRecord file : ordered) {
            String path = uniquePath(usedPaths, filePath(root, folders, file));
            String gridfsId = canonicalGridFsId(file.getGridfsId());
            Long length = gridfsId == null || gridfsLengths == null ? null : gridfsLengths.get(gridfsId);
            if (gridfsId == null || length == null || length < 0) {
                missingPaths.add(path);
                continue;
            }
            present.add(new Assigned(path, length, gridfsId));
        }

        if (present.isEmpty()) {
            return new SourceBundlePlan(SourceBundlePlan.Status.EMPTY, displayName(projectName), null, 0, 0,
                    missingPaths.size(), 0, null, List.of());
        }

        present.sort(Comparator.comparing(Assigned::path));
        missingPaths.sort(String::compareTo);

        List<PlannedZipEntry> entries = new ArrayList<>(present.size() + 1);
        long totalBytes = 0;
        for (Assigned file : present) {
            entries.add(new PlannedZipEntry(file.path, file.size, file.gridfsId, null));
            totalBytes += file.size;
        }
        if (!missingPaths.isEmpty()) {
            byte[] listing = missingListing(missingPaths);
            String listingPath = uniquePath(usedPaths, root + "/" + MISSING_LIST_NAME);
            entries.add(new PlannedZipEntry(listingPath, listing.length, null, listing));
        }

        List<ZipStreamEntry> measured = new ArrayList<>(entries.size());
        for (PlannedZipEntry entry : entries) {
            measured.add(new ZipStreamEntry(entry.path(), entry.size(), null));
        }
        long zipBytes;
        try {
            zipBytes = StoredZipStreamer.sizeOf(measured);
        } catch (IllegalArgumentException ex) {
            return SourceBundlePlan.rejected(displayName(projectName), ex.getMessage());
        }

        return new SourceBundlePlan(SourceBundlePlan.Status.READY, displayName(projectName), root + "-原件.zip",
                present.size(), totalBytes, missingPaths.size(), zipBytes, null, List.copyOf(entries));
    }

    static String canonicalGridFsId(String raw) {
        if (raw == null || raw.isBlank() || !ObjectId.isValid(raw.trim())) {
            return null;
        }
        return new ObjectId(raw.trim()).toHexString();
    }

    private static final Comparator<FileRecord> FILE_ORDER = Comparator
            .comparing(FileRecord::getArchiveId, Comparator.nullsFirst(Long::compareTo))
            .thenComparing(FileRecord::getPhase, Comparator.nullsFirst(Integer::compareTo))
            .thenComparing(FileRecord::getOriginalName, Comparator.nullsFirst(String::compareTo))
            .thenComparing(FileRecord::getId, Comparator.nullsFirst(Long::compareTo));

    private static Map<Long, String> folderNames(List<FileArchive> archives) {
        List<FileArchive> ordered = new ArrayList<>(archives == null ? List.of() : archives);
        ordered.sort(Comparator
                .comparing(FileArchive::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(FileArchive::getId, Comparator.nullsLast(Long::compareTo)));
        Set<String> used = new HashSet<>();
        Map<Long, String> names = new LinkedHashMap<>();
        for (FileArchive archive : ordered) {
            if (archive == null || archive.getId() == null) {
                continue;
            }
            String fallback = archive.getKind() == null ? UNNAMED_FOLDER : archive.getKind().getName();
            String name = uniqueSegment(used, sanitizeSegment(archive.getName(), fallback, 80));
            names.put(archive.getId(), name);
        }
        return names;
    }

    private static String filePath(String root, Map<Long, String> folders, FileRecord file) {
        String folder = file.getArchiveId() != null ? folders.get(file.getArchiveId()) : null;
        if (folder == null) {
            FileContextType contextType = file.getFileContextType();
            folder = contextType == null ? "其他文件" : contextType.getName();
        }
        StringBuilder path = new StringBuilder(root).append('/').append(folder);
        if (file.getPhase() != null) {
            path.append("/第").append(file.getPhase()).append("期");
        }
        path.append('/').append(sanitizeFileName(file.getOriginalName()));
        return path.toString();
    }

    private static String uniquePath(Set<String> used, String path) {
        if (used.add(path)) {
            return path;
        }
        int slash = path.lastIndexOf('/');
        String dir = slash >= 0 ? path.substring(0, slash + 1) : "";
        String fileName = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        int index = 2;
        while (true) {
            String candidate = dir + base + " (" + index + ")" + ext;
            if (used.add(candidate)) {
                return candidate;
            }
            index++;
        }
    }

    private static String uniqueSegment(Set<String> used, String name) {
        if (used.add(name)) {
            return name;
        }
        int index = 2;
        while (true) {
            String candidate = name + " (" + index + ")";
            if (used.add(candidate)) {
                return candidate;
            }
            index++;
        }
    }

    static String sanitizeSegment(String raw, String fallback, int maxLength) {
        String value = raw == null ? "" : raw.trim();
        StringBuilder builder = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint < 0x20 || codePoint == 0x7f || codePoint == '/' || codePoint == '\\') {
                builder.append('_');
            } else {
                builder.appendCodePoint(codePoint);
            }
        });
        value = builder.toString().trim();
        while (value.endsWith(".") || value.endsWith(" ")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        while (value.startsWith(".")) {
            value = value.substring(1).trim();
        }
        if (value.isBlank() || ".".equals(value) || "..".equals(value)) {
            return fallback;
        }
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength).trim();
        }
        return value.isBlank() ? fallback : value;
    }

    static String sanitizeFileName(String raw) {
        String name = sanitizeSegment(raw, UNNAMED_FILE, Integer.MAX_VALUE);
        if (UNNAMED_FILE.equals(name) || name.length() <= 100) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
        int keep = Math.max(1, 100 - ext.length());
        String base = name.substring(0, Math.min(keep, name.length() - ext.length())).trim();
        if (base.isBlank()) {
            base = UNNAMED_FILE;
        }
        return base + ext;
    }

    private static byte[] missingListing(List<String> paths) {
        StringBuilder builder = new StringBuilder("以下原件在存储中不存在，未打入压缩包：\n");
        for (String path : paths) {
            builder.append("- ").append(path).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String displayName(String projectName) {
        String trimmed = projectName == null ? "" : projectName.trim();
        return trimmed.isBlank() ? UNNAMED_PROJECT : trimmed;
    }

    private record Assigned(String path, long size, String gridfsId) {
    }
}
