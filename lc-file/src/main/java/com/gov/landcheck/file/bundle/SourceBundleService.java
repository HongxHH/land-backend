package com.gov.landcheck.file.bundle;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.audit.OperationAuditService;
import com.gov.landcheck.core.audit.OperationType;
import com.gov.landcheck.core.audit.TargetType;
import com.gov.landcheck.core.bo.entity.FileArchive;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.Project;
import com.gov.landcheck.file.service.FileService;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.Filters;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 规划并流式写出一个项目的全部原件压缩包。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceBundleService {

    private static final int GRIDFS_ID_CHUNK = 200;

    private final FileService fileService;
    private final MongoTemplate mongoTemplate;
    private final GridFSBucket gridFsBucket;
    private final OperationAuditService operationAuditService;

    public SourceBundlePlan plan(Long projectId) {
        if (projectId == null) {
            return SourceBundlePlan.missingProject();
        }
        Project project = mongoTemplate.findById(projectId, Project.class);
        if (project == null || Integer.valueOf(1).equals(project.getIsDeleted())) {
            return SourceBundlePlan.missingProject();
        }
        List<FileRecord> files = fileService.getFilesByProjectId(projectId);
        List<FileArchive> archives = mongoTemplate.find(
                new Query(Criteria.where("project_id").is(projectId)), FileArchive.class);
        Map<String, Long> lengths = loadGridFsLengths(files);
        return SourceBundlePlanner.plan(project.getProjectName(), archives, files, lengths);
    }

    public void write(SourceBundlePlan plan, OutputStream output) throws IOException {
        if (plan == null || !plan.downloadable()) {
            throw new IOException("没有可下载的原件");
        }
        List<ZipStreamEntry> entries = new ArrayList<>(plan.entries().size());
        for (PlannedZipEntry entry : plan.entries()) {
            if (entry.inline()) {
                byte[] bytes = entry.inlineBytes();
                entries.add(new ZipStreamEntry(entry.path(), bytes.length, () -> new ByteArrayInputStream(bytes)));
            } else {
                String gridfsId = entry.gridfsId();
                entries.add(new ZipStreamEntry(entry.path(), entry.size(), () -> openGridFs(gridfsId)));
            }
        }
        StoredZipStreamer.write(output, entries);
    }

    public void auditDownload(SourceBundlePlan plan, long projectId, SourceBundleTicket ticket) {
        if (plan == null || !plan.downloadable() || ticket == null) {
            return;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("fileCount", plan.fileCount());
        extra.put("totalBytes", plan.totalBytes());
        extra.put("missingCount", plan.missingCount());
        extra.put("zipBytes", plan.zipBytes());
        String summary = "下载全部原件 " + plan.fileCount() + " 个，共 " + plan.totalBytes() + " 字节";
        if (plan.missingCount() > 0) {
            summary += "，缺失 " + plan.missingCount() + " 个";
        }
        operationAuditService.recordOperation(OperationType.DOWNLOAD.name(), TargetType.PROJECT.getValue(),
                String.valueOf(projectId), projectId, null, summary, extra, ticket.userId(), ticket.userName());
    }

    private Map<String, Long> loadGridFsLengths(List<FileRecord> files) {
        Set<String> canonicalIds = new LinkedHashSet<>();
        if (files != null) {
            for (FileRecord file : files) {
                String canonical = SourceBundlePlanner.canonicalGridFsId(file == null ? null : file.getGridfsId());
                if (canonical != null) {
                    canonicalIds.add(canonical);
                }
            }
        }
        List<ObjectId> ids = canonicalIds.stream().map(ObjectId::new).toList();
        Map<String, Long> lengths = new HashMap<>();
        for (int offset = 0; offset < ids.size(); offset += GRIDFS_ID_CHUNK) {
            List<ObjectId> chunk = ids.subList(offset, Math.min(offset + GRIDFS_ID_CHUNK, ids.size()));
            try {
                for (GridFSFile file : gridFsBucket.find(Filters.in("_id", chunk))) {
                    if (file.getObjectId() != null) {
                        lengths.put(file.getObjectId().toHexString(), file.getLength());
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("批量读取 GridFS 长度失败: count={}, error={}", chunk.size(), ex.getMessage());
                throw new IllegalStateException("读取原件大小失败，请稍后再试", ex);
            }
        }
        return lengths;
    }

    private InputStream openGridFs(String gridfsId) {
        return gridFsBucket.openDownloadStream(new ObjectId(gridfsId));
    }
}
