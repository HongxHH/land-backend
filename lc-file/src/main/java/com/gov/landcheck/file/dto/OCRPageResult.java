package com.gov.landcheck.file.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * OCR页面识别结果
 *
 * @author system
 * @date 2025/01/17
 */
@Data
public class OCRPageResult {

    /**
     * 页码 (从1开始)
     */
    private Integer pageNumber;

    /**
     * 原始OCR结果JSON
     */
    private String rawOcrResult;

    /**
     * 提取的Markdown文本
     */
    private String markdownText;

    /**
     * 提取的输出图像Base64列表
     */
    private List<String> outputImages;

    /**
     * 是否成功识别
     */
    private Boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    public OCRPageResult() {
        this.success = true;
    }

    public OCRPageResult(Integer pageNumber) {
        this.pageNumber = pageNumber;
        this.success = true;
    }

    public void markFailed(String errorMessage) {
        this.success = false;
        this.errorMessage = errorMessage;
    }
}