package com.gov.landcheck.file.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.gov.landcheck.core.bo.entity.FileArchive;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.enums.FileContextType;

class SourceBundlePlannerTest {

    @Test
    void laysOutArchivePhaseAndDedupesNames() {
        ObjectId contractId = new ObjectId();
        ObjectId reportId = new ObjectId();
        ObjectId missingId = new ObjectId();
        FileArchive contractFolder = FileArchive.builder().name("合同/扫描").kind(FileContextType.CONTRACT).sortOrder(1)
                .build();
        contractFolder.setId(11L);
        FileArchive reportFolder = FileArchive.builder().name("实测报告").kind(FileContextType.SURVEY_REPORT).sortOrder(2)
                .build();
        reportFolder.setId(12L);

        FileRecord first = file(1L, 11L, null, "出让合同.pdf", contractId.toHexString());
        FileRecord second = file(2L, 11L, null, "出让合同.pdf", contractId.toHexString());
        FileRecord report = file(3L, 12L, 2, "../第2期.pdf", reportId.toHexString());
        FileRecord missing = file(4L, null, null, "丢失.pdf", missingId.toHexString());
        FileRecord blank = file(5L, null, null, "没有存储.pdf", "  ");

        SourceBundlePlan plan = SourceBundlePlanner.plan("滨江/一期", List.of(reportFolder, contractFolder),
                List.of(report, missing, first, blank, second),
                Map.of(contractId.toHexString(), 20L, reportId.toHexString(), 7L));

        assertEquals(SourceBundlePlan.Status.READY, plan.status());
        assertEquals(3, plan.fileCount());
        assertEquals(47L, plan.totalBytes());
        assertEquals(2, plan.missingCount());
        assertEquals("滨江_一期-原件.zip", plan.downloadFileName());
        assertEquals(20L, entry(plan, "滨江_一期/合同_扫描/出让合同.pdf").size());
        assertEquals(20L, entry(plan, "滨江_一期/合同_扫描/出让合同 (2).pdf").size());
        assertEquals("滨江_一期/实测报告/第2期/_第2期.pdf",
                entry(plan, "滨江_一期/实测报告/第2期/_第2期.pdf").path());
        PlannedZipEntry listingEntry = plan.entries().get(plan.entries().size() - 1);
        assertTrue(listingEntry.inline());
        String listing = new String(listingEntry.inlineBytes(), StandardCharsets.UTF_8);
        assertTrue(listing.contains("滨江_一期/其他文件/丢失.pdf"));
        assertTrue(listing.contains("滨江_一期/其他文件/没有存储.pdf"));
        assertEquals(plan.zipBytes(), StoredZipStreamer.sizeOf(plan.entries().stream()
                .map(item -> new ZipStreamEntry(item.path(), item.size(), null))
                .toList()));
    }

    @Test
    void emptyProjectDoesNotInventAZip() {
        SourceBundlePlan plan = SourceBundlePlanner.plan("空项目", List.of(), List.of(), Map.of());
        assertEquals(SourceBundlePlan.Status.EMPTY, plan.status());
        assertEquals(0, plan.fileCount());
        assertEquals(0, plan.zipBytes());
        assertTrue(plan.entries().isEmpty());
    }

    @Test
    void rejectsArchiveOverFourGigabytes() {
        ObjectId id = new ObjectId();
        FileRecord huge = file(1L, null, null, "huge.pdf", id.toHexString());
        SourceBundlePlan plan = SourceBundlePlanner.plan("大项目", List.of(), List.of(huge),
                Map.of(id.toHexString(), StoredZipStreamer.ZIP32_LIMIT));
        assertEquals(SourceBundlePlan.Status.REJECTED, plan.status());
        assertTrue(plan.rejectMessage().contains("4GB"));
    }

    private static PlannedZipEntry entry(SourceBundlePlan plan, String path) {
        return plan.entries().stream().filter(item -> path.equals(item.path())).findFirst().orElseThrow();
    }

    private static FileRecord file(Long id, Long archiveId, Integer phase, String name, String gridfsId) {
        FileRecord record = new FileRecord();
        record.setId(id);
        record.setArchiveId(archiveId);
        record.setPhase(phase);
        record.setOriginalName(name);
        record.setGridfsId(gridfsId);
        record.setFileContextType(archiveId == null ? FileContextType.OTHER : null);
        return record;
    }
}
