package com.gov.landcheck.file.task.processor.command;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.OCRExecutionResult;
import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.dto.OCRProcessResult;
import com.gov.landcheck.file.service.ParseJobUpdateService;
import com.gov.landcheck.file.service.parse.ParseArtifactCleanupService;
import com.gov.landcheck.file.service.parse.ParseRollbackSummary;
import com.gov.landcheck.file.task.base.AbstractCommand;
import com.gov.landcheck.file.task.base.TaskData;
import com.gov.landcheck.file.task.base.TaskException;
import com.gov.landcheck.file.task.processor.receiver.OcrStrategySelector;
import com.gov.landcheck.file.utils.GridFSUtils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 命令模式中角色：具体命令 - OCR识别命令
 * OCR命令 - 执行OCR识别操作
 *
 * @author system
 * @date 2026/01/27
 */
@Slf4j
@Component
public class OCRCommand extends AbstractCommand {

    @Resource
    private OcrStrategySelector ocrStrategySelector;

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private GridFSUtils gridFSUtils;

    @Resource
    private ParseJobUpdateService parseJobUpdateService;

    @Resource
    private ParseArtifactCleanupService parseArtifactCleanupService;

    public OCRCommand() {
        super("OCR识别", "OCR");
    }

    @Override
    protected void doExecute(TaskData taskData) throws TaskException {
        log.info("开始OCR识别: fileId={}", taskData.getFileRecord().getId());

        parseJobUpdateService.updateOcrStarted(taskData.getParseJob());

        FileRecord fileRecord = taskData.getFileRecord();

        try {
            // 使用预处理后的文件，如果没有则使用原始文件
            String targetGridfsId = StringUtils.hasText(taskData.getPreprocessGridfsId())
                    ? taskData.getPreprocessGridfsId()
                    : fileRecord.getGridfsId();

            log.debug("OCR使用文件进行识别: targetGridfsId={}", targetGridfsId);

            OCRProcessResult ocrResult = ocrStrategySelector
                    .select(fileRecord.getFileContextType())
                    .process(taskData);

            // 保存OCR结果到数据库和GridFS
            saveOCRResult(ocrResult, fileRecord.getId(), taskData.getParseJob().getId());

            // 更新任务数据
            taskData.setOcrProcessResult(ocrResult);

            parseJobUpdateService.updateOcrCompleted(taskData.getParseJob(), "OCR结果已保存");

            int pageCount = ocrResult.getPageResults() == null ? 0 : ocrResult.getPageResults().size();
            log.info("OCR识别完成: fileId={}, pages={}",
                    fileRecord.getId(), pageCount);

        } catch (Exception e) {
            TaskException.ErrorCode errorCode = determineOcrErrorCode(e);
            parseJobUpdateService.updateOcrFailed(taskData.getParseJob(), e.getMessage());
            throw new TaskException(
                    errorCode,
                    getStage(),
                    fileRecord.getId(),
                    taskData.getParseJob() != null ? taskData.getParseJob().getId() : null,
                    "OCR识别失败: " + e.getMessage(),
                    e);
        }
    }

    @Override
    public ParseRollbackSummary rollback(TaskData taskData) throws TaskException {
        var summary = parseArtifactCleanupService.rollbackOcr(taskData);
        if (summary.hasFailures()) {
            log.warn("OCR阶段回滚存在失败项: fileId={}, summary={}",
                    taskData != null && taskData.getFileRecord() != null ? taskData.getFileRecord().getId() : null,
                    summary);
        }
        return summary;
    }

    private void saveOCRResult(OCRProcessResult ocrResult, Long fileId, Long parseJobId) throws TaskException {
        String ocrResultJsonGridfsId = null;
        String markdownFileGridfsId = null;
        try {
            ocrResultJsonGridfsId = saveOCRResultToGridFS(ocrResult, fileId);
            markdownFileGridfsId = saveOCRMarkdownToGridFS(ocrResult, fileId);

            if (!StringUtils.hasText(ocrResultJsonGridfsId) && !StringUtils.hasText(markdownFileGridfsId)) {
                throw new TaskException(
                        TaskException.ErrorCode.IO_ERROR,
                        getStage(),
                        fileId,
                        parseJobId,
                        "OCR结果未能写入 GridFS（JSON 与 Markdown 均为空）");
            }

            OCRExecutionResult executionResult = new OCRExecutionResult();
            executionResult.setFileRecordId(fileId);
            executionResult.setParseJobId(parseJobId);
            executionResult.setOcrResultJsonGridfsId(ocrResultJsonGridfsId);
            executionResult.setMarkdownFileGridfsId(markdownFileGridfsId);
            executionResult.setPageCount(ocrResult.getPageResults() != null ? ocrResult.getPageResults().size() : 0);
            executionResult.setProcessingTimeMs(ocrResult.getProcessingTimeMs());
            executionResult.setExecutionTime(LocalDateTime.now());
            executionResult.preSave();
            mongoTemplate.save(executionResult);

            log.info("OCR结果保存完成: fileId={}, ocrResultGridfsId={}, markdownGridfsId={}",
                    fileId, ocrResultJsonGridfsId, markdownFileGridfsId);

        } catch (TaskException e) {
            safeDeleteQuiet(ocrResultJsonGridfsId);
            safeDeleteQuiet(markdownFileGridfsId);
            throw e;
        } catch (Exception e) {
            safeDeleteQuiet(ocrResultJsonGridfsId);
            safeDeleteQuiet(markdownFileGridfsId);
            throw new TaskException(
                    TaskException.ErrorCode.IO_ERROR,
                    getStage(),
                    fileId,
                    parseJobId,
                    "保存OCR结果失败: " + e.getMessage(),
                    e);
        }
    }

