package com.gov.landcheck.file.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.gov.landcheck.core.audit.FileOperationAuthorization;
import com.gov.landcheck.core.audit.OperatorContext;
import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.common.MessageConstant;
import com.gov.landcheck.file.dto.SubmitParseResult;
import com.gov.landcheck.file.service.parse.FileParseSubmissionService;

class FileServiceImplTest {

    private static final String FILE_ID = "42";

    private FileServiceImpl fileService;
    private MongoTemplate mongoTemplate;
    private FileParseSubmissionService fileParseSubmissionService;

    @BeforeEach
    void setUp() {
        fileService = new FileServiceImpl();
        mongoTemplate = mock(MongoTemplate.class);
        fileParseSubmissionService = mock(FileParseSubmissionService.class);
        ReflectionTestUtils.setField(fileService, "mongoTemplate", mongoTemplate);
        ReflectionTestUtils.setField(fileService, "fileParseSubmissionService", fileParseSubmissionService);
    }

    @AfterEach
    void tearDown() {
        OperatorContext.clear();
    }

    @Test
    void parseFile_whenOperatorDoesNotOwnFile_rejectsWithoutSubmittingParse() {
        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(42L);
        fileRecord.setUploadUserId(100L);
        when(mongoTemplate.findById(FILE_ID, FileRecord.class)).thenReturn(fileRecord);
        OperatorContext.setOperator(200L, "other-user");

        AjaxJson result = fileService.parseFile(FILE_ID);

        assertEquals(MessageConstant.PARAMS_ERROR_CODE, result.getCode());
        assertEquals(FileOperationAuthorization.denyReasonForFileMutate(), result.getMsg());
        verify(fileParseSubmissionService, never()).submitParseIfEligible(fileRecord);
    }

    @Test
    void parseFile_whenOperatorOwnsFile_submitsParse() {
        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(42L);
        fileRecord.setUploadUserId(100L);
        when(mongoTemplate.findById(FILE_ID, FileRecord.class)).thenReturn(fileRecord);
        when(fileParseSubmissionService.submitParseIfEligible(fileRecord))
                .thenReturn(SubmitParseResult.success("task-1"));
        OperatorContext.setOperator(100L, "owner");

        AjaxJson result = fileService.parseFile(FILE_ID);

        assertEquals(AjaxJson.CODE_SUCCESS, result.getCode());
        verify(fileParseSubmissionService).submitParseIfEligible(fileRecord);
    }
}
