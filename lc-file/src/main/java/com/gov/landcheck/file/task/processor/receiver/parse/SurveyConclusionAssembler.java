package com.gov.landcheck.file.task.processor.receiver.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import com.gov.landcheck.file.dto.OCRPageResult;

/**
 * 按页拼接勘测成果表 HTML 块（跨页合并）。
 */
public final class SurveyConclusionAssembler {

    private static final Pattern CONCLUSION_TITLE = Pattern.compile("勘\\s*测\\s*成\\s*果\\s*表");
    private static final Pattern ROOM_TABLE_TITLE = ParseConstants.ROOM_TABLE_TITLE_PATTERN;

    private SurveyConclusionAssembler() {
    }

    public static String assemble(List<OCRPageResult> pages, Long fileRecordId) {
        if (pages == null || pages.isEmpty()) {
            return null;
        }
        int startIdx = -1;
        for (int i = 0; i < pages.size(); i++) {
            OCRPageResult page = pages.get(i);
            if (page != null && StringUtils.hasText(page.getMarkdownText())
                    && CONCLUSION_TITLE.matcher(page.getMarkdownText()).find()) {
                startIdx = i;
                break;
            }
        }
        if (startIdx < 0) {
            return null;
        }

        StringBuilder merged = new StringBuilder();
        int endIdx = startIdx;
        int startPage = resolvePageNumber(pages.get(startIdx), startIdx);
        boolean blankStreak = false;

        for (int i = startIdx; i < pages.size(); i++) {
            OCRPageResult page = pages.get(i);
            String html = page != null ? page.getMarkdownText() : null;
            if (!StringUtils.hasText(html)) {
                if (i > startIdx) {
                    blankStreak = true;
                }
                if (blankStreak && i > startIdx) {
                    break;
                }
                continue;
            }
            if (i > startIdx && ROOM_TABLE_TITLE.matcher(html).find()) {
                break;
            }
            boolean hasConclusion = CONCLUSION_TITLE.matcher(html).find() || hasConclusionStructure(html);
            if (i > startIdx && !hasConclusion) {
                if (blankStreak) {
                    break;
                }
                blankStreak = true;
                continue;
            }
            blankStreak = false;
            merged.append(html).append("\n");
            endIdx = i;
        }

        String result = merged.toString().trim();
        if (result.isEmpty()) {
            return null;
        }
        int endPage = resolvePageNumber(pages.get(endIdx), endIdx);
        SurveyParseTelemetry.conclusionPages(fileRecordId, startPage, endPage, result.length());
        return result;
    }

    private static boolean hasConclusionStructure(String html) {
        return html.contains("用途") || html.contains("面积") || html.contains("层");
    }

    private static int resolvePageNumber(OCRPageResult page, int index) {
        if (page != null && page.getPageNumber() != null) {
            return page.getPageNumber();
        }
        return index + 1;
    }

    /** 无页对象时 regex 兜底 */
    public static List<String> assembleFromCombinedText(String combinedText) {
        List<String> blocks = new ArrayList<>();
        if (!StringUtils.hasText(combinedText)) {
            return null;
        }
        java.util.regex.Matcher matcher = ParseConstants.SURVEY_CONCLUSION_PATTERN.matcher(combinedText);
        while (matcher.find()) {
            String content = matcher.group(0);
            if (StringUtils.hasText(content)) {
                blocks.add(content.trim());
            }
        }
        return blocks.isEmpty() ? null : blocks;
    }
}
