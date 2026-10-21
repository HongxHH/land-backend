package com.gov.landcheck.file.task.context;

import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.service.SurveyReportContractApprovalSyncService;
import com.gov.landcheck.core.service.UnknownUsageRecordService;
import com.gov.landcheck.file.task.processor.receiver.DataValidReceiver;
import com.gov.landcheck.file.task.processor.receiver.parse.CapacityIndicatorTableParser;
import com.gov.landcheck.file.task.processor.receiver.parse.ContractDataParser;
import com.gov.landcheck.file.task.processor.receiver.parse.PlanningReviewTableParser;
import com.gov.landcheck.file.task.processor.receiver.parse.ProjectPartySummaryExcelParser;
import com.gov.landcheck.file.task.processor.receiver.parse.RoomTableParser;
import com.gov.landcheck.file.task.processor.receiver.parse.SurveyDataParser;

public final class FileParseModulesFixture {

    private FileParseModulesFixture() {
    }

    public static FileParseModuleRegistry registry() {
        return new FileParseModuleRegistry(modules());
    }

    public static FileParseModuleRegistry registryWithSurvey(MongoTemplate mongoTemplate) {
        List<FileParseModule> wired = new ArrayList<>(modules());
        wired.removeIf(module -> module.type() == FileContextType.SURVEY_REPORT);
        wired.add(surveyModule(mongoTemplate));
        return new FileParseModuleRegistry(wired);
    }

    public static SurveyReportParseModule surveyModule(MongoTemplate mongoTemplate) {
        return new SurveyReportParseModule(
                mock(SurveyDataParser.class),
                mock(RoomTableParser.class),
                mongoTemplate,
                new FillChangePublisher(null),
                mock(SurveyReportContractApprovalSyncService.class),
                mock(UnknownUsageRecordService.class),
                mock(DataValidReceiver.class));
    }

    public static List<FileParseModule> modules() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        FillChangePublisher publisher = new FillChangePublisher(null);
        SurveyReportContractApprovalSyncService sync = mock(SurveyReportContractApprovalSyncService.class);
        return List.of(
                new ContractParseModule(mock(ContractDataParser.class), mongoTemplate, publisher, sync),
                new SurveyReportParseModule(
                        mock(SurveyDataParser.class),
                        mock(RoomTableParser.class),
                        mongoTemplate,
                        publisher,
                        sync,
                        mock(UnknownUsageRecordService.class),
                        mock(DataValidReceiver.class)),
                new PlanningReviewParseModule(mock(PlanningReviewTableParser.class), mongoTemplate, publisher),
                new CapacityIndicatorParseModule(mock(CapacityIndicatorTableParser.class), mongoTemplate, publisher),
                new ProjectPartySummaryParseModule(
                        mock(ProjectPartySummaryExcelParser.class), mongoTemplate, publisher));
    }
}
