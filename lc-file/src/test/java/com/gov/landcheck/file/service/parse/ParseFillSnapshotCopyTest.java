package com.gov.landcheck.file.service.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.bo.entity.SurveyReportInfo;
import com.gov.landcheck.core.enums.FloorAreaTypeEnum;

class ParseFillSnapshotCopyTest {

    @Test
    void copySurveyReportSnapshotPreservesBusinessFields() {
        SurveyReportInfo source = new SurveyReportInfo();
        source.setId(11L);
        source.setProjectId(22L);
        source.setFileRecordId(33L);
        source.setBuildingName("1号楼");
        source.setIsParsed(1);
        source.setActualTotalBuildingArea(new BigDecimal("100.5"));
        source.setHasUnknownUsage(0);

        SurveyReportInfo copy = ParseArtifactCleanupService.copySurveyReportSnapshot(source);
        copy.setBuildingName("changed");

        assertEquals("1号楼", source.getBuildingName());
        assertEquals(11L, copy.getId());
        assertEquals(new BigDecimal("100.5"), copy.getActualTotalBuildingArea());
        assertEquals(1, copy.getIsParsed());
    }

    @Test
    void copyRoomSnapshotsPreservesIdsAndIsIndependent() {
        RoomInfo source = new RoomInfo();
        source.setId(101L);
        source.setFileRecordId(33L);
        source.setRoomNumber("101");
        source.setBuildingArea(new BigDecimal("88.00"));
        source.setFloorAreaType(FloorAreaTypeEnum.BUILDABLE);

        List<RoomInfo> copies = ParseArtifactCleanupService.copyRoomSnapshots(List.of(source));
        copies.get(0).setRoomNumber("999");

        assertEquals("101", source.getRoomNumber());
        assertEquals(101L, copies.get(0).getId());
        assertEquals(FloorAreaTypeEnum.BUILDABLE, copies.get(0).getFloorAreaType());
        assertTrue(ParseArtifactCleanupService.copyRoomSnapshots(null).isEmpty());
        assertNull(ParseArtifactCleanupService.copySurveyReportSnapshot(null));
    }
}
