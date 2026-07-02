package com.gov.landcheck.file.service;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.file.dto.*;

/**
 * 其他数据表服务接口
 * 提供解析相关数据表的查询服务
 *
 * @author system
 * @date 2026/01/24
 */
public interface OtherDataService {

    /**
     * 解析数据头表通用查询
     * 根据查询条件动态构建查询语句，支持分页和排序
     *
     * @param queryDTO 查询条件DTO
     * @return 分页查询结果
     */
    AjaxJson queryParsedDataHeaders(ParsedDataHeaderQueryDTO queryDTO);

    /**
     * 解析数据明细表通用查询
     * 根据查询条件动态构建查询语句，支持分页和排序
     *
     * @param queryDTO 查询条件DTO
     * @return 分页查询结果
     */
    AjaxJson queryParsedDataItems(ParsedDataItemQueryDTO queryDTO);

    /**
     * 解析任务表通用查询
     * 根据查询条件动态构建查询语句，支持分页和排序
     *
     * @param queryDTO 查询条件DTO
     * @return 分页查询结果
     */
    AjaxJson queryParseJobs(ParseJobQueryDTO queryDTO);

    /**
     * OCR执行结果表通用查询
     * 根据查询条件动态构建查询语句，支持分页和排序
     *
     * @param queryDTO 查询条件DTO
     * @return 分页查询结果
     */
    AjaxJson queryOcrExecutionResults(OCRExecutionResultQueryDTO queryDTO);

}