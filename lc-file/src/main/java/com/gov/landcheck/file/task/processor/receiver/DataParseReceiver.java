package com.gov.landcheck.file.task.processor.receiver;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.core.bo.entity.RoomInfo;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.dto.ParseResult;
import com.gov.landcheck.file.dto.RoomTableParseResult;
import com.gov.landcheck.file.task.processor.receiver.parse.CapacityIndicatorTableParser;
import com.gov.landcheck.file.task.processor.receiver.parse.ContractDataParser;
import com.gov.landcheck.file.task.processor.receiver.parse.ParseUtils;
import com.gov.landcheck.file.task.processor.receiver.parse.PlanningReviewTableParser;
import com.gov.landcheck.file.task.processor.receiver.parse.ProjectPartySummaryExcelParser;
import com.gov.landcheck.file.task.processor.receiver.parse.RoomTableParser;
import com.gov.landcheck.file.task.processor.receiver.parse.SurveyDataParser;

import lombok.extern.slf4j.Slf4j;

/**
 * 数据解析入口：合并 OCR 页面文本，按文件类型委托给对应解析器，汇总结果
 */
@Slf4j
@Service
public class DataParseReceiver {

    @Autowired
    private ContractDataParser contractDataParser;
    @Autowired
    private SurveyDataParser surveyDataParser;
    @Autowired
    private RoomTableParser roomTableParser;

    @Autowired
    private PlanningReviewTableParser planningReviewTableParser;

    @Autowired
    private CapacityIndicatorTableParser capacityIndicatorTableParser;

    @Autowired
    private ProjectPartySummaryExcelParser projectPartySummaryExcelParser;

    private final Map<FileContextType, ParseHandler> parseHandlers = new EnumMap<>(FileContextType.class);

    @PostConstruct
    public void initHandlers() {
        parseHandlers.put(FileContextType.CONTRACT, this::parseContract);
        parseHandlers.put(FileContextType.SURVEY_REPORT, this::parseSurvey);
        parseHandlers.put(FileContextType.PLANNING_REVIEW, this::parsePlanningReview);
        parseHandlers.put(FileContextType.CAPACITY_INDICATOR, this::parseCapacityIndicator);
        parseHandlers.put(FileContextType.PROJECT_PARTY_SURVEY_SUMMARY, this::parseProjectPartySummary);
    }

    public ParseResult parse(List<OCRPageResult> ocrResults, FileRecord fileRecord, ParsedDataHeader header)
            throws Exception {
        long startTime = System.currentTimeMillis();
        ParseHandler handler = parseHandlers.get(fileRecord.getFileContextType());
        if (handler == null) {
            throw new IllegalStateException("不支持的文件解析类型: " + fileRecord.getFileContextType());
        }
        ParseResult result = handler.parse(new ParseContext(ocrResults, fileRecord, header, startTime));
        enforceCriticalOutput(fileRecord.getFileContextType(), result);
        return result;
    }

    private ParseResult parseContract(ParseContext ctx) throws Exception {
        String contractText = ParseUtils.combineFirst20PagesText(ctx.ocrResults());
        List<ParsedDataItem> dataItems = contractDataParser.parseContractData(contractText, ctx.header());
        return finalizeSimpleResult(ctx, dataItems, new ArrayList<>(), "LLM");
    }

    private ParseResult parseSurvey(ParseContext ctx) throws Exception {
        List<ParsedDataItem> dataItems = new ArrayList<>(
                surveyDataParser.parseSurveyData(ctx.ocrResults(), ctx.header()));
        RoomTableParseResult roomTableResult = roomTableParser.parseRoomTable(ctx.ocrResults(), ctx.fileRecord(),
                ctx.header());
        List<RoomInfo> roomInfos = new ArrayList<>(roomTableResult.getRoomInfos());
        dataItems.addAll(roomTableResult.getTotalItems());
        String parseEngine = buildSurveyParseEngine(roomTableResult);
        return finalizeSimpleResult(ctx, dataItems, roomInfos, parseEngine);
    }

    private String buildSurveyParseEngine(RoomTableParseResult roomTableResult) {
        StringBuilder engine = new StringBuilder("REGEX");
        if (roomTableResult.isParseUsedLlm()) {
            engine.append("+LLM(户室");
            if (roomTableResult.isRoomTablePartialLlm()) {
                engine.append(",部分");
            }
            engine.append(")");
        }
        if (roomTableResult.isUsageFilledByLlm()) {
            engine.append("+LLM(用途)");
        }
        return engine.toString();
    }

