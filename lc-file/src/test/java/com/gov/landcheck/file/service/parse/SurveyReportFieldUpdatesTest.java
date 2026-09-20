package com.gov.landcheck.file.service.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Update;

import com.gov.landcheck.core.bo.entity.SurveyReportInfo;

class SurveyReportFieldUpdatesTest {

    @Test
    void applyComputedStatsResetClearsVerificationButKeepsParsedAndOcr() {
        SurveyReportInfo info = populatedSurvey();

        SurveyReportFieldUpdates.applyComputedStatsReset(info);

        assertNull(info.getIsVerified());
        assertFalse(info.isValidationFailed());
        assertNull(info.getVerificationErrorReason());
        assertEquals(0, info.getHasUnknownUsage());
        assertNull(info.getUnknownUsages());
        assertNull(info.getUnknownUsageCount());
        assertNull(info.getPendingConfirmArea());
        assertNull(info.getActualTotalBuildingArea());
        assertNull(info.getActualResidentialArea());
        assertNull(info.getActualCommercialArea());
        assertNull(info.getActualManagementRoomArea());
        assertNull(info.getActualOtherBuildableArea());
        assertNull(info.getActualCommunityArea());
        assertNull(info.getActualOtherPublicArea());
        assertNull(info.getTotalBuildableArea());
        assertNull(info.getTotalNonBuildableArea());
        assertNull(info.getRoomInfoBuildingAreaSum());
        assertNull(info.getRoomInfoInnerAreaSum());
        assertNull(info.getRoomInfoBalconyAreaSum());
        assertNull(info.getRoomInfoSharedAreaSum());
        assertEquals(1, info.getIsParsed());
        assertEquals(new BigDecimal("99.5"), info.getRoomInfoBuildingAreaSumFromOcr());
        assertEquals(new BigDecimal("88.8"), info.getRoomInfoInnerAreaSumFromOcr());
        assertEquals("1号楼", info.getBuildingName());
    }

    @Test
    void appendComputedStatsResetUnsetsSameFieldsAsEntityReset() {
        Update update = SurveyReportFieldUpdates.appendComputedStatsReset(new Update());
        Document updateObject = update.getUpdateObject();
        Document setDoc = updateObject.get("$set", Document.class);
        Document unsetDoc = updateObject.get("$unset", Document.class);

        assertNull(setDoc.get("is_verified"));
        assertEquals(0, setDoc.get("has_unknown_usage"));
        assertEquals(Set.copyOf(SurveyReportFieldUpdates.COMPUTED_STATS_UNSET_FIELDS), unsetDoc.keySet());
        assertFalse(setDoc.containsKey("is_parsed"));
        assertFalse(unsetDoc.containsKey("room_info_building_area_sum_from_ocr"));
    }

    @Test
    void applyComputedStatsResetIgnoresNull() {
        SurveyReportFieldUpdates.applyComputedStatsReset(null);
        SurveyReportInfo info = populatedSurvey();
        SurveyReportFieldUpdates.applyComputedStatsReset(info);
        assertNull(info.getIsVerified());
    }

    @Test
    void nullIsVerifiedIsNotValidationFailed() {
        SurveyReportInfo parsedUnverified = new SurveyReportInfo();
        parsedUnverified.setIsParsed(1);
        parsedUnverified.setIsVerified(null);
        assertFalse(parsedUnverified.isValidationFailed());

        SurveyReportInfo failed = new SurveyReportInfo();
        failed.setIsParsed(1);
        failed.setIsVerified(0);
        assertTrue(failed.isValidationFailed());
    }

    private static SurveyReportInfo populatedSurvey() {
        SurveyReportInfo info = new SurveyReportInfo();
        info.setIsParsed(1);
        info.setIsVerified(1);
        info.setVerificationErrorReason("旧原因");
        info.setHasUnknownUsage(1);
        info.setUnknownUsages("{\"101\":\"科技馆\"}");
        info.setUnknownUsageCount(1);
        info.setPendingConfirmArea(new BigDecimal("1"));
        info.setActualTotalBuildingArea(new BigDecimal("100"));
        info.setActualResidentialArea(new BigDecimal("80"));
        info.setActualCommercialArea(new BigDecimal("10"));
        info.setActualManagementRoomArea(new BigDecimal("2"));
        info.setActualOtherBuildableArea(new BigDecimal("1"));
        info.setActualCommunityArea(new BigDecimal("3"));
        info.setActualOtherPublicArea(new BigDecimal("4"));
        info.setTotalBuildableArea(new BigDecimal("93"));
        info.setTotalNonBuildableArea(new BigDecimal("7"));
        info.setRoomInfoBuildingAreaSum(new BigDecimal("100"));
        info.setRoomInfoInnerAreaSum(new BigDecimal("90"));
        info.setRoomInfoBalconyAreaSum(new BigDecimal("5"));
        info.setRoomInfoSharedAreaSum(new BigDecimal("5"));
        info.setRoomInfoBuildingAreaSumFromOcr(new BigDecimal("99.5"));
        info.setRoomInfoInnerAreaSumFromOcr(new BigDecimal("88.8"));
        info.setBuildingName("1号楼");
        return info;
    }
}
