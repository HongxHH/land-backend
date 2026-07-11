package com.gov.landcheck.file.task.processor.receiver.parse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.file.dto.OCRPageResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 解析用工具方法：文本合并、清理、数值解析、创建 ParsedDataItem 等
 */
@Slf4j
public final class ParseUtils {

    private ParseUtils() {}

    public static final BigDecimal DEFAULT_CONFIDENCE = BigDecimal.valueOf(0.85);

    /** 提取方法/数据来源：正则或 OCR */
    public static final String SOURCE_REGEX = "REGEX";
    /** 提取方法/数据来源：大模型 */
    public static final String SOURCE_LLM = "LLM";

    /** 合同解析时发送给大模型的最大页数 */
    public static final int CONTRACT_MAX_PAGES_FOR_LLM = 20;

    /**
     * 合并所有页面的 OCR 文本
     */
    public static String combineAllPagesText(List<OCRPageResult> ocrResults) {
        return combinePagesText(ocrResults, Integer.MAX_VALUE);
    }

    /**
     * 合并前 N 页的 OCR 文本（用于合同解析等只取前若干页的场景）
     */
    public static String combineFirst20PagesText(List<OCRPageResult> ocrResults) {
        return combinePagesText(ocrResults, CONTRACT_MAX_PAGES_FOR_LLM);
    }

    private static String combinePagesText(List<OCRPageResult> ocrResults, int maxPages) {
        if (ocrResults == null) return "";
        StringBuilder combinedText = new StringBuilder();
        int count = 0;
        for (OCRPageResult result : ocrResults) {
            if (count >= maxPages) break;
            String pageText = result.getMarkdownText();
            if (pageText != null && !pageText.trim().isEmpty()) {
                combinedText.append("\n===PAGE_BREAK===\n");
                combinedText.append(pageText);
                combinedText.append("\n");
                count++;
            }
        }
        return combinedText.toString();
    }

    public static String cleanText(String text) {
        return text != null ? text.trim() : "";
    }

    public static String extractNumericString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        return text.replaceAll("[^\\d.-]", "");
    }

    public static BigDecimal parseAreaValue(String text) {
        String numericStr = extractNumericString(text);
        if (numericStr.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(numericStr);
        } catch (NumberFormatException e) {
            log.warn("无法解析面积值: {}", text);
            return null;
        }
    }

    /**
     * 从字符串列表中取出现次数最多的项
     */
    public static String getMostCommonItem(List<String> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        Map<String, Integer> counter = new java.util.HashMap<>();
        for (String item : items) {
            if (item != null && !item.trim().isEmpty()) {
                String trimmed = item.trim();
                counter.put(trimmed, counter.getOrDefault(trimmed, 0) + 1);
            }
        }
        if (counter.isEmpty()) {
            return null;
        }
        String mostCommon = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : counter.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mostCommon = entry.getKey();
            }
        }
        return mostCommon;
    }

    public static String determineFieldType(BigDecimal valueNumber) {
        return valueNumber != null ? "NUMBER" : "STRING";
    }

    /**
     * 创建解析数据项
     *
     * @param extractionMethod 提取方法（如 REGEX / LLM）
     * @param dataSource        数据来源（如 REGEX / LLM）
     */
    public static ParsedDataItem createDataItem(
            String fieldKey, String fieldValue, BigDecimal valueNumber,
            String valueUnit, String normalizedKey, String normalizedValue,
            BigDecimal normalizedNumber, String dataCategory, int sourcePage,
            String sourcePosition, String extractionMethod, String dataSource,
            ParsedDataHeader header) {
        ParsedDataItem item = new ParsedDataItem();
        item.setHeaderId(header.getId());
        item.setDataCategory(dataCategory);
        item.setFieldKey(fieldKey);
        item.setFieldValue(fieldValue);
        item.setValueNumber(valueNumber);
        item.setValueUnit(valueUnit);
        item.setNormalizedKey(normalizedKey);
        item.setNormalizedValue(normalizedValue);
        item.setNormalizedNumber(normalizedNumber);
        item.setSourcePage(sourcePage);
        item.setSourcePosition(sourcePosition);
        item.setFieldType(determineFieldType(valueNumber != null ? valueNumber : normalizedNumber));
        item.setExtractionMethod(extractionMethod);
        item.setDataSource(dataSource);
        item.setConfidenceScore(DEFAULT_CONFIDENCE);
        return item;
    }

    public static String getOriginalFieldName(String normalizedKey) {
        return ParseConstants.getFieldNameMapping().getOrDefault(normalizedKey, normalizedKey);
    }
}
