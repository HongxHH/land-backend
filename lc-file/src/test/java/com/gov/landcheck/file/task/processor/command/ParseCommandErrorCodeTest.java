package com.gov.landcheck.file.task.processor.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gov.landcheck.file.task.base.TaskException;

class ParseCommandErrorCodeTest {

    private ParseCommand parseCommand;
    private Method determineParseErrorCode;

    @BeforeEach
    void setUp() throws Exception {
        parseCommand = new ParseCommand();
        determineParseErrorCode = ParseCommand.class.getDeclaredMethod("determineParseErrorCode", Exception.class);
        determineParseErrorCode.setAccessible(true);
    }

    @Test
    void enforceCriticalOutputMessage_mapsToParseDataInvalid() throws Exception {
        IllegalStateException ex = new IllegalStateException("实测报告未解析到户室面积对照表明细");

        TaskException.ErrorCode code = (TaskException.ErrorCode) determineParseErrorCode.invoke(parseCommand, ex);

        assertEquals(TaskException.ErrorCode.PARSE_DATA_INVALID, code);
    }

    @Test
    void emptyContractMessage_mapsToParseDataInvalid() throws Exception {
        IllegalStateException ex = new IllegalStateException("合同解析结果为空，判定为不可恢复错误");

        TaskException.ErrorCode code = (TaskException.ErrorCode) determineParseErrorCode.invoke(parseCommand, ex);

        assertEquals(TaskException.ErrorCode.PARSE_DATA_INVALID, code);
    }

    @Test
    void timeoutMessage_mapsToParseTimeout() throws Exception {
        Exception ex = new RuntimeException("connection time out while parsing");

        TaskException.ErrorCode code = (TaskException.ErrorCode) determineParseErrorCode.invoke(parseCommand, ex);

        assertEquals(TaskException.ErrorCode.PARSE_TIMEOUT, code);
    }
}
