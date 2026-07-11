package com.gov.landcheck.file.task.processor.receiver.parse.excel;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.gov.landcheck.core.service.LLMService;

import jakarta.annotation.Resource;

/**
 * 项目方汇总抽取（仅负责调用统一 LLMService）。
 */
@Component
public class ProjectPartySummaryLlmExtractor {

    @Resource
    private LLMService llmService;

    public ExtractResult extract(String regionText, String projectId, Long fileRecordId) throws Exception {
        Map<String, Object> result = llmService.parseProjectPartySummary(regionText, projectId, fileRecordId);
        String prompt = result.get("prompt") == null ? "" : String.valueOf(result.get("prompt"));
        String response = result.get("response") == null ? "" : String.valueOf(result.get("response"));
        return new ExtractResult(prompt, response);
    }

    public record ExtractResult(String prompt, String response) {
    }
}
