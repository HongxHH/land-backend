package com.gov.landcheck.file.task.processor.receiver.parse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.task.processor.receiver.parse.SurveyHeaderLabelSynonyms.HeaderField;
import com.gov.landcheck.file.task.processor.receiver.parse.SurveyLabelTableScanner.Candidate;

import lombok.extern.slf4j.Slf4j;

/**
 * 实测报告头部解析：按页表格优先抽取告知书/报告编号、建筑名称、不动产权证编号；勘测成果表块按页拼接。
 */
@Slf4j
@Component
public class SurveyDataParser {

    private static final HeaderField[] TRACKED_FIELDS = {
            HeaderField.PROPERTY_AREA_CONFIRMATION_NOTICE,
            HeaderField.REAL_ESTATE_SURVEY_REPORT,
            HeaderField.BUILDING_NAME,
            HeaderField.PROPERTY_CERTIFICATE
    };

    public List<ParsedDataItem> parseSurveyData(List<OCRPageResult> pages, ParsedDataHeader header) {
        List<ParsedDataItem> items = new ArrayList<>();
        Long fileRecordId = header != null ? header.getFileRecordId() : null;

        List<Candidate> allCandidates = SurveyLabelTableScanner.scanHeaderFields(pages);

        for (HeaderField field : TRACKED_FIELDS) {
            Candidate best = selectBestCandidate(allCandidates, field);
            if (best != null) {
                items.add(toDataItem(best, header));
            } else {
                SurveyParseTelemetry.fieldMiss(fileRecordId, field.normalizedKey());
            }
        }
        return items;
    }

    private Candidate selectBestCandidate(List<Candidate> candidates, HeaderField field) {
        Map<String, Integer> valueCounts = new HashMap<>();
        Map<String, Candidate> valueToCandidate = new HashMap<>();
        for (Candidate c : candidates) {
            if (c.getField() != field || !StringUtils.hasText(c.getValue())) {
                continue;
            }
            String value = c.getValue().trim();
            if (field == HeaderField.PROPERTY_AREA_CONFIRMATION_NOTICE) {
                value = SurveyLabelTableScanner.extractNoticeNumber(value);
                if (value == null) {
                    continue;
                }
            }
            valueCounts.merge(value, 1, Integer::sum);
            Candidate existing = valueToCandidate.get(value);
            if (existing == null || c.getSourcePage() < existing.getSourcePage()) {
                valueToCandidate.put(value, c);
            }
        }
        if (valueCounts.isEmpty()) {
            return null;
        }
        String bestValue = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : valueCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                bestValue = entry.getKey();
            } else if (entry.getValue() == maxCount && bestValue != null) {
                Candidate current = valueToCandidate.get(bestValue);
                Candidate challenger = valueToCandidate.get(entry.getKey());
                if (challenger != null && current != null
                        && challenger.getSourcePage() < current.getSourcePage()) {
                    bestValue = entry.getKey();
                }
            }
        }
        return bestValue != null ? valueToCandidate.get(bestValue) : null;
    }

    private ParsedDataItem toDataItem(Candidate candidate, ParsedDataHeader header) {
        String normalizedKey = candidate.getField().normalizedKey();
        String originalFieldName = ParseUtils.getOriginalFieldName(normalizedKey);
        String value = candidate.getValue();
        return ParseUtils.createDataItem(
                originalFieldName, value, null, "", normalizedKey, value,
                null, "SURVEY_INFO", candidate.getSourcePage(),
                candidate.getLabelHit() + "(" + candidate.getSource() + ")",
                ParseUtils.SOURCE_REGEX, ParseUtils.SOURCE_REGEX, header);
    }

    /**
     * 提取勘测成果表内容块（供户室表解析与用途填充使用）
     */
    public List<String> extractSurveyConclusion(List<OCRPageResult> pages, Long fileRecordId) {
        try {
            String assembled = SurveyConclusionAssembler.assemble(pages, fileRecordId);
            if (StringUtils.hasText(assembled)) {
                return List.of(assembled);
            }
            String combinedText = ParseUtils.combineAllPagesText(pages);
            List<String> fallback = SurveyConclusionAssembler.assembleFromCombinedText(combinedText);
            log.debug("提取到 {} 个勘测成果表内容（fallback）", fallback != null ? fallback.size() : 0);
            return fallback;
        } catch (Exception e) {
            log.warn("提取勘测成果表时发生错误: {}", e.getMessage());
            return null;
        }
    }
}
