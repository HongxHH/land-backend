package com.gov.landcheck.file.task.processor.receiver.parse;

import java.util.ArrayList;

import java.util.StringJoiner;

import org.springframework.stereotype.Component;

import org.springframework.util.StringUtils;

import com.gov.landcheck.core.bo.entity.FileRecord;

import com.gov.landcheck.core.bo.entity.ParsedDataHeader;

import com.gov.landcheck.core.bo.entity.ProjectPartyDeclaredTotals;

import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;

import com.gov.landcheck.file.dto.ParseResult;

import com.gov.landcheck.file.task.processor.receiver.parse.excel.ProjectPartySummaryGroundingValidator;

import com.gov.landcheck.file.task.processor.receiver.parse.excel.ProjectPartySummaryGroundingValidator.GroundingResult;

import com.gov.landcheck.file.task.processor.receiver.parse.excel.ProjectPartySummaryLlmExtractor;

import com.gov.landcheck.file.task.processor.receiver.parse.excel.ProjectPartySummaryRegion;

import com.gov.landcheck.file.task.processor.receiver.parse.excel.ProjectPartySummaryRegionLocator;

import com.gov.landcheck.file.task.processor.receiver.parse.excel.ProjectPartySummaryRuleExtractor;

import com.gov.landcheck.file.task.processor.receiver.parse.excel.ProjectPartySummaryRuleExtractor.RuleResult;

import com.gov.landcheck.file.task.processor.receiver.parse.excel.ProjectPartySummarySchemaValidator;

import com.gov.landcheck.file.task.processor.receiver.parse.excel.ProjectPartySummarySheetMatrixReader;

import com.gov.landcheck.file.utils.GridFSUtils;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * 项目方实测汇总 Excel 解析器：规则优先，LLM 对锚点区块兜底；严禁编造，无汇总则输出空。
 * 
 */

@Slf4j

@Component

public class ProjectPartySummaryExcelParser {

    private static final String REMARK_NO_SUMMARY_BLOCK = "文件中未找到项目方声明汇总三行，未提取汇总数据";

    private static final String REMARK_LLM_EMPTY = "LLM 未在文件片段中发现可确认的汇总数值";

    private static final String REMARK_GROUNDING_REJECTED = "LLM 抽取数值与文件片段不一致已丢弃，未写入汇总数据";

    @Resource

    private GridFSUtils gridFSUtils;

    @Resource

    private ProjectPartySummarySheetMatrixReader sheetMatrixReader;

    @Resource

    private ProjectPartySummaryRegionLocator regionLocator;

    @Resource

    private ProjectPartySummaryRuleExtractor ruleExtractor;

    @Resource

    private ProjectPartySummaryLlmExtractor llmExtractor;

    @Resource

    private ProjectPartySummarySchemaValidator schemaValidator;

    @Resource

    private ProjectPartySummaryGroundingValidator groundingValidator;

