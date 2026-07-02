package com.gov.landcheck.file.task.processor.receiver.parse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.PlanningReviewForm;
import com.gov.landcheck.core.bo.entity.PlanningReviewRow;
import com.gov.landcheck.core.bo.entity.UsageConfig;
import com.gov.landcheck.core.enums.PlanningReviewAreaCategory;
import com.gov.landcheck.core.service.UsageConfigService;
import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.dto.ParseResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 规划复核表：合并各页模型 JSON，生成主表 + 行列表。
 */
@Slf4j
@Service
public class PlanningReviewTableParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UsageConfigService usageConfigService;

    public ParseResult parse(List<OCRPageResult> ocrPages, ParsedDataHeader header) throws Exception {
        long start = System.currentTimeMillis();
        PlanningReviewForm form = new PlanningReviewForm();
        Map<String, PlanningReviewRow> rowByKey = new LinkedHashMap<>();
        Set<String> seenPayloads = new HashSet<>();
        int jsonParseFailureCount = 0;
        List<Integer> jsonParseFailurePages = new ArrayList<>();

        if (ocrPages != null) {
            for (OCRPageResult page : ocrPages) {
                if (page == null || !Boolean.TRUE.equals(page.getSuccess())) {
                    continue;
                }
                String raw = page.getMarkdownText();
                if (!StringUtils.hasText(raw)) {
                    continue;
                }
                String cleaned = stripJsonFence(raw.trim());
                if (!seenPayloads.add(cleaned)) {
                    continue;
                }
                try {
                    JsonNode root = objectMapper.readTree(cleaned);
                    mergeHeader(form, root.path("header"));
                    mergeRows(rowByKey, root.path("rows"));
                } catch (Exception ex) {
                    jsonParseFailureCount++;
                    int pageNo = page.getPageNumber();
                    jsonParseFailurePages.add(pageNo);
                    log.warn("规划复核表单页JSON解析失败: page={}, err={}", pageNo, ex.getMessage());
                }
            }
        }

        if (jsonParseFailureCount > 0) {
            throw new IllegalStateException(String.format(
                    "规划复核解析失败：存在 %d 页 JSON 无法解析（页码: %s），已中止合并以避免部分结果静默落库",
                    jsonParseFailureCount, jsonParseFailurePages));
        }

        Map<String, PlanningReviewAreaCategory> areaCategoryCache = new HashMap<>();
        List<PlanningReviewRow> rows = new ArrayList<>(rowByKey.values());
        for (PlanningReviewRow row : rows) {
            row.setAreaCategory(resolveAreaCategory(row.getBuildingNatureRaw(), areaCategoryCache));
        }

        ParseResult result = new ParseResult(Collections.emptyList(), Collections.emptyList());
        result.setPlanningReviewForm(form);
        result.setPlanningReviewRows(rows);
        result.setParseEngine("LLM_VISION");
        result.setProcessingTimeMs(System.currentTimeMillis() - start);
        return result;
    }

    private void mergeHeader(PlanningReviewForm form, JsonNode header) {
        if (header == null || header.isMissingNode() || !header.isObject()) {
            return;
        }
        form.setProjectName(firstNonBlank(form.getProjectName(), text(header, "project_name")));
        form.setConstructionUnit(firstNonBlank(form.getConstructionUnit(), text(header, "construction_unit")));
        form.setDesignUnit(firstNonBlank(form.getDesignUnit(), text(header, "design_unit")));
        form.setConstructionLocation(
                firstNonBlank(form.getConstructionLocation(), text(header, "construction_location")));
        form.setLandUseNature(firstNonBlank(form.getLandUseNature(), text(header, "land_use_nature")));
        form.setContactPerson(firstNonBlank(form.getContactPerson(), text(header, "contact_person")));
        form.setContactPhone(firstNonBlank(form.getContactPhone(), text(header, "contact_phone")));
        form.setRemarks(firstNonBlank(form.getRemarks(), text(header, "remarks")));
    }

    private void mergeRows(Map<String, PlanningReviewRow> rowByKey, JsonNode rowsNode) {
        if (rowsNode == null || !rowsNode.isArray()) {
            return;
        }
        for (JsonNode n : rowsNode) {
            if (n == null || !n.isObject()) {
                continue;
            }
            if (n.path("is_summary_row").asBoolean(false)) {
                continue;
            }
            PlanningReviewRow row = toRow(n);
            String key = rowKey(row);
            rowByKey.putIfAbsent(key, row);
        }
    }

    private static String rowKey(PlanningReviewRow row) {
        Integer idx = row.getRowIndex();
        String name = row.getEngineeringProject() != null ? row.getEngineeringProject() : "";
        return (idx != null ? idx : -1) + "|" + name;
    }

    private PlanningReviewRow toRow(JsonNode n) {
        PlanningReviewRow row = new PlanningReviewRow();
        row.setRowIndex(intVal(n, "row_index"));
        row.setEngineeringProject(text(n, "engineering_project"));
        row.setBuildingNatureRaw(text(n, "building_nature"));
        row.setConstructionNature(text(n, "construction_nature"));
        row.setBuildingCount(intVal(n, "building_count"));
        row.setAboveGroundFloors(intVal(n, "above_ground_floors"));
        row.setBelowGroundFloors(intVal(n, "below_ground_floors"));
        row.setHeightM(decimal(n, "height_m"));
        row.setBaseAreaM2(decimal(n, "base_area_m2"));
        row.setResidentialResidentialArea(decimal(n, "residential_residential_area"));
        row.setResidentialHotelApartmentArea(decimal(n, "residential_hotel_apartment_area"));
        row.setResidentialOtherArea(decimal(n, "residential_other_area"));
        row.setNrAboveCommercial(decimal(n, "nr_above_commercial"));
        row.setNrAboveGarage(decimal(n, "nr_above_garage"));
        row.setNrAboveOther(decimal(n, "nr_above_other"));
        row.setNrBelowCommercial(decimal(n, "nr_below_commercial"));
        row.setNrBelowSupporting(decimal(n, "nr_below_supporting"));
        row.setNrBelowOther(decimal(n, "nr_below_other"));
        row.setAboveGroundArea(decimal(n, "above_ground_area"));
        row.setBelowGroundArea(decimal(n, "below_ground_area"));
        row.setTotalArea(decimal(n, "total_area"));
        row.setFarAboveGround(decimal(n, "far_above_ground"));
        row.setFarBelowGround(decimal(n, "far_below_ground"));
        return row;
    }

    private PlanningReviewAreaCategory resolveAreaCategory(String raw,
            Map<String, PlanningReviewAreaCategory> cache) {
        if (!StringUtils.hasText(raw)) {
            return PlanningReviewAreaCategory.OTHER_PENDING;
        }
        String t = raw.trim();
        return cache.computeIfAbsent(t, this::resolveAreaCategoryUncached);
    }

    private PlanningReviewAreaCategory resolveAreaCategoryUncached(String t) {
        UsageConfig config = usageConfigService.matchUsageConfig(t);
        if (config != null && StringUtils.hasText(config.getUsageCategory())) {
            String cat = config.getUsageCategory().trim();
            if ("RESIDENTIAL".equalsIgnoreCase(cat)) {
                return PlanningReviewAreaCategory.RESIDENTIAL;
            }
            if ("COMMERCIAL".equalsIgnoreCase(cat)) {
                return PlanningReviewAreaCategory.COMMERCIAL;
            }
        }
        if (t.contains("商住") || t.contains("商业")) {
            return PlanningReviewAreaCategory.COMMERCIAL;
        }
        if (t.contains("住宅") || t.contains("居住")) {
            return PlanningReviewAreaCategory.RESIDENTIAL;
        }
        if (t.contains("其它") || t.contains("其他")) {
            return PlanningReviewAreaCategory.OTHER_PENDING;
        }
        return PlanningReviewAreaCategory.OTHER_PENDING;
    }

    private static String firstNonBlank(String current, String incoming) {
        if (StringUtils.hasText(current)) {
            return current;
        }
        return incoming;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        if (v.isTextual()) {
            String s = v.asText();
            return StringUtils.hasText(s) ? s.trim() : null;
        }
        if (v.isNumber()) {
            return v.asText();
        }
        return null;
    }

    private static Integer intVal(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        if (v.isInt() || v.isLong()) {
            return v.intValue();
        }
        if (v.isTextual()) {
            String s = v.asText().trim();
            if (!StringUtils.hasText(s)) {
                return null;
            }
            try {
                return Integer.parseInt(s.replaceAll("[^0-9-]", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (v.isNumber()) {
            return v.intValue();
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
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
