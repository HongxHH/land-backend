package com.gov.landcheck.file.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.file.service.ITaskExecuteService;

class FileControllerTest {

    @Test
    void parseJobFlowReturnsForbiddenWhenServiceRejectsAccess() {
        FileController controller = new FileController();
        ITaskExecuteService taskExecuteService = mock(ITaskExecuteService.class);
        ReflectionTestUtils.setField(controller, "taskExecuteService", taskExecuteService);
        when(taskExecuteService.getParseJobFlowDetail(42L))
                .thenThrow(new SecurityException("无权查看解析流水线"));

        AjaxJson response = controller.getParseJobFlow(42L);

        assertThat(response.getCode()).isEqualTo(AjaxJson.CODE_NOT_JUR);
        assertThat(response.getMsg()).isEqualTo("无权查看解析流水线");
    }
}