    public ParseResult parse(FileRecord fileRecord, ParsedDataHeader header) throws Exception {

        String gridfsId = fileRecord.getGridfsId();
        if (!StringUtils.hasText(gridfsId)) {
            throw new IllegalStateException(
                    "项目方汇总解析失败：fileRecordId=" + fileRecord.getId() + " 缺少有效的 gridfsId");
        }

        byte[] bytes = gridFSUtils.getFileBytes(gridfsId);

        var matrices = sheetMatrixReader.read(bytes);

        if (matrices.isEmpty()) {

            throw new IllegalStateException("Excel内容为空，无法解析");

        }

        ProjectPartySummaryRegion region = regionLocator.locate(matrices);

        String regionSnippet = region.toPromptText();

        String projectId = header != null && header.getProjectId() != null

                ? String.valueOf(header.getProjectId())

                : "unknown";

        Long fileRecordId = fileRecord.getId();

        RuleResult ruleResult = ruleExtractor.extract(region);

        if (ruleResult.success()) {

            log.info("项目方汇总规则解析成功: fileId={}, confidence={}", fileRecordId, ruleResult.confidence());

            ProjectPartySurveySummaryForm form = toForm(fileRecord, ruleResult.totals());

            applyParseStatusPolicy(form);

            return buildResult(form, "RULE", regionSnippet, null, 0, header);

        }

        if (!region.hasLikelySummaryBlock()) {

            log.info("项目方汇总未发现声明三行结构，跳过 LLM: fileId={}, confidence={}, categoryRows={}",

                    fileRecordId, region.confidence(), region.categoryRowCount());

            return buildNoSummaryResult(fileRecord, header, regionSnippet, "NONE");

        }

        log.info("项目方汇总规则解析未通过，走 LLM 兜底: fileId={}, reason={}", fileRecordId, ruleResult.reason());

        int retryCount = 0;

        String validationError = null;

        ProjectPartySummarySchemaValidator.ValidatedResult validated = null;

        String finalPrompt = null;

        String finalResponse = null;

        for (int attempt = 0; attempt < 2; attempt++) {

            String extractInput = regionSnippet;

            if (StringUtils.hasText(validationError)) {

                extractInput = regionSnippet + "\n\n【上一轮校验反馈】\n" + validationError + "\n请仅输出修正后的JSON。";

            }

            ProjectPartySummaryLlmExtractor.ExtractResult extractResult = llmExtractor.extract(

                    extractInput, projectId, fileRecordId);

            finalPrompt = extractResult.prompt();

            finalResponse = extractResult.response();

            validated = schemaValidator.validateAndConvert(finalResponse, true);

            if (!validated.valid()) {

                validationError = validated.errorMessage();

                retryCount++;

                continue;

            }

            if (validated.empty()) {

                break;

            }

            GroundingResult grounding = groundingValidator.validate(validated.totals(), region);

            if (grounding.grounded()) {

                break;

            }

            validationError = grounding.errorMessage();

            retryCount++;

        }

        if (validated == null || !validated.valid()) {

            throw new IllegalStateException("项目方汇总解析失败: " + (validated == null ? "未知错误" : validated.errorMessage()));

        }

        if (validated.empty()) {

            log.info("项目方汇总 LLM 返回空 totals（文件中无确认数值）: fileId={}", fileRecordId);

            return buildNoSummaryResult(fileRecord, header, finalPrompt, "LLM", finalResponse, retryCount,
                    REMARK_LLM_EMPTY);

        }

        GroundingResult finalGrounding = groundingValidator.validate(validated.totals(), region);

        if (!finalGrounding.grounded()) {

            log.warn("项目方汇总 LLM 数值未通过原文校验，丢弃结果: fileId={}, reason={}", fileRecordId,

                    finalGrounding.errorMessage());

            return buildNoSummaryResult(fileRecord, header, finalPrompt, "LLM", finalResponse, retryCount,

                    REMARK_GROUNDING_REJECTED);

        }

        ProjectPartySurveySummaryForm form = toForm(fileRecord, validated);

        applyParseStatusPolicy(form);

        log.info("项目方汇总 LLM 兜底成功: fileId={}, parseEngine=RULE+LLM, retries={}", fileRecordId, retryCount);

        return buildResult(form, "RULE+LLM", finalPrompt, finalResponse, retryCount, header);

    }

    private ParseResult buildNoSummaryResult(

            FileRecord fileRecord,

            ParsedDataHeader header,

            String prompt,

            String parseEngine) {

        return buildNoSummaryResult(fileRecord, header, prompt, parseEngine, null, 0, REMARK_NO_SUMMARY_BLOCK);

    }

