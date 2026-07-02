package com.gov.landcheck.file.controller;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.file.dto.*;
import com.gov.landcheck.file.service.OtherDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;

import org.springframework.web.bind.annotation.*;

/**
 * 其他数据表管理控制器
 * 提供解析相关数据表的通用查询接口
 *
 * @author system
 * @date 2026/01/24
 */
@Tag(name = "其他数据表管理")
@RestController
@RequestMapping("/data-tables")
public class OtherDataController {

    @Resource
    private OtherDataService OtherDataService;

    @PostMapping("/parsed-data-headers/query")
    @Operation(summary = "解析数据头表通用查询", description = "根据多个条件组合查询解析数据头表信息，支持分页和排序")
    public AjaxJson queryParsedDataHeaders(@Parameter(description = "查询条件") @RequestBody ParsedDataHeaderQueryDTO queryDTO) {
        return OtherDataService.queryParsedDataHeaders(queryDTO);
    }

    @PostMapping("/parsed-data-items/query")
    @Operation(summary = "解析数据明细表通用查询", description = "根据多个条件组合查询解析数据明细表信息，支持分页和排序")
    public AjaxJson queryParsedDataItems(@Parameter(description = "查询条件") @RequestBody ParsedDataItemQueryDTO queryDTO) {
        return OtherDataService.queryParsedDataItems(queryDTO);
    }

    @PostMapping("/parse-jobs/query")
    @Operation(summary = "解析任务表通用查询", description = "根据多个条件组合查询解析任务表信息，支持分页和排序")
    public AjaxJson queryParseJobs(@Parameter(description = "查询条件") @RequestBody ParseJobQueryDTO queryDTO) {
        return OtherDataService.queryParseJobs(queryDTO);
    }

    @PostMapping("/ocr-execution-results/query")
    @Operation(summary = "OCR执行结果表通用查询", description = "根据多个条件组合查询OCR执行结果表信息，支持分页和排序")
    public AjaxJson queryOcrExecutionResults(@Parameter(description = "查询条件") @RequestBody OCRExecutionResultQueryDTO queryDTO) {
        return OtherDataService.queryOcrExecutionResults(queryDTO);
    }

}