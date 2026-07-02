package com.gov.landcheck.file.task.processor.receiver.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.util.StringUtils;

import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.task.processor.receiver.parse.SurveyHeaderLabelSynonyms.HeaderField;

import lombok.Getter;

/**
 * 按页扫描 HTML 表格，提取实测报告头部 label -> value 候选。
 */
public final class SurveyLabelTableScanner {

    /**
     * @deprecated 请使用 {@link #HEADER_COVER_MAX_PAGES} 与 {@link #scanHeaderFields}
     */
    @Deprecated
    public static final int DEFAULT_MAX_PAGES = 3;

    /** 封面/声明等前部有效页扫描上限（跳过 OCR 空页） */
    public static final int HEADER_COVER_MAX_PAGES = 5;

    private static final Pattern LATEX_TEXT_FRAGMENT = Pattern.compile(
            "\\\\underline\\{\\s*\\\\text\\{([^}]+)\\}");

    private SurveyLabelTableScanner() {
    }

    @Getter
    public static final class Candidate {
        private final HeaderField field;
        private final String value;
        private final int sourcePage;
        private final String labelHit;
        private final String source;

        public Candidate(HeaderField field, String value, int sourcePage, String labelHit, String source) {
            this.field = field;
            this.value = value;
            this.sourcePage = sourcePage;
            this.labelHit = labelHit;
            this.source = source;
        }
    }

    /**
     * 头部字段综合扫描：前部有效页 + 勘测成果表页 + 全页告知书专项扫描。
     */
    public static List<Candidate> scanHeaderFields(List<OCRPageResult> pages) {
        List<Candidate> candidates = new ArrayList<>();
        if (pages == null || pages.isEmpty()) {
            return candidates;
        }

        int coverScanned = 0;
        for (OCRPageResult page : pages) {
            if (coverScanned >= HEADER_COVER_MAX_PAGES) {
                break;
            }
            if (!isMeaningfulPage(page)) {
                continue;
            }
            int pageNum = resolvePageNumber(page, coverScanned);
            String text = page.getMarkdownText();
            candidates.addAll(scanPageHtml(text, pageNum, null));
            candidates.addAll(scanRegexFromText(text, pageNum));
            coverScanned++;
        }

        for (int i = 0; i < pages.size(); i++) {
            OCRPageResult page = pages.get(i);
            if (!isMeaningfulPage(page)) {
                continue;
            }
            String text = page.getMarkdownText();
            if (!ParseConstants.SURVEY_CONCLUSION_TITLE_PATTERN.matcher(text).find()) {
                continue;
            }
            int pageNum = resolvePageNumber(page, i);
            candidates.addAll(scanPageHtml(text, pageNum, null));
            candidates.addAll(scanRegexFromText(text, pageNum));
        }

        candidates.addAll(scanNoticeAcrossAllPages(pages));
        return candidates;
    }

