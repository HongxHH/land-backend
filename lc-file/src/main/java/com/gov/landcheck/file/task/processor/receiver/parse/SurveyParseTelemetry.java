package com.gov.landcheck.file.task.processor.receiver.parse;

import java.math.BigDecimal;

import lombok.extern.slf4j.Slf4j;

/**
 * 实测报告解析结构化日志。
 */
@Slf4j
public final class SurveyParseTelemetry {

    private SurveyParseTelemetry() {
    }

    public static void fieldMiss(Long fileRecordId, String normalizedKey) {
        log.warn("survey_field_miss: fileRecordId={}, key={}", fileRecordId, normalizedKey);
    }

    public static void dedupRemoved(Long fileRecordId, int removedCount) {
        if (removedCount > 0) {
            log.info("room_table_dedup_removed: fileRecordId={}, count={}", fileRecordId, removedCount);
        }
    }

    public static void ocrTotalSelected(Long fileRecordId, String key, int candidateCount, BigDecimal chosenValue) {
        log.info("ocr_total_selected: fileRecordId={}, key={}, candidates={}, chosen={}",
                fileRecordId, key, candidateCount, chosenValue);
    }

    public static void llmFallback(Long fileRecordId, int page, String reason) {
        log.warn("room_table_llm_fallback: fileRecordId={}, page={}, reason={}", fileRecordId, page, reason);
    }

    public static void conclusionPages(Long fileRecordId, int startPage, int endPage, int length) {
        log.info("survey_conclusion_pages: fileRecordId={}, startPage={}, endPage={}, length={}",
                fileRecordId, startPage, endPage, length);
    }
}
