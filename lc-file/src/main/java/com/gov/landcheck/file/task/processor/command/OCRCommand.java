package com.gov.landcheck.file.task.processor.command;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.bo.entity.FileRecord;
import com.gov.landcheck.core.bo.entity.OCRExecutionResult;
import com.gov.landcheck.core.bo.entity.ParseJob;
import com.gov.landcheck.file.dto.OCRPageResult;
import com.gov.landcheck.file.dto.OCRProcessResult;
import com.gov.landcheck.file.service.ParseJobUpdateService;
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

    /**
     * OCR 阶段回滚：
     * - 删除本次解析任务产生的 OCRExecutionResult 记录
     * - 同时删除其关联的 JSON / Markdown GridFS 文件
     * - 清理 TaskData 中的 OCR 结果缓存
     */
    @Override
    public void rollback(TaskData taskData) throws TaskException {
        if (taskData == null || taskData.getFileRecord() == null || taskData.getParseJob() == null) {
            return;
        }

        FileRecord fileRecord = taskData.getFileRecord();
        ParseJob parseJob = taskData.getParseJob();

        Long fileId = fileRecord.getId();
        Long parseJobId = parseJob.getId();

        try {
            Query query = new Query(Criteria.where("parse_job_id").is(parseJobId));
            List<OCRExecutionResult> results = mongoTemplate.find(query, OCRExecutionResult.class);

            if (results == null || results.isEmpty()) {
                log.debug("OCR阶段回滚：未找到需要删除的 OCRExecutionResult, fileId={}, parseJobId={}",
                        fileId, parseJobId);
            } else {
                for (OCRExecutionResult executionResult : results) {
                    // 1. 删除OCR结果JSON文件
                    String jsonGridfsId = executionResult.getOcrResultJsonGridfsId();
                    if (jsonGridfsId != null && !jsonGridfsId.trim().isEmpty()) {
                        try {
                            gridFSUtils.deleteById(jsonGridfsId);
                            log.debug("OCR阶段回滚：已删除 OCR JSON 文件, fileId={}, gridfsId={}", fileId, jsonGridfsId);
                        } catch (Exception ex) {
                            log.warn("OCR阶段回滚：删除 OCR JSON 失败, fileId={}, gridfsId={}, error={}",
                                    fileId, jsonGridfsId, ex.getMessage());
                        }
                    }

                    // 2. 删除OCR结果Markdown文件
                    String markdownGridfsId = executionResult.getMarkdownFileGridfsId();
                    if (markdownGridfsId != null && !markdownGridfsId.trim().isEmpty()) {
                        try {
                            gridFSUtils.deleteById(markdownGridfsId);
                            log.debug("OCR阶段回滚：已删除 OCR Markdown 文件, fileId={}, gridfsId={}",
                                    fileId, markdownGridfsId);
                        } catch (Exception ex) {
                            log.warn("OCR阶段回滚：删除 OCR Markdown 失败, fileId={}, gridfsId={}, error={}",
                                    fileId, markdownGridfsId, ex.getMessage());
                        }
                    }

                    // 3. 删除OCRExecutionResult记录
                    mongoTemplate.remove(executionResult);
                    log.info("OCR阶段回滚：已删除 OCRExecutionResult, fileId={}, parseJobId={}, executionResultId={}",
                            fileId, parseJobId, executionResult.getId());
                }
            }
        } catch (Exception ex) {
            log.warn("OCR阶段回滚失败: fileId={}, parseJobId={}, error={}", fileId, parseJobId, ex.getMessage());
        }

        // 清理任务上下文中的 OCR 结果，避免后续命令误用
        taskData.setOcrProcessResult(null);
    }

    /**
     * 保存OCR结果到数据库和GridFS
     */
    private void saveOCRResult(OCRProcessResult ocrResult, Long fileId, Long parseJobId) throws TaskException {
        try {
            // 创建OCR执行结果记录
            OCRExecutionResult executionResult = new OCRExecutionResult();
            executionResult.setFileRecordId(fileId);
            executionResult.setParseJobId(parseJobId);

            // 1. 保存OCR结果JSON到GridFS
            String ocrResultJsonGridfsId = saveOCRResultToGridFS(ocrResult, fileId);
            executionResult.setOcrResultJsonGridfsId(ocrResultJsonGridfsId);

            // 2. 保存Markdown内容到GridFS
            String markdownFileGridfsId = saveOCRMarkdownToGridFS(ocrResult, fileId);
            executionResult.setMarkdownFileGridfsId(markdownFileGridfsId);

            // 设置统计信息
            executionResult.setPageCount(ocrResult.getPageResults() != null ? ocrResult.getPageResults().size() : 0);
            executionResult.setProcessingTimeMs(ocrResult.getProcessingTimeMs());
            executionResult.setExecutionTime(LocalDateTime.now());

            // 保存到数据库
            executionResult.preSave();
            mongoTemplate.save(executionResult);

            log.info("OCR结果保存完成: fileId={}, ocrResultGridfsId={}, markdownGridfsId={}",
                    fileId, ocrResultJsonGridfsId, markdownFileGridfsId);

        } catch (Exception e) {
            throw new TaskException(
                    TaskException.ErrorCode.IO_ERROR,
                    getStage(),
                    fileId,
                    parseJobId,
                    "保存OCR结果失败: " + e.getMessage(),
                    e);
        }
    }

    /**
     * 保存OCR结果JSON到GridFS
     */
    private String saveOCRResultToGridFS(OCRProcessResult ocrResult, Long fileId) {
        try {
            // 序列化完整版OCR结果为JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonContent = mapper.writeValueAsString(ocrResult);

            // 生成文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("ocr_result_%d_%s.json", fileId, timestamp);

            // 上传到GridFS
            String gridfsId = gridFSUtils.uploadString(jsonContent, filename, "application/json");

            log.debug("OCR结果JSON已保存到GridFS: fileId={}, gridfsId={}", fileId, gridfsId);
            return gridfsId;

        } catch (Exception e) {
            log.warn("保存OCR结果到GridFS失败: fileId={}, error={}", fileId, e.getMessage());
            return null;
        }
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

    /**
     * 更新解析任务状态
     */
    public void updateParseJobStatus(ParseJob parseJob) {
        try {
            mongoTemplate.save(parseJob);
        } catch (Exception e) {
            log.error("更新解析任务状态失败: parseJobId={}, error={}",
                    parseJob.getId(), e.getMessage());
        }
    }

}