package com.gov.landcheck.file.task.context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ProjectPartyDeclaredTotals;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.task.base.TaskData;

class ProjectPartySummaryParseModuleSkipTest {

    @Test
    void skipPartialPartySummaryWithoutTotals() {
        TaskData taskData = partySummaryTask("PARTIAL", null);
        assertTrue(ProjectPartySummaryParseModule.shouldSkipFillWithoutTotals(taskData));
    }

    @Test
    void doNotSkipWhenTotalsPresent() {
        ProjectPartyDeclaredTotals totals = new ProjectPartyDeclaredTotals();
        totals.setContractAgreedTotalBuildingArea(java.math.BigDecimal.TEN);
        TaskData taskData = partySummaryTask("PARTIAL", totals);
        assertFalse(ProjectPartySummaryParseModule.shouldSkipFillWithoutTotals(taskData));
    }

    @Test
    void doNotSkipSurveyReport() {
        FileRecord fileRecord = new FileRecord();
        fileRecord.setFileContextType(FileContextType.SURVEY_REPORT);
        TaskData taskData = new TaskData(fileRecord);
        assertFalse(ProjectPartySummaryParseModule.shouldSkipFillWithoutTotals(taskData));
    }

    private static TaskData partySummaryTask(String parseStatus, ProjectPartyDeclaredTotals totals) {
        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(1L);
        fileRecord.setFileContextType(FileContextType.PROJECT_PARTY_SURVEY_SUMMARY);
        ProjectPartySurveySummaryForm form = new ProjectPartySurveySummaryForm();
        form.setParseStatus(parseStatus);
        form.setDeclaredTotals(totals);
        TaskData taskData = new TaskData(fileRecord);
        taskData.setProjectPartySummaryForm(form);
        return taskData;
    }
}
