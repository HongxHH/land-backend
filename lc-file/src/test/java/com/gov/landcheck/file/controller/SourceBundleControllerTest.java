package com.gov.landcheck.file.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.OutputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gov.landcheck.file.bundle.SourceBundleDownloadGate;
import com.gov.landcheck.file.bundle.SourceBundlePlan;
import com.gov.landcheck.file.bundle.SourceBundleService;
import com.gov.landcheck.file.bundle.SourceBundleTicketStore;

@ExtendWith(MockitoExtension.class)
class SourceBundleControllerTest {

    @Mock
    private SourceBundleService sourceBundleService;

    private final SourceBundleTicketStore ticketStore = new SourceBundleTicketStore();
    private final SourceBundleDownloadGate downloadGate = new SourceBundleDownloadGate();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SourceBundleController controller = new SourceBundleController(sourceBundleService, ticketStore, downloadGate);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void downloadWritesZipBytesAndReleasesTheGate() throws Exception {
        byte[] zip = new byte[] { 'P', 'K', 3, 4 };
        SourceBundlePlan plan = new SourceBundlePlan(SourceBundlePlan.Status.READY, "示范项目", "示范项目-原件.zip", 1, 2,
                0, zip.length, null, List.of());
        when(sourceBundleService.plan(70L)).thenReturn(plan);
        doAnswer(invocation -> {
            OutputStream output = invocation.getArgument(1);
            output.write(zip);
            return null;
        }).when(sourceBundleService).write(eq(plan), any());

        String ticket = ticketStore.issue(3L, "审核员", 70L);
        MvcResult started = mockMvc.perform(get("/file/project/70/source-bundle").param("ticket", ticket))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(content().bytes(zip));
        assertFalse(downloadGate.isBusy(3L));
    }

    @Test
    void planningFailureReleasesTheGate() throws Exception {
        when(sourceBundleService.plan(70L)).thenThrow(new IllegalStateException("读取原件大小失败，请稍后再试"));
        String ticket = ticketStore.issue(3L, "审核员", 70L);

        MvcResult started = mockMvc.perform(get("/file/project/70/source-bundle").param("ticket", ticket))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started)).andExpect(status().isInternalServerError());
        assertFalse(downloadGate.isBusy(3L));
    }
}