    /**
     * 告知书编号常出现在楼盘表附件页（第 10 页以后），需全页扫描且不得误用受理编号。
     */
    static List<Candidate> scanNoticeAcrossAllPages(List<OCRPageResult> pages) {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            OCRPageResult page = pages.get(i);
            if (!isMeaningfulPage(page)) {
                continue;
            }
            int pageNum = resolvePageNumber(page, i);
            String text = page.getMarkdownText();
            candidates.addAll(scanPageHtml(text, pageNum, HeaderField.PROPERTY_AREA_CONFIRMATION_NOTICE));
            candidates.addAll(scanNoticeRegexFromText(text, pageNum));
        }
        return candidates;
    }

    public static List<Candidate> scanPages(List<OCRPageResult> pages, int maxPages) {
        List<Candidate> candidates = new ArrayList<>();
        if (pages == null || pages.isEmpty()) {
            return candidates;
        }
        int limit = maxPages <= 0 ? HEADER_COVER_MAX_PAGES : maxPages;
        int scanned = 0;
        for (OCRPageResult page : pages) {
            if (!isMeaningfulPage(page)) {
                continue;
            }
            if (scanned >= limit) {
                break;
            }
            int pageNum = resolvePageNumber(page, scanned);
            candidates.addAll(scanPageHtml(page.getMarkdownText(), pageNum, null));
            scanned++;
        }
        return candidates;
    }

    static List<Candidate> scanPageHtml(String html, int pageNum) {
        return scanPageHtml(html, pageNum, null);
    }

    static List<Candidate> scanPageHtml(String html, int pageNum, HeaderField onlyField) {
        List<Candidate> candidates = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(html);
            for (Element table : doc.select("table")) {
                for (Element row : table.select("tr")) {
                    Elements cells = row.select("td, th");
                    for (int i = 0; i < cells.size(); i++) {
                        Element cell = cells.get(i);
                        String cellText = cell.text().trim();
                        HeaderField field = SurveyHeaderLabelSynonyms.matchField(cellText);
                        if (field == null || (onlyField != null && field != onlyField)) {
                            continue;
                        }
                        String value = extractAdjacentValue(cells, i, cell);
                        value = resolveFieldValue(field, cleanExtractedValue(value));
                        if (value != null) {
                            candidates.add(new Candidate(field, value, pageNum, cellText, "TABLE"));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 单页扫描失败不影响其他页
        }
        return candidates;
    }

    private static String extractAdjacentValue(Elements cells, int labelIndex, Element labelCell) {
        if (labelIndex + 1 < cells.size()) {
            String adjacent = cells.get(labelIndex + 1).text().trim();
            if (StringUtils.hasText(adjacent)) {
                return adjacent;
            }
        }
        HeaderField field = SurveyHeaderLabelSynonyms.matchField(labelCell.text().trim());
        if (field == HeaderField.BUILDING_NAME) {
            Matcher inline = ParseConstants.BUILDING_LOCATION_INLINE_PATTERN.matcher(labelCell.html());
            if (inline.find()) {
                return inline.group(1).trim();
            }
        }
        if (field == HeaderField.PROPERTY_CERTIFICATE) {
            Matcher cert = ParseConstants.PROPERTY_CERTIFICATE_INLINE_PATTERN.matcher(labelCell.html());
            if (cert.find()) {
                return cert.group(1).trim();
            }
        }
        return null;
    }

    public static List<Candidate> scanRegexFromText(String text, int defaultPage) {
        List<Candidate> candidates = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return candidates;
        }
        collectRegexMatches(text, ParseConstants.REAL_ESTATE_SURVEY_REPORT_PATTERN,
                HeaderField.REAL_ESTATE_SURVEY_REPORT, defaultPage, "分栋报告编号", candidates);
        collectRegexMatches(text, ParseConstants.BUILDING_LOCATION_LATEX_PATTERN,
                HeaderField.BUILDING_NAME, defaultPage, "房屋坐落", candidates);
        collectRegexMatches(text, ParseConstants.BUILDING_LOCATION_PATTERN,
                HeaderField.BUILDING_NAME, defaultPage, "房屋坐落", candidates);
        collectRegexMatches(text, ParseConstants.BUILDING_LOCATION_INLINE_PATTERN,
                HeaderField.BUILDING_NAME, defaultPage, "房屋坐落", candidates);
        collectRegexMatches(text, ParseConstants.PROPERTY_CERTIFICATE_ADJACENT_CELL_PATTERN,
                HeaderField.PROPERTY_CERTIFICATE, defaultPage, "土地权属来源证明材料", candidates);
        collectRegexMatches(text, ParseConstants.PROPERTY_CERTIFICATE_PATTERN,
                HeaderField.PROPERTY_CERTIFICATE, defaultPage, "土地权属来源证明材料", candidates);
        collectRegexMatches(text, ParseConstants.PROPERTY_CERTIFICATE_INLINE_PATTERN,
                HeaderField.PROPERTY_CERTIFICATE, defaultPage, "土地权属来源证明材料", candidates);
        return candidates;
    }

    static List<Candidate> scanNoticeRegexFromText(String text, int defaultPage) {
        List<Candidate> candidates = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return candidates;
        }
        collectRegexMatches(text, ParseConstants.PROPERTY_AREA_CONFIRMATION_NOTICE_PATTERN,
                HeaderField.PROPERTY_AREA_CONFIRMATION_NOTICE, defaultPage, "确认告知书编号", candidates);
        collectRegexMatches(text, ParseConstants.PROPERTY_NOTICE_NUMBER_FALLBACK_PATTERN,
                HeaderField.PROPERTY_AREA_CONFIRMATION_NOTICE, defaultPage, "长住建房字", candidates);
        return candidates;
    }

    private static void collectRegexMatches(String text, Pattern pattern,
            HeaderField field, int page, String labelHit, List<Candidate> candidates) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = resolveFieldValue(field, cleanExtractedValue(matcher.group(1)));
            if (value != null) {
                candidates.add(new Candidate(field, value, page, labelHit, "REGEX"));
            }
        }
    }

    /**
     * 从 OCR 原文中提取标准告知书编号（容忍尾部备注、空格等杂质）。
     */
    static String extractNoticeNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (ParseConstants.NOTICE_REJECT_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        Matcher matcher = ParseConstants.PROPERTY_NOTICE_NUMBER_FALLBACK_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    static boolean isPlausibleNoticeValue(String value) {
        return extractNoticeNumber(value) != null;
    }

    private static String resolveFieldValue(HeaderField field, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (field == HeaderField.PROPERTY_AREA_CONFIRMATION_NOTICE) {
            return extractNoticeNumber(value);
        }
        return value.trim();
    }

    static boolean isPlausibleFieldValue(HeaderField field, String value) {
        return resolveFieldValue(field, value) != null;
    }

    static String cleanExtractedValue(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String cleaned = value.trim();
        Matcher latex = LATEX_TEXT_FRAGMENT.matcher(cleaned);
        if (latex.find()) {
            return latex.group(1).trim();
        }
        cleaned = cleaned.replace("$", "").trim();
        if (cleaned.contains("\\underline") || cleaned.contains("\\text")) {
            return null;
        }
        return cleaned;
    }

    static boolean isMeaningfulPage(OCRPageResult page) {
        if (page == null || !StringUtils.hasText(page.getMarkdownText())) {
            return false;
        }
        String text = page.getMarkdownText().trim();
        return !text.contains(ParseConstants.OCR_EMPTY_PAGE_MARKER);
    }

    private static int resolvePageNumber(OCRPageResult page, int fallbackIndex) {
        if (page != null && page.getPageNumber() != null) {
            return page.getPageNumber();
        }
        return fallbackIndex + 1;
    }
}
