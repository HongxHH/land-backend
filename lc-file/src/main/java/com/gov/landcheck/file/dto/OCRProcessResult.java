package com.gov.landcheck.file.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


/**
 * OCR处理结果DTO
 * 封装OCR处理后的结果数据
 *
 * @author system
 * @date 2025/01/18
 */
@Data
@NoArgsConstructor
public class OCRProcessResult {

    /**
     * OCR页面结果列表
     */
    private List<OCRPageResult> pageResults;

    /**
     * 处理耗时(毫秒)
     */
    private Long processingTimeMs;

    /**
     * 构造函数
     *
     * @param pageResults 页面结果列表
     */
    public OCRProcessResult(List<OCRPageResult> pageResults) {
        this.pageResults = pageResults;
    }
}