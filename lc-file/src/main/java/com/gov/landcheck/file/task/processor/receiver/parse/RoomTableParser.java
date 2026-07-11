package com.gov.landcheck.file.task.processor.receiver.parse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.service.LLMService;
import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.dto.RoomTableParseResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 户室面积对照表解析：按页扫描 HTML 表格（表头锚定）、合计行提取，必要时 LLM 单页兜底。
 */
@Slf4j
@Component
public class RoomTableParser {

    private static final int PAGE_SCORE_THRESHOLD = 8;
    private static final BigDecimal TOTAL_AREA_TOLERANCE = new BigDecimal("0.05");

    private static final String[] HEADER_KEYWORDS = {
            "层次", "户室号", "建筑面积", "套内面积", "阳台面积",
            "分摊面积", "结构", "用途", "备注", "楼层", "房间号"
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SurveyDataParser surveyDataParser;
    @Autowired
    private RoomUsageLlmFiller roomUsageLlmFiller;
    @Autowired
    private LLMService llmService;

    public RoomTableParseResult parseRoomTable(List<OCRPageResult> pages, FileRecord fileRecord,
            ParsedDataHeader header) throws Exception {
        List<RoomInfo> roomInfos = new ArrayList<>();
        List<ParsedDataItem> totalItems = new ArrayList<>();
        boolean hasUsageColumn = false;
        boolean parseUsedLlm = false;
        boolean roomTablePartialLlm = false;
        boolean usageFilledByLlm = false;
        Long fileRecordId = fileRecord != null ? fileRecord.getId() : null;

        try {
            List<String> surveyConclusions = surveyDataParser.extractSurveyConclusion(pages, fileRecordId);

            if (pages != null) {
                for (OCRPageResult page : pages) {
                    if (page == null || !StringUtils.hasText(page.getMarkdownText())) {
                        continue;
                    }
                    Document doc = Jsoup.parse(page.getMarkdownText());
                    for (Element table : doc.select("table")) {
                        if (!isRoomAreaTable(table)) {
                            continue;
                        }
                        boolean usage = parseTable(table, fileRecord, header, roomInfos, totalItems);
                        if (usage) {
                            hasUsageColumn = true;
                        }
                    }
                }
            }

            RoomTablePostProcessor.DedupResult dedup = RoomTablePostProcessor.deduplicateRooms(roomInfos);
            roomInfos = new ArrayList<>(dedup.getRooms());
            SurveyParseTelemetry.dedupRemoved(fileRecordId, dedup.getRemovedCount());

            OCRPageResult fallbackPage = findBestCandidatePage(pages);
            int bestPageScore = fallbackPage != null ? scorePage(fallbackPage.getMarkdownText()) : 0;

            if (roomInfos.isEmpty()) {
                if (fallbackPage != null && bestPageScore >= PAGE_SCORE_THRESHOLD) {
                    SurveyParseTelemetry.llmFallback(fileRecordId, fallbackPage.getPageNumber(), "empty");
                    if (tryLlmFallback(fallbackPage.getMarkdownText(), fileRecord, header, roomInfos, totalItems,
                            false)) {
                        parseUsedLlm = true;
                        hasUsageColumn = roomInfos.stream()
                                .anyMatch(r -> StringUtils.hasText(r.getRoomUsage()));
                    }
                }
            } else if (fallbackPage != null && bestPageScore >= PAGE_SCORE_THRESHOLD
                    && shouldTriggerPartialLlm(roomInfos, totalItems, bestPageScore)) {
                List<RoomInfo> backupRooms = new ArrayList<>(roomInfos);
                List<ParsedDataItem> backupTotals = new ArrayList<>(totalItems);
                roomInfos.clear();
                totalItems.clear();
                SurveyParseTelemetry.llmFallback(fileRecordId, fallbackPage.getPageNumber(), "partial");
                if (tryLlmFallback(fallbackPage.getMarkdownText(), fileRecord, header, roomInfos, totalItems, true)) {
                    parseUsedLlm = true;
                    roomTablePartialLlm = true;
                    hasUsageColumn = roomInfos.stream()
                            .anyMatch(r -> StringUtils.hasText(r.getRoomUsage()));
                } else {
                    roomInfos.addAll(backupRooms);
                    totalItems.addAll(backupTotals);
                }
            }

            totalItems = new ArrayList<>(RoomTablePostProcessor.selectBestTotals(totalItems, roomInfos));

            if (!hasUsageColumn && surveyConclusions != null && !surveyConclusions.isEmpty()) {
                String surveyText = String.join("\n", surveyConclusions);
                List<RoomInfo> beforeUsage = new ArrayList<>(roomInfos);
                roomInfos = roomUsageLlmFiller.fillRoomUsageFromSurveyConclusion(roomInfos, surveyText, header,
                        fileRecord);
                usageFilledByLlm = usageWasFilledByLlm(beforeUsage, roomInfos);
            }

            log.debug("成功解析户室面积对照表，共提取 {} 个房间信息和 {} 个合计信息，llmFallback={}, partialLlm={}",
                    roomInfos.size(), totalItems.size(), parseUsedLlm, roomTablePartialLlm);
        } catch (Exception e) {
            log.error("解析户室面积对照表失败: {}", e.getMessage(), e);
            throw new Exception("解析户室面积对照表失败: " + e.getMessage(), e);
        }

        RoomTableParseResult result = new RoomTableParseResult(roomInfos, totalItems);
        result.setParseUsedLlm(parseUsedLlm);
        result.setRoomTablePartialLlm(roomTablePartialLlm);
        result.setUsageFilledByLlm(usageFilledByLlm);
        return result;
    }

    private boolean usageWasFilledByLlm(List<RoomInfo> before, List<RoomInfo> after) {
        if (before == null || after == null || before.size() != after.size()) {
            return false;
        }
        for (int i = 0; i < before.size(); i++) {
            boolean beforeEmpty = !StringUtils.hasText(before.get(i).getRoomUsage());
            boolean afterFilled = StringUtils.hasText(after.get(i).getRoomUsage());
            if (beforeEmpty && afterFilled) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldTriggerPartialLlm(List<RoomInfo> roomInfos, List<ParsedDataItem> totalItems,
            int bestPageScore) {
        int expectedRows = Math.min(10, Math.max(0, bestPageScore - 8));
        if (expectedRows > 0 && roomInfos.size() < expectedRows) {
            return true;
        }
        BigDecimal ocrBuilding = findOcrBuildingTotal(totalItems);
        if (RoomTablePostProcessor.buildingTotalMismatch(roomInfos, ocrBuilding)) {
            SurveyParseTelemetry.llmFallback(null, 0, "total_mismatch");
            return true;
        }
        return false;
    }

    private BigDecimal findOcrBuildingTotal(List<ParsedDataItem> totalItems) {
        if (totalItems == null) {
            return null;
        }
        for (ParsedDataItem item : totalItems) {
            if ("room_info_building_area_sum_from_ocr".equals(item.getNormalizedKey())) {
                return item.getValueNumber();
            }
        }
        return null;
    }

    private OCRPageResult findBestCandidatePage(List<OCRPageResult> pages) {
        if (pages == null || pages.isEmpty()) {
            return null;
        }
        return pages.stream()
                .filter(p -> p != null && StringUtils.hasText(p.getMarkdownText()))
                .max(Comparator.comparingInt(p -> scorePage(p.getMarkdownText())))
                .orElse(null);
    }

    private int scorePage(String pageHtml) {
        if (!StringUtils.hasText(pageHtml)) {
            return 0;
        }
        int score = 0;
        if (ParseConstants.ROOM_TABLE_TITLE_PATTERN.matcher(pageHtml).find()) {
            score += 3;
        }
        Document doc = Jsoup.parse(pageHtml);
        int dataRows = 0;
        boolean hasTotalRow = false;
        for (Element table : doc.select("table")) {
            if (isRoomAreaTable(table)) {
                score += 5;
                Elements rows = table.select("tr");
                for (Element row : rows) {
                    Elements cells = row.select("td, th");
                    if (isDataRow(cells) && !"合计".equals(extractPlainLevel(cells))) {
                        dataRows++;
                    }
                    if ("合计".equals(extractPlainLevel(cells))) {
                        hasTotalRow = true;
                    }
                }
            }
        }
        score += Math.min(dataRows, 10);
        if (hasTotalRow) {
            score += 3;
        }
        if (doc.select("table").isEmpty() && pageHtml.contains("建筑面积")) {
            score -= 2;
        }
        return score;
    }

    private String extractPlainLevel(Elements cells) {
        if (cells == null || cells.isEmpty()) {
            return null;
        }
        return cells.get(0).text().trim();
    }

    private boolean isRoomAreaTable(Element table) {
        if (table == null) {
            return false;
        }
        Elements rows = table.select("tr");
        for (int i = 0; i < Math.min(5, rows.size()); i++) {
            Elements cells = rows.get(i).select("td, th");
            if (isAddressRow(cells)) {
                continue;
            }
            if (rowHasRoomHeaderFields(cells) && hasSubsequentDataRows(rows, i + 1)) {
                return true;
            }
        }
        return false;
    }

    private boolean rowHasRoomHeaderFields(Elements cells) {
        if (cells == null || cells.isEmpty()) {
            return false;
        }
        String joined = cells.text();
        return joined.contains("层次") && joined.contains("户室号") && joined.contains("建筑面积");
    }

    private boolean parseTable(Element table, FileRecord fileRecord, ParsedDataHeader header,
            List<RoomInfo> roomInfos, List<ParsedDataItem> totalItems) {
        Elements rows = table.select("tr");
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        int headerRowIndex = findActualHeaderRow(rows);
        int startIndex;
        Elements headerCells;
        boolean hasActualHeader;

        if (headerRowIndex != -1) {
            headerCells = rows.get(headerRowIndex).select("td, th");
            hasActualHeader = true;
            startIndex = headerRowIndex + 1;
        } else {
            Element headerRow = rows.first();
            if (headerRow == null) {
                return false;
            }
            headerCells = headerRow.select("td, th");
            hasActualHeader = isHeaderRow(headerCells) && !isAddressRow(headerCells);
            startIndex = hasActualHeader ? 1 : 0;
            if (isAddressRow(headerCells)) {
                startIndex = 1;
                hasActualHeader = false;
            }
        }

        Map<String, Integer> columnMapping = buildColumnMapping(headerCells, hasActualHeader);
        boolean hasUsageColumn = columnMapping.containsKey("用途");

        for (int i = startIndex; i < rows.size(); i++) {
            Elements cells = rows.get(i).select("td, th");
            if (cells.isEmpty() || isAddressRow(cells)) {
                continue;
            }
            String levelValue = extractTextValue(cells, columnMapping, "层次");
            if (!isDataRow(cells) && !isLikelyRoomTableRow(levelValue)) {
                continue;
            }
            if ("合计".equals(levelValue)) {
                extractTotalAreaValues(cells, columnMapping, totalItems, header);
            } else {
                RoomInfo roomInfo = extractRoomInfoAsObject(cells, columnMapping);
                if (roomInfo != null) {
                    roomInfo.setFileRecordId(fileRecord.getId());
                    roomInfo.setProjectId(fileRecord.getProjectId());
                    roomInfos.add(roomInfo);
                }
            }
        }
        return hasUsageColumn;
    }

    private boolean tryLlmFallback(String pageHtml, FileRecord fileRecord, ParsedDataHeader header,
            List<RoomInfo> roomInfos, List<ParsedDataItem> totalItems, boolean replaceExisting) {
        try {
            String htmlInput = selectLlmHtmlInput(pageHtml);
            String projectId = header.getProjectId() != null ? header.getProjectId().toString() : "unknown";
            Map<String, Object> llmResult = llmService.extractRoomTableFromHtml(htmlInput, projectId,
                    fileRecord != null ? fileRecord.getId() : null);
            String response = (String) llmResult.get("response");
            if (!StringUtils.hasText(response)) {
                return false;
            }
            return applyLlmResponse(response, fileRecord, header, roomInfos, totalItems, replaceExisting);
        } catch (Exception e) {
            log.warn("户室表 LLM 兜底失败: {}", e.getMessage());
            return false;
        }
    }

    private String selectLlmHtmlInput(String pageHtml) {
        Document doc = Jsoup.parse(pageHtml);
        Element best = null;
        int bestRows = 0;
        for (Element table : doc.select("table")) {
            if (!isRoomAreaTable(table)) {
                continue;
            }
            int rows = table.select("tr").size();
            if (rows > bestRows) {
                bestRows = rows;
                best = table;
            }
        }
        if (best != null) {
            return best.outerHtml();
        }
        return pageHtml.length() > 12000 ? pageHtml.substring(0, 12000) : pageHtml;
    }

    private boolean applyLlmResponse(String response, FileRecord fileRecord, ParsedDataHeader header,
            List<RoomInfo> roomInfos, List<ParsedDataItem> totalItems, boolean replaceExisting) throws Exception {
        String json = stripMarkdownFence(response.trim());
        JsonNode root = objectMapper.readTree(json);
        JsonNode rows = root.path("rows");
        if (!rows.isArray() || rows.isEmpty()) {
            return false;
        }
        List<RoomInfo> parsed = new ArrayList<>();
        for (JsonNode row : rows) {
            RoomInfo room = new RoomInfo();
            room.setFileRecordId(fileRecord.getId());
            room.setProjectId(fileRecord.getProjectId());
            room.setRoomLevel(textOrNull(row, "level"));
            room.setRoomNumber(textOrNull(row, "number"));
            room.setBuildingArea(decimalOrNull(row, "building_area"));
            room.setInnerArea(decimalOrNull(row, "inner_area"));
            room.setBalconyArea(decimalOrNull(row, "balcony_area"));
            room.setSharedArea(decimalOrNull(row, "shared_area"));
            room.setRoomStructure(textOrNull(row, "structure"));
            room.setRoomUsage(textOrNull(row, "usage"));
            room.setRemark(textOrNull(row, "remark"));
            if (room.getRoomNumber() != null || room.getBuildingArea() != null) {
                parsed.add(room);
            }
        }
        if (parsed.isEmpty()) {
            return false;
        }

        JsonNode totals = root.path("totals");
        if (!validateAllLlmTotals(parsed, totals)) {
            log.warn("LLM 户室表四维合计校验未通过，丢弃结果");
            return false;
        }

        if (replaceExisting) {
            roomInfos.clear();
            removeOcrTotalItems(totalItems);
        }
        roomInfos.addAll(parsed);
        addLlmTotalsFromJson(totals, totalItems, header);
        return true;
    }

    private void removeOcrTotalItems(List<ParsedDataItem> totalItems) {
        if (totalItems == null || totalItems.isEmpty()) {
            return;
        }
        totalItems.removeIf(item -> item != null && StringUtils.hasText(item.getNormalizedKey())
                && item.getNormalizedKey().endsWith("_from_ocr"));
    }

    private void addLlmTotalsFromJson(JsonNode totals, List<ParsedDataItem> totalItems, ParsedDataHeader header) {
        BigDecimal building = decimalOrNull(totals, "building_area");
        if (building != null) {
            addLlmTotalItem("建筑面积合计", "room_info_building_area_sum_from_ocr", building, totalItems, header);
        }
        BigDecimal inner = decimalOrNull(totals, "inner_area");
        if (inner != null) {
            addLlmTotalItem("套内面积合计", "room_info_inner_area_sum_from_ocr", inner, totalItems, header);
        }
        BigDecimal balcony = decimalOrNull(totals, "balcony_area");
        if (balcony != null) {
            addLlmTotalItem("阳台面积合计", "room_info_balcony_area_sum_from_ocr", balcony, totalItems, header);
        }
        BigDecimal shared = decimalOrNull(totals, "shared_area");
        if (shared != null) {
            addLlmTotalItem("分摊面积合计", "room_info_shared_area_sum_from_ocr", shared, totalItems, header);
        }
    }

    private boolean validateAllLlmTotals(List<RoomInfo> rooms, JsonNode totals) {
        if (!validateTotalDimension(rooms, decimalOrNull(totals, "building_area"), RoomInfo::getBuildingArea)) {
            return false;
        }
        if (!validateTotalDimension(rooms, decimalOrNull(totals, "inner_area"), RoomInfo::getInnerArea)) {
            return false;
        }
        if (!validateTotalDimension(rooms, decimalOrNull(totals, "balcony_area"), RoomInfo::getBalconyArea)) {
            return false;
        }
        return validateTotalDimension(rooms, decimalOrNull(totals, "shared_area"), RoomInfo::getSharedArea);
    }

    private boolean validateTotalDimension(List<RoomInfo> rooms, BigDecimal llmTotal,
            java.util.function.Function<RoomInfo, BigDecimal> getter) {
        if (llmTotal == null) {
            return true;
        }
        BigDecimal sum = sumDimension(rooms, getter);
        if (sum == null) {
            return true;
        }
        return sum.subtract(llmTotal).abs().compareTo(TOTAL_AREA_TOLERANCE) <= 0;
    }

    private BigDecimal sumDimension(List<RoomInfo> rooms,
            java.util.function.Function<RoomInfo, BigDecimal> getter) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (RoomInfo r : rooms) {
            BigDecimal val = getter.apply(r);
            if (val != null) {
                sum = sum.add(val);
                any = true;
            }
        }
        return any ? sum : null;
    }

    private void addLlmTotalItem(String displayName, String key, BigDecimal value,
            List<ParsedDataItem> totalItems, ParsedDataHeader header) {
        String displayStr = value.toString();
        totalItems.add(ParseUtils.createDataItem(
                displayName, displayStr, value, "㎡", key, displayStr, value,
                "ROOM_TABLE_TOTAL", 1, "LLM合计",
                ParseUtils.SOURCE_LLM, ParseUtils.SOURCE_LLM, header));
    }

    private static String stripMarkdownFence(String s) {
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                return s.substring(firstNl + 1, lastFence).trim();
            }
        }
        return s;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String t = v.asText().trim();
        return t.isEmpty() ? null : t;
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return v.decimalValue();
        }
        return ParseUtils.parseAreaValue(v.asText());
    }

    private boolean isHeaderRow(Elements cells) {
        if (cells == null || cells.isEmpty()) {
            return false;
        }
        if (isAddressRow(cells)) {
            return false;
        }
        int textCellCount = 0;
        int headerKeywordCount = 0;
        int totalCells = cells.size();
        for (Element cell : cells) {
            String cellText = cell.text().trim();
            if (cellText.isEmpty() || cellText.matches("[0-9.,\\s]*")) {
                continue;
            }
            for (String keyword : HEADER_KEYWORDS) {
                if (cellText.contains(keyword)) {
                    headerKeywordCount++;
                    break;
                }
            }
            if (cellText.matches(".*[\u4e00-\u9fa5].*") || cellText.contains("层") || cellText.contains("号")
                    || cellText.contains("面积")) {
                textCellCount++;
            }
        }
        return textCellCount > totalCells / 2 && (headerKeywordCount > 0 || totalCells <= 3);
    }

    private boolean isAddressRow(Elements cells) {
        if (cells == null || cells.isEmpty() || cells.size() > 1) {
            return false;
        }
        String cellText = cells.first().text().trim();
        return cellText.contains("房屋坐落") || cellText.contains("地址")
                || cellText.matches(".*[\u4e00-\u9fa5]*[路街巷号栋单元].*")
                || (cellText.length() > 10 && cellText.matches(".*[\u4e00-\u9fa5]+.*\\d+.*号.*"));
    }

    private boolean isLikelyRoomTableRow(String levelValue) {
        if (levelValue == null) {
            return false;
        }
        return "合计".equals(levelValue) || levelValue.matches("\\d+层");
    }

    private boolean isDataRow(Elements cells) {
        if (cells == null || cells.isEmpty()) {
            return false;
        }
        int numericCellCount = 0;
        int textCellCount = 0;
        for (Element cell : cells) {
            String cellText = cell.text().trim();
            if (cellText.isEmpty()) {
                continue;
            }
            if (cellText.matches(".*\\d+(\\.\\d+)?(\\s*m²|\\s*㎡|\\s*平方米)?")) {
                numericCellCount++;
            } else if (cellText.matches("\\d+[层楼]") || cellText.matches("\\d+\\d+")
                    || cellText.matches("\\d+[A-Z]\\d*") || cellText.matches("\\d+")) {
                textCellCount++;
            }
        }
        return numericCellCount > 0 || (textCellCount > 0 && numericCellCount >= 1);
    }

    private int findActualHeaderRow(Elements rows) {
        if (rows == null || rows.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < Math.min(5, rows.size()); i++) {
            Elements cells = rows.get(i).select("td, th");
            if (isAddressRow(cells)) {
                continue;
            }
            if (isHeaderRow(cells) && hasSubsequentDataRows(rows, i + 1)) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasSubsequentDataRows(Elements rows, int startIndex) {
        if (rows == null || startIndex >= rows.size()) {
            return false;
        }
        int checkCount = Math.min(3, rows.size() - startIndex);
        int dataRowCount = 0;
        for (int i = startIndex; i < startIndex + checkCount; i++) {
            if (isDataRow(rows.get(i).select("td, th"))) {
                dataRowCount++;
            }
        }
        return dataRowCount >= 1;
    }

    private Map<String, Integer> buildColumnMapping(Elements headerCells, boolean isActualHeader) {
        Map<String, Integer> columnMapping = new HashMap<>();
        int totalColumns = headerCells.size();
        Map<String, List<String>> aliases = ParseConstants.getRoomFieldAliases();

        if (isActualHeader) {
            for (int i = 0; i < headerCells.size(); i++) {
                String headerText = headerCells.get(i).text().trim();
                for (Map.Entry<String, List<String>> entry : aliases.entrySet()) {
                    String fieldName = entry.getKey();
                    if (columnMapping.containsKey(fieldName)) {
                        continue;
                    }
                    for (String alias : entry.getValue()) {
                        if (SurveyHeaderLabelSynonyms.headerMatchesAlias(headerText, alias)) {
                            columnMapping.put(fieldName, i);
                            break;
                        }
                    }
                }
            }
        }

        if (columnMapping.size() >= 3) {
            return columnMapping;
        }

        List<String> fixedFieldOrder;
        if (totalColumns == 9) {
            fixedFieldOrder = Arrays.asList("层次", "户室号", "建筑面积", "套内面积", "阳台面积", "分摊面积", "户室结构", "用途", "备注");
        } else if (totalColumns == 8) {
            fixedFieldOrder = Arrays.asList("层次", "户室号", "建筑面积", "套内面积", "阳台面积", "分摊面积", "户室结构", "备注");
        } else if (totalColumns == 7) {
            fixedFieldOrder = Arrays.asList("层次", "户室号", "建筑面积", "套内面积", "阳台面积", "分摊面积", "备注");
        } else {
            fixedFieldOrder = Arrays.asList("层次", "户室号", "建筑面积", "套内面积", "阳台面积", "分摊面积", "户室结构", "用途", "备注");
            if (totalColumns > fixedFieldOrder.size()) {
                fixedFieldOrder = fixedFieldOrder.subList(0, Math.min(totalColumns, fixedFieldOrder.size()));
            }
        }

        for (int i = 0; i < totalColumns; i++) {
            final int colIdx = i;
            boolean isMapped = columnMapping.values().stream().anyMatch(v -> v == colIdx);
            if (!isMapped && i < fixedFieldOrder.size()) {
                String fieldName = fixedFieldOrder.get(i);
                if (!columnMapping.containsKey(fieldName)) {
                    columnMapping.put(fieldName, i);
                }
            }
        }
        return columnMapping;
    }

    private RoomInfo extractRoomInfoAsObject(Elements cells, Map<String, Integer> columnMapping) {
        try {
            RoomInfo roomInfo = new RoomInfo();
            roomInfo.setRoomLevel(extractTextValue(cells, columnMapping, "层次"));
            roomInfo.setRoomNumber(extractTextValue(cells, columnMapping, "户室号"));
            roomInfo.setBuildingArea(extractAreaValue(cells, columnMapping, "建筑面积"));
            roomInfo.setInnerArea(extractAreaValue(cells, columnMapping, "套内面积"));
            roomInfo.setBalconyArea(extractAreaValue(cells, columnMapping, "阳台面积"));
            roomInfo.setSharedArea(extractAreaValue(cells, columnMapping, "分摊面积"));
            roomInfo.setRoomStructure(extractTextValue(cells, columnMapping, "户室结构"));
            roomInfo.setRoomUsage(extractTextValue(cells, columnMapping, "用途"));
            roomInfo.setRemark(extractTextValue(cells, columnMapping, "备注"));
            return roomInfo;
        } catch (Exception e) {
            log.warn("解析房间信息失败: {}", e.getMessage());
            return null;
        }
    }

    private String extractTextValue(Elements cells, Map<String, Integer> columnMapping, String fieldName) {
        if (!columnMapping.containsKey(fieldName)) {
            return null;
        }
        int columnIndex = columnMapping.get(fieldName);
        if (columnIndex >= cells.size()) {
            return null;
        }
        return ParseUtils.cleanText(cells.get(columnIndex).text());
    }

    private BigDecimal extractAreaValue(Elements cells, Map<String, Integer> columnMapping, String fieldName) {
        if (!columnMapping.containsKey(fieldName)) {
            return null;
        }
        int columnIndex = columnMapping.get(fieldName);
        if (columnIndex >= cells.size()) {
            return null;
        }
        return ParseUtils.parseAreaValue(ParseUtils.cleanText(cells.get(columnIndex).text()));
    }

    private void extractTotalAreaField(Elements cells, Map<String, Integer> columnMapping,
            String fieldName, String normalizedKey, String displayName,
            List<ParsedDataItem> items, ParsedDataHeader header) {
        if (!columnMapping.containsKey(fieldName)) {
            return;
        }
        int columnIndex = columnMapping.get(fieldName);
        if (columnIndex >= cells.size()) {
            return;
        }
        String areaStr = ParseUtils.cleanText(cells.get(columnIndex).text());
        BigDecimal areaValue = ParseUtils.parseAreaValue(areaStr);
        if (areaValue != null) {
            String displayStr = areaValue.toString();
            items.add(ParseUtils.createDataItem(
                    displayName, displayStr, areaValue, "㎡",
                    normalizedKey, displayStr, areaValue, "ROOM_TABLE_TOTAL",
                    1, "合计行" + fieldName,
                    ParseUtils.SOURCE_REGEX, ParseUtils.SOURCE_REGEX, header));
        }
    }

    private void extractTotalAreaValues(Elements cells, Map<String, Integer> columnMapping,
            List<ParsedDataItem> totalItems, ParsedDataHeader header) {
        extractTotalAreaField(cells, columnMapping, "建筑面积", "room_info_building_area_sum_from_ocr", "建筑面积合计",
                totalItems, header);
        extractTotalAreaField(cells, columnMapping, "套内面积", "room_info_inner_area_sum_from_ocr", "套内面积合计",
                totalItems, header);
        extractTotalAreaField(cells, columnMapping, "阳台面积", "room_info_balcony_area_sum_from_ocr", "阳台面积合计",
                totalItems, header);
        extractTotalAreaField(cells, columnMapping, "分摊面积", "room_info_shared_area_sum_from_ocr", "分摊面积合计",
                totalItems, header);
    }
}
