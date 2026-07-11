package com.gov.landcheck.file.task.processor.command;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.dto.ParseResult;
import com.gov.landcheck.file.service.ParseJobUpdateService;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;
import com.gov.landcheck.file.service.parse.ParseRollbackSummary;
import com.gov.landcheck.file.task.base.AbstractCommand;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.base.TaskException;
import com.gov.landcheck.file.task.processor.receiver.DataParseReceiver;
import com.gov.landcheck.file.utils.GridFSUtils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据解析命令 - 执行数据解析操作
 *
 * @author system
 * @date 2026/01/27
 */
@Slf4j
@Component
public class ParseCommand extends AbstractCommand {

    @Resource
    private DataParseReceiver dataParseReceiver;

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private ParseJobUpdateService parseJobUpdateService;

    @Resource
    private GridFSUtils gridFSUtils;

    @Resource
    private ParseArtifactCleanupService parseArtifactCleanupService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ParseCommand() {
        super("数据解析", "PARSE");
    }

    @Override
    protected void doExecute(TaskData taskData) throws TaskException {
        log.info("开始数据解析: fileId={}", taskData.getFileRecord().getId());

        parseJobUpdateService.updateParseStarted(taskData.getParseJob());

        try {
            // 创建解析结果头
            ParsedDataHeader header = new ParsedDataHeader();
            header.setFileRecordId(taskData.getFileRecord().getId());
            header.setParseJobId(taskData.getParseJob().getId());
            header.setProjectId(taskData.getFileRecord().getProjectId());
            header.setFileType(taskData.getFileRecord().getFileType());
            header.setFileContextType(taskData.getFileRecord().getFileContextType());
            header.setOriginalFileName(taskData.getFileRecord().getOriginalName());
            header.setFileSize(taskData.getFileRecord().getFileSize());
            if (taskData.getParseJob() != null && StringUtils.hasText(taskData.getParseJob().getOcrResultPath())) {
                header.setOcrRawDataPath(taskData.getParseJob().getOcrResultPath());
            }
            header.markParseStarted();

            // 执行数据解析
            ParseResult parseResult = dataParseReceiver.parse(
                    resolveOcrPages(taskData),
                    taskData.getFileRecord(),
                    header);

            // 保存解析数据头
            header.markParseCompleted();
            header.setExecutionTimeMs(taskData.getExecutionTime());
            header.preSave();
            mongoTemplate.save(header);

            // 批量保存解析数据项（避免逐条 save）
            if (parseResult.getDataItems() != null && !parseResult.getDataItems().isEmpty()) {
                for (ParsedDataItem item : parseResult.getDataItems()) {
                    item.setHeaderId(header.getId());
                    item.preSave();
                }
                mongoTemplate.insertAll(parseResult.getDataItems());
            }

            // 更新任务数据
            taskData.setParsedDataItems(parseResult.getDataItems());
            taskData.setRoomInfos(parseResult.getRoomInfos());
            taskData.setParsedDataHeader(header);
            taskData.setPlanningReviewForm(parseResult.getPlanningReviewForm());
            taskData.setPlanningReviewRows(parseResult.getPlanningReviewRows());
            taskData.setProjectPartySummaryForm(parseResult.getProjectPartySummaryForm());
            taskData.setCapacityIndicatorInfo(parseResult.getCapacityIndicatorInfo());

            // 更新进度
            taskData.updateProgress(60);
            String llmResultPath = persistLlmResult(taskData, header, parseResult);
            parseJobUpdateService.updateParseCompleted(taskData.getParseJob(), llmResultPath);

            int itemCount = parseResult.getDataItems() != null ? parseResult.getDataItems().size() : 0;
            int prRows = parseResult.getPlanningReviewRows() != null ? parseResult.getPlanningReviewRows().size() : 0;
            log.info("数据解析完成: fileId={}, items={}, planningRows={}",
                    taskData.getFileRecord().getId(), itemCount, prRows);

        } catch (Exception e) {
            if (isCancellationInterrupt(taskData, e)) {
                throw new TaskException(
                        TaskException.ErrorCode.TASK_CANCELLED,
                        getStage(),
                        taskData.getFileRecord().getId(),
                        taskData.getParseJob() != null ? taskData.getParseJob().getId() : null,
                        "任务已取消，中止解析阶段: " + e.getMessage(),
                        e);
            }
            // 根据异常类型确定具体的错误码
            TaskException.ErrorCode errorCode = determineParseErrorCode(e);
            parseJobUpdateService.updateParseFailed(taskData.getParseJob(), e.getMessage());

            throw new TaskException(
                    errorCode,
                    getStage(),
                    taskData.getFileRecord().getId(),
                    taskData.getParseJob() != null ? taskData.getParseJob().getId() : null,
                    "数据解析失败: " + e.getMessage(),
                    e);
        }
    }

