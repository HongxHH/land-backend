package com.gov.landcheck.file.task.processor.receiver.parse;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.landcheck.core.bo.entity.CapacityIndicatorInfo;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.dto.ParseResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 容量指标核查表：解析多模态 JSON，提取本次报建建筑面积三列数值。
 */
@Slf4j
@Service
public class CapacityIndicatorTableParser {

    private static final BigDecimal CONSISTENCY_TOLERANCE = new BigDecimal("0.01");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ParseResult parse(List<OCRPageResult> ocrPages, ParsedDataHeader header) throws Exception {
        long start = System.currentTimeMillis();
        CapacityIndicatorInfo info = new CapacityIndicatorInfo();

        if (ocrPages != null) {
            for (OCRPageResult page : ocrPages) {
                if (page == null || !Boolean.TRUE.equals(page.getSuccess())) {
                    continue;
                }
                String raw = page.getMarkdownText();
                if (!StringUtils.hasText(raw)) {
                    continue;
                }
                try {
                    JsonNode root = objectMapper.readTree(stripJsonFence(raw.trim()));
                    info.setTotalArea(firstNonNull(info.getTotalArea(), decimal(root, "total_area")));
                    info.setCommercialArea(firstNonNull(info.getCommercialArea(), decimal(root, "commercial_area")));
                    info.setResidentialArea(firstNonNull(info.getResidentialArea(), decimal(root, "residential_area")));
                } catch (Exception ex) {
                    log.warn("容量指标核查表单页JSON解析失败: page={}, err={}", page.getPageNumber(), ex.getMessage());
                    throw new IllegalStateException(
                            "容量指标核查解析失败：页 " + page.getPageNumber() + " JSON 无法解析", ex);
                }
                break;
            }
        }

        if (info.getTotalArea() == null && info.getCommercialArea() == null && info.getResidentialArea() == null) {
            throw new IllegalStateException("容量指标核查解析失败：未提取到任何面积字段");
        }

        warnIfInconsistent(info);

        ParseResult result = new ParseResult(Collections.emptyList(), Collections.emptyList());
        result.setCapacityIndicatorInfo(info);
        result.setParseEngine("LLM_VISION");
        result.setProcessingTimeMs(System.currentTimeMillis() - start);
        return result;
    }

    private void warnIfInconsistent(CapacityIndicatorInfo info) {
        BigDecimal total = info.getTotalArea();
        BigDecimal commercial = info.getCommercialArea();
        BigDecimal residential = info.getResidentialArea();
        if (total == null || commercial == null || residential == null) {
            return;
        }
        BigDecimal sum = commercial.add(residential);
        BigDecimal diff = total.subtract(sum).abs();
        if (diff.compareTo(CONSISTENCY_TOLERANCE) > 0) {
            log.warn("容量指标核查面积一致性偏差: total={}, commercial+residential={}, diff={}",
                    total, sum, diff);
        }
    }

    private static BigDecimal firstNonNull(BigDecimal current, BigDecimal incoming) {
        return current != null ? current : incoming;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        if (v.isNumber()) {
            return v.decimalValue();
        }
        if (v.isTextual()) {
            String s = v.asText().trim();
            if (!StringUtils.hasText(s)) {
                return null;
            }
            try {
                return new BigDecimal(s.replace(",", "").replace("，", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String stripJsonFence(String s) {
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) {
                s = s.substring(firstNl + 1);
            }
            int end = s.lastIndexOf("```");
            if (end > 0) {
                s = s.substring(0, end);
            }
        }
        return s.trim();
    }
}