    private void safeDeleteQuiet(String gridfsId) {
        if (!StringUtils.hasText(gridfsId)) {
            return;
        }
        try {
            gridFSUtils.deleteById(gridfsId.trim());
        } catch (Exception ex) {
            log.warn("OCR保存失败补偿删除 GridFS 失败: gridfsId={}, error={}", gridfsId, ex.getMessage());
        }
    }

    /**
     * 保存OCR结果JSON到GridFS
     */
    private String saveOCRResultToGridFS(OCRProcessResult ocrResult, Long fileId) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String jsonContent = mapper.writeValueAsString(ocrResult);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("ocr_result_%d_%s.json", fileId, timestamp);
        String gridfsId = gridFSUtils.uploadString(jsonContent, filename, "application/json");
        log.debug("OCR结果JSON已保存到GridFS: fileId={}, gridfsId={}", fileId, gridfsId);
        return gridfsId;
    }

    /**
     * 保存OCR结果中的Markdown内容到GridFS
     */
    private String saveOCRMarkdownToGridFS(OCRProcessResult ocrResult, Long fileId) {
        try {
            if (ocrResult.getPageResults() == null || ocrResult.getPageResults().isEmpty()) {
                log.warn("OCR结果中没有页面数据，跳过Markdown保存: fileId={}", fileId);
                return null;
            }

            // 提取所有页面的Markdown内容
            StringBuilder markdownContent = new StringBuilder();
            markdownContent.append("# OCR识别结果 - Markdown内容\n\n");
            markdownContent.append(String.format("文件ID: %d\n", fileId));
            markdownContent.append(String.format("生成时间: %s\n", LocalDateTime.now()));
            markdownContent.append(String.format("页面数量: %d\n\n", ocrResult.getPageResults().size()));
            markdownContent.append("---\n\n");

            for (int i = 0; i < ocrResult.getPageResults().size(); i++) {
                OCRPageResult pageResult = ocrResult.getPageResults().get(i);
                // 直接使用markdownText字段，它已经包含了完整的markdown内容
                if (pageResult.getMarkdownText() != null && !pageResult.getMarkdownText().trim().isEmpty()) {
                    markdownContent.append(pageResult.getMarkdownText());
                    markdownContent.append("\n\n");
                } else {
                    log.warn("第{}页OCR识别的markdownText为空或null", i + 1);
                    markdownContent.append("*未找到Markdown内容*\n\n");
                }

                markdownContent.append("---\n\n");
            }

            // 生成文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("ocr_markdown_%d_%s.md", fileId, timestamp);

            // 上传到GridFS
            String gridfsId = gridFSUtils.uploadString(markdownContent.toString(), filename, "text/markdown");

            log.debug("OCR Markdown内容已保存到GridFS: fileId={}, gridfsId={}", fileId, gridfsId);
            return gridfsId;

        } catch (Exception e) {
            log.warn("保存OCR Markdown内容到GridFS失败: fileId={}, error={}", fileId, e.getMessage());
            return null;
        }
    }

    /**
     * 根据异常类型确定OCR错误码
     */
    private TaskException.ErrorCode determineOcrErrorCode(Exception exception) {
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
            return TaskException.ErrorCode.OCR_TIMEOUT;
        }

        if (lowerMessage.contains("api") || lowerMessage.contains("http") ||
                lowerMessage.contains("connection") || lowerMessage.contains("network")) {
            return TaskException.ErrorCode.OCR_API_ERROR;
        }

        if (lowerMessage.contains("invalid") || lowerMessage.contains("format") ||
                lowerMessage.contains("parse")) {
            return TaskException.ErrorCode.OCR_INVALID_INPUT;
        }

        // 默认使用通用OCR失败错误码
        return TaskException.ErrorCode.OCR_FAILED;
    }
}