    private ParseResult parsePlanningReview(ParseContext ctx) throws Exception {
        ParseResult pr = planningReviewTableParser.parse(ctx.ocrResults(), ctx.header());
        long processingTime = System.currentTimeMillis() - ctx.startTime();
        pr.setProcessingTimeMs(processingTime);
        log.info("数据解析完成: fileId={}, planningRows={}, processingTime={}ms",
                ctx.fileRecord().getId(),
                pr.getPlanningReviewRows() != null ? pr.getPlanningReviewRows().size() : 0,
                processingTime);
        return pr;
    }

    private ParseResult parseCapacityIndicator(ParseContext ctx) throws Exception {
        ParseResult pr = capacityIndicatorTableParser.parse(ctx.ocrResults(), ctx.header());
        long processingTime = System.currentTimeMillis() - ctx.startTime();
        pr.setProcessingTimeMs(processingTime);
        log.info("容量指标核查解析完成: fileId={}, processingTime={}ms",
                ctx.fileRecord().getId(), processingTime);
        return pr;
    }

    private ParseResult parseProjectPartySummary(ParseContext ctx) throws Exception {
        ParseResult summaryResult = projectPartySummaryExcelParser.parse(ctx.fileRecord(), ctx.header());
        long processingTime = System.currentTimeMillis() - ctx.startTime();
        summaryResult.setProcessingTimeMs(processingTime);
        log.info("项目方汇总解析完成: fileId={}, parseEngine={}, processingTime={}ms",
                ctx.fileRecord().getId(),
                summaryResult.getParseEngine(),
                processingTime);
        return summaryResult;
    }

    private ParseResult finalizeSimpleResult(ParseContext ctx, List<ParsedDataItem> dataItems, List<RoomInfo> roomInfos,
            String parseEngine) {
        long processingTime = System.currentTimeMillis() - ctx.startTime();
        ParseResult result = new ParseResult(dataItems, roomInfos);
        result.setProcessingTimeMs(processingTime);
        result.setParseEngine(parseEngine);
        log.info("数据解析完成: fileId={}, items={}, roomInfos={}, processingTime={}ms",
                ctx.fileRecord().getId(), dataItems.size(), roomInfos.size(), processingTime);
        return result;
    }

    /**
     * 关键产物守卫：
     * - 合同：必须至少抽取出1个字段；
     * - 项目方汇总：必须有主表；declaredTotals 可为 null（无汇总区块），回填为 PARTIAL 状态。
     */
    private void enforceCriticalOutput(FileContextType contextType, ParseResult result) {
        if (contextType == null || result == null) {
            return;
        }
        switch (contextType) {
            case CONTRACT -> {
                if (result.getDataItems() == null || result.getDataItems().isEmpty()) {
                    throw new IllegalStateException("合同解析结果为空，判定为不可恢复错误");
                }
            }
            case PROJECT_PARTY_SURVEY_SUMMARY -> {
                if (result.getProjectPartySummaryForm() == null) {
                    throw new IllegalStateException("项目方汇总缺少主表数据，判定为不可恢复错误");
                }
                // 解析产物允许 declaredTotals 为 null（无汇总），回填时保留 PARTIAL 状态和说明。
            }
            case SURVEY_REPORT -> {
                if (result.getRoomInfos() == null || result.getRoomInfos().isEmpty()) {
                    throw new IllegalStateException("实测报告未解析到户室面积对照表明细");
                }
            }
            case CAPACITY_INDICATOR -> {
                if (result.getCapacityIndicatorInfo() == null
                        || (result.getCapacityIndicatorInfo().getTotalArea() == null
                                && result.getCapacityIndicatorInfo().getCommercialArea() == null
                                && result.getCapacityIndicatorInfo().getResidentialArea() == null)) {
                    throw new IllegalStateException("容量指标核查解析结果为空，判定为不可恢复错误");
                }
            }
            default -> {
                // 非关键类型暂维持原容错策略
            }
        }
    }

    @FunctionalInterface
    private interface ParseHandler {
        ParseResult parse(ParseContext context) throws Exception;
    }

    private record ParseContext(
            List<OCRPageResult> ocrResults,
            FileRecord fileRecord,
            ParsedDataHeader header,
            long startTime) {
    }
}