    private boolean isCancellationInterrupt(TaskData taskData, Exception exception) {
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }
        if (taskData != null && taskData.getTask() != null && taskData.getTask().shouldStop()) {
            return true;
        }
        Throwable current = exception;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            String simpleName = current.getClass().getSimpleName();
            if ("MongoInterruptedException".equals(simpleName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Override
    public ParseRollbackSummary rollback(TaskData taskData) throws TaskException {
        var summary = parseArtifactCleanupService.rollbackParse(taskData);
        if (summary.hasFailures()) {
            log.warn("解析阶段回滚存在失败项: fileRecordId={}, summary={}",
                    taskData != null && taskData.getFileRecord() != null ? taskData.getFileRecord().getId() : null,
                    summary);
        }
        return summary;
    }

    private List<OCRPageResult> resolveOcrPages(TaskData taskData) {
        if (taskData.getOcrProcessResult() == null || taskData.getOcrProcessResult().getPageResults() == null) {
            return java.util.Collections.emptyList();
        }
        return taskData.getOcrProcessResult().getPageResults();
    }

    private String persistLlmResult(TaskData taskData, ParsedDataHeader header, ParseResult parseResult) {
        if (header == null) {
            return "";
        }
        if (!StringUtils.hasText(header.getModelPrompt()) && !StringUtils.hasText(header.getModelAnalysisResult())) {
            return "";
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("fileRecordId", header.getFileRecordId());
            payload.put("parseJobId", header.getParseJobId());
            payload.put("projectId", header.getProjectId());
            payload.put("fileContextType",
                    header.getFileContextType() != null ? header.getFileContextType().name() : null);
            payload.put("parseEngine", parseResult != null ? parseResult.getParseEngine() : null);
            payload.put("modelPrompt", header.getModelPrompt());
            payload.put("modelAnalysisResult", header.getModelAnalysisResult());
            payload.put("modelRetryCount", header.getModelRetryCount());
            payload.put("savedAt", LocalDateTime.now().toString());

            String content = objectMapper.writeValueAsString(payload);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("llm_parse_result_%d_%s.json",
                    taskData.getFileRecord() != null ? taskData.getFileRecord().getId() : header.getFileRecordId(),
                    timestamp);
            String llmResultPath = gridFSUtils.uploadString(content, filename, "application/json");
            header.setLlmRawDataPath(llmResultPath);
            mongoTemplate.save(header);
            return llmResultPath;
        } catch (Exception ex) {
            log.warn("保存LLM解析结果到GridFS失败: fileId={}, error={}",
                    taskData.getFileRecord() != null ? taskData.getFileRecord().getId() : null, ex.getMessage());
            return "";
        }
    }

    /**
     * 根据异常类型确定解析错误码
     */
    private TaskException.ErrorCode determineParseErrorCode(Exception exception) {
        String message = exception.getMessage();
        if (message == null)
            message = "";

        String lowerMessage = message.toLowerCase();

        // 检查是否是TaskException，如果是则保持原有错误码
        if (exception instanceof TaskException) {
            return ((TaskException) exception).getErrorCode();
        }

        // 根据异常信息判断错误类型
        if (lowerMessage.contains("timeout") || lowerMessage.contains("time out")) {
            return TaskException.ErrorCode.PARSE_TIMEOUT;
        }

        if (lowerMessage.contains("invalid") || lowerMessage.contains("format") ||
                lowerMessage.contains("malformed") || lowerMessage.contains("parse")) {
            return TaskException.ErrorCode.PARSE_DATA_INVALID;
        }

        if (isNonRetryableParseBusinessFailure(exception, message)) {
            return TaskException.ErrorCode.PARSE_DATA_INVALID;
        }

        // 默认使用通用解析失败错误码
        return TaskException.ErrorCode.PARSE_FAILED;
    }

    private boolean isNonRetryableParseBusinessFailure(Exception exception, String message) {
        if (exception instanceof IllegalStateException) {
            return true;
        }
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("未解析")
                || message.contains("为空")
                || message.contains("不可恢复")
                || message.contains("缺少");
    }
}