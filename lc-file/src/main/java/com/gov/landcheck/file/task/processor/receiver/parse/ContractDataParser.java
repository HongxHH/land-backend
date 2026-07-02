package com.gov.landcheck.file.task.processor.receiver.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.core.service.LLMService;

import lombok.extern.slf4j.Slf4j;

/**
 * 合同类文件解析：通过大模型从 OCR 文本中抽取合同编号、出让方、受让方（单次调用）
 */
@Slf4j
@Component
public class ContractDataParser {

    private static final String KEY_CONTRACT_NUMBER_CN = "合同编号";
    private static final String KEY_TRANSFEROR_CN = "出让方";
    private static final String KEY_TRANSFEREE_CN = "受让方";

    @Autowired
    private LLMService llmService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ParsedDataItem> parseContractData(String text, ParsedDataHeader header) throws Exception {
        List<ParsedDataItem> items = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalStateException("合同OCR文本为空，无法解析");
        }

        String projectId = header.getProjectId() != null ? header.getProjectId().toString() : "unknown";
        Long fileRecordId = header.getFileRecordId();
        Map<String, Object> result = llmService.parseContract(text, projectId, fileRecordId);

        String response = (String) result.get("response");
        String prompt = (String) result.get("prompt");
        if (prompt != null)
            header.setModelPrompt(prompt);
        if (response != null)
            header.setModelAnalysisResult(response);

        if (response == null || response.trim().isEmpty()) {
            throw new IllegalStateException("合同解析大模型返回为空");
        }

        Map<String, String> parsed = parseContractResponse(response);
        if (parsed.isEmpty()) {
            throw new IllegalStateException("合同解析无法从模型响应中提取结构化JSON");
        }

        addItemIfPresent(items, header, "contract_number", ParseUtils.getOriginalFieldName("contract_number"),
                parsed.get(KEY_CONTRACT_NUMBER_CN));
        addItemIfPresent(items, header, "transferor", ParseUtils.getOriginalFieldName("transferor"),
                parsed.get(KEY_TRANSFEROR_CN));
        addItemIfPresent(items, header, "transferee", ParseUtils.getOriginalFieldName("transferee"),
                parsed.get(KEY_TRANSFEREE_CN));

        if (items.isEmpty()) {
            throw new IllegalStateException("合同解析未产出任何字段");
        }
        for (ParsedDataItem item : items) {
            log.info("字段: {}, 值: {}, 页码: {}, 位置: {}",
                    item.getFieldKey(), item.getFieldValue(), item.getSourcePage(), item.getSourcePosition());
        }
        return items;
    }

    /**
     * 从模型响应中解析出 合同编号、出让方、受让方（兼容中文 key）
     */
    private Map<String, String> parseContractResponse(String response) {
        Map<String, String> out = new java.util.HashMap<>();
        if (response == null)
            return out;
        try {
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}') + 1;
            if (start < 0 || end <= start)
                return out;
            String jsonStr = response.substring(start, end);
            JsonNode root = objectMapper.readTree(jsonStr);
            if (root.has(KEY_CONTRACT_NUMBER_CN))
                out.put(KEY_CONTRACT_NUMBER_CN, nullToEmpty(root.get(KEY_CONTRACT_NUMBER_CN).asText()).trim());
            if (root.has(KEY_TRANSFEROR_CN))
                out.put(KEY_TRANSFEROR_CN, nullToEmpty(root.get(KEY_TRANSFEROR_CN).asText()).trim());
            if (root.has(KEY_TRANSFEREE_CN))
                out.put(KEY_TRANSFEREE_CN, nullToEmpty(root.get(KEY_TRANSFEREE_CN).asText()).trim());
        } catch (Exception e) {
            log.warn("解析合同JSON失败: {}", e.getMessage());
        }
        return out;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private void addItemIfPresent(List<ParsedDataItem> items, ParsedDataHeader header,
            String normalizedKey, String displayKey, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        ParsedDataItem item = ParseUtils.createDataItem(
                displayKey, value, null, "", normalizedKey, value,
                null, "CONTRACT_INFO", 1, "大模型抽取",
                ParseUtils.SOURCE_LLM, ParseUtils.SOURCE_LLM, header);
        items.add(item);
    }
}