    private ParseResult buildNoSummaryResult(

            FileRecord fileRecord,

            ParsedDataHeader header,

            String prompt,

            String parseEngine,

            String response,

            int retryCount,

            String remark) {

        ProjectPartySurveySummaryForm form = new ProjectPartySurveySummaryForm();

        form.setProjectId(fileRecord.getProjectId());

        form.setFileRecordId(fileRecord.getId());

        form.setDeclaredTotals(null);

        form.setIsParsed(1);

        form.setParseStatus("PARTIAL");

        form.setRemark(remark);

        return buildResult(form, parseEngine, prompt, response, retryCount, header);

    }

    private ParseResult buildResult(

            ProjectPartySurveySummaryForm form,

            String parseEngine,

            String prompt,

            String response,

            int retryCount,

            ParsedDataHeader header) {

        ParseResult result = new ParseResult(new ArrayList<>(), new ArrayList<>());

        result.setProjectPartySummaryForm(form);

        result.setParseEngine(parseEngine);

        if (header != null) {

            header.setModelPrompt(prompt);

            header.setModelAnalysisResult(response);

            header.setModelRetryCount(retryCount);

        }

        return result;

    }

    private ProjectPartySurveySummaryForm toForm(FileRecord fileRecord,

            ProjectPartySummarySchemaValidator.ValidatedResult validated) {

        ProjectPartySurveySummaryForm form = new ProjectPartySurveySummaryForm();

        form.setProjectId(fileRecord.getProjectId());

        form.setFileRecordId(fileRecord.getId());

        form.setDeclaredTotals(validated.totals());

        form.setIsParsed(1);

        form.setParseStatus("SUCCESS");

        return form;

    }

    private ProjectPartySurveySummaryForm toForm(FileRecord fileRecord, ProjectPartyDeclaredTotals totals) {

        ProjectPartySurveySummaryForm form = new ProjectPartySurveySummaryForm();

        form.setProjectId(fileRecord.getProjectId());

        form.setFileRecordId(fileRecord.getId());

        form.setDeclaredTotals(totals);

        form.setIsParsed(1);

        form.setParseStatus("SUCCESS");

        return form;

    }

    /**
     * 
     * totals 有数据但部分字段缺失时标记 PARTIAL。
     * 
     */

    private void applyParseStatusPolicy(ProjectPartySurveySummaryForm form) {

        if (form == null) {

            throw new IllegalStateException("项目方汇总主表为空");

        }

        ProjectPartyDeclaredTotals totals = form.getDeclaredTotals();

        if (totals == null) {

            form.setParseStatus("PARTIAL");

            form.setRemark(REMARK_NO_SUMMARY_BLOCK);

            return;

        }

        totals.fillMissingDifferences();

        StringJoiner missing = new StringJoiner("、");

        if (totals.getContractAgreedTotalBuildingArea() == null) {

            missing.add("合同约定建筑面积");

        }

        if (totals.getBuildableTotalBuildingArea() == null) {

            missing.add("计容建筑面积");

        }

        if (totals.getDifferenceTotalBuildingArea() == null) {

            missing.add("建筑面积差值");

        }

        if (totals.getContractAgreedCommercialArea() == null) {

            missing.add("合同约定商业面积");

        }

        if (totals.getBuildableCommercialArea() == null) {

            missing.add("计容商业面积");

        }

        if (totals.getDifferenceCommercialArea() == null) {

            missing.add("商业面积差值");

        }

        if (totals.getContractAgreedResidentialArea() == null) {

            missing.add("合同约定住宅面积");

        }

        if (totals.getBuildableResidentialArea() == null) {

            missing.add("计容住宅面积");

        }

        if (totals.getDifferenceResidentialArea() == null) {

            missing.add("住宅面积差值");

        }

        String missingFields = missing.toString();

        if (StringUtils.hasText(missingFields)) {

            form.setParseStatus("PARTIAL");

            form.setRemark("汇总字段缺失: " + missingFields);

        } else {

            form.setParseStatus("SUCCESS");

            if (!StringUtils.hasText(form.getRemark())) {

                form.setRemark(null);

            }

        }

    }

}
