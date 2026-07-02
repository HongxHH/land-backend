package com.gov.landcheck.file.task.processor.receiver.ocr;

import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.file.dto.OCRProcessResult;
import com.gov.landcheck.file.task.base.TaskData;

/**
 * OCR 识别策略
 */
public interface OcrProcessingStrategy {

    /**
     * @return 是否处理该文件内容类型
     */
    boolean supports(FileContextType fileContextType);

    /**
     * 执行识别
     */
    OCRProcessResult process(TaskData taskData) throws Exception;
}
