package com.gov.landcheck.file.service.impl;

import java.util.List;

import com.gov.landcheck.file.service.OtherDataService;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.bo.entity.OCRExecutionResult;
import com.gov.landcheck.core.bo.entity.ParsedDataHeader;
import com.gov.landcheck.core.common.MessageConstant;
import com.gov.landcheck.core.config.query.MongoRegexCriteria;
import com.gov.landcheck.core.config.query.MongoSortFields;
import com.gov.landcheck.core.config.query.SafePageSort;
import com.gov.landcheck.file.dto.*;
import com.gov.landcheck.file.utils.GridFSUtils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 解析数据头表服务实现
 *
 * @author system
 * @date 2026/01/24
 */
@Service
@Slf4j
public class OtherDataServiceImpl implements OtherDataService {

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private GridFSUtils gridFSUtils;

    @Override
    public AjaxJson queryParsedDataHeaders(ParsedDataHeaderQueryDTO queryDTO) {
        try {
            // 构建查询条件
            Criteria criteria = new Criteria();

            // 文件记录ID查询
            if (queryDTO.getFileRecordId() != null) {
                criteria.and("file_record_id").is(queryDTO.getFileRecordId());
            }

            // 解析任务ID查询
            if (queryDTO.getParseJobId() != null) {
                criteria.and("parse_job_id").is(queryDTO.getParseJobId());
            }

            // 项目ID查询
            if (queryDTO.getProjectId() != null) {
                criteria.and("project_id").is(queryDTO.getProjectId());
            }

            // 文件类型查询
            if (queryDTO.getFileType() != null) {
                criteria.and("file_type").is(queryDTO.getFileType());
            }

            // 文件内容类型查询
            if (queryDTO.getFileContextType() != null) {
                criteria.and("file_context_type").is(queryDTO.getFileContextType());
            }

            // 解析状态查询
            if (queryDTO.getParseStatus() != null) {
                criteria.and("parse_status_code").is(queryDTO.getParseStatus());
            }

            // 解析开始时间范围查询
            if (queryDTO.getParseStartTimeStart() != null || queryDTO.getParseStartTimeEnd() != null) {
                Criteria parseStartTimeCriteria = new Criteria();
                if (queryDTO.getParseStartTimeStart() != null && queryDTO.getParseStartTimeEnd() != null) {
                    parseStartTimeCriteria.and("parse_start_time").gte(queryDTO.getParseStartTimeStart())
                            .lte(queryDTO.getParseStartTimeEnd());
                } else if (queryDTO.getParseStartTimeStart() != null) {
                    parseStartTimeCriteria.and("parse_start_time").gte(queryDTO.getParseStartTimeStart());
                } else if (queryDTO.getParseStartTimeEnd() != null) {
                    parseStartTimeCriteria.and("parse_start_time").lte(queryDTO.getParseStartTimeEnd());
                }
                criteria.andOperator(parseStartTimeCriteria);
            }

            // 解析完成时间范围查询
            if (queryDTO.getParseEndTimeStart() != null || queryDTO.getParseEndTimeEnd() != null) {
                Criteria parseEndTimeCriteria = new Criteria();
                if (queryDTO.getParseEndTimeStart() != null && queryDTO.getParseEndTimeEnd() != null) {
                    parseEndTimeCriteria.and("parse_end_time").gte(queryDTO.getParseEndTimeStart())
                            .lte(queryDTO.getParseEndTimeEnd());
                } else if (queryDTO.getParseEndTimeStart() != null) {
                    parseEndTimeCriteria.and("parse_end_time").gte(queryDTO.getParseEndTimeStart());
                } else if (queryDTO.getParseEndTimeEnd() != null) {
                    parseEndTimeCriteria.and("parse_end_time").lte(queryDTO.getParseEndTimeEnd());
                }
                criteria.andOperator(parseEndTimeCriteria);
            }

            // 解析引擎查询
            if (StringUtils.hasText(queryDTO.getParseEngine())) {
                criteria.and("parse_engine").is(queryDTO.getParseEngine());
            }

            // 原始文件名模糊查询
            addSafeRegex(criteria, "original_file_name", queryDTO.getOriginalFileName());

            // 是否为扫描件查询
            if (queryDTO.getIsScanned() != null) {
                criteria.and("is_scanned").is(queryDTO.getIsScanned());
            }

            // 大模型名称查询
            if (StringUtils.hasText(queryDTO.getLlmModel())) {
                criteria.and("llm_model").is(queryDTO.getLlmModel());
            }

            // 执行时间范围查询
            if (queryDTO.getExecutionTimeMsStart() != null || queryDTO.getExecutionTimeMsEnd() != null) {
                Criteria executionTimeCriteria = new Criteria();
                if (queryDTO.getExecutionTimeMsStart() != null && queryDTO.getExecutionTimeMsEnd() != null) {
                    executionTimeCriteria.and("execution_time_ms").gte(queryDTO.getExecutionTimeMsStart())
                            .lte(queryDTO.getExecutionTimeMsEnd());
                } else if (queryDTO.getExecutionTimeMsStart() != null) {
                    executionTimeCriteria.and("execution_time_ms").gte(queryDTO.getExecutionTimeMsStart());
                } else if (queryDTO.getExecutionTimeMsEnd() != null) {
                    executionTimeCriteria.and("execution_time_ms").lte(queryDTO.getExecutionTimeMsEnd());
                }
                criteria.andOperator(executionTimeCriteria);
            }

            // 构建查询对象
            Query query = new Query(criteria);

            // 排序
            Sort sort = SafePageSort.resolve(
                    queryDTO.getSortDirection(),
                    queryDTO.getSortField(),
                    "createTime",
                    MongoSortFields.PARSED_DATA);
            query.with(sort);

            // 分页
            int pageNum = queryDTO.getPageNum() != null && queryDTO.getPageNum() > 0 ? queryDTO.getPageNum() : 1;
            int pageSize = queryDTO.getPageSize() != null && queryDTO.getPageSize() > 0 ? queryDTO.getPageSize() : 10;

            long total = mongoTemplate.count(query, ParsedDataHeader.class);
            query.skip((long) (pageNum - 1) * pageSize).limit(pageSize);

            // 执行查询
            List<ParsedDataHeader> records = mongoTemplate.find(query, ParsedDataHeader.class);

            // 构建分页结果
            ParsedDataHeaderQueryResultDTO result = new ParsedDataHeaderQueryResultDTO();
            result.setRecords(records);
            result.setCurrent(pageNum);
            result.setSize(pageSize);
            result.setTotal(total);
            result.setPages((int) Math.ceil((double) total / pageSize));

            return AjaxJson.getSuccess("查询成功").setData(result);

        } catch (Exception e) {
            log.error("解析数据头表查询失败: error={}", e.getMessage(), e);
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "解析数据头表查询失败");
        }
    }

    @Override
    public AjaxJson queryParsedDataItems(ParsedDataItemQueryDTO queryDTO) {
        try {
            Criteria criteria = new Criteria();

            // 构建查询条件
            if (queryDTO.getHeaderId() != null) {
                criteria.and("header_id").is(queryDTO.getHeaderId());
            }
            addSafeRegex(criteria, "data_category", queryDTO.getDataCategory());
            addSafeRegex(criteria, "field_key", queryDTO.getFieldKey());
            addSafeRegex(criteria, "field_value", queryDTO.getFieldValue());
            addSafeRegex(criteria, "normalized_key", queryDTO.getNormalizedKey());
            addSafeRegex(criteria, "normalized_value", queryDTO.getNormalizedValue());
            if (StringUtils.hasText(queryDTO.getFieldType())) {
                criteria.and("field_type").is(queryDTO.getFieldType());
            }
            if (StringUtils.hasText(queryDTO.getExtractionMethod())) {
                criteria.and("extraction_method").is(queryDTO.getExtractionMethod());
            }
            if (StringUtils.hasText(queryDTO.getDataSource())) {
                criteria.and("data_source").is(queryDTO.getDataSource());
            }
            if (StringUtils.hasText(queryDTO.getValidationStatus())) {
                criteria.and("validation_status").is(queryDTO.getValidationStatus());
            }
            if (queryDTO.getIsModified() != null) {
                criteria.and("is_modified").is(queryDTO.getIsModified());
            }

            Query query = new Query(criteria);

            int pageNum = resolvePageNum(queryDTO.getPageNum());
            int pageSize = resolvePageSize(queryDTO.getPageSize());
            Sort sort = SafePageSort.resolve(
                    queryDTO.getSortDirection(),
                    queryDTO.getSortField(),
                    "createTime",
                    MongoSortFields.PARSED_DATA);
            Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
            query.with(pageable);

            // 执行查询
            List<com.gov.landcheck.core.bo.entity.ParsedDataItem> parsedDataItems = mongoTemplate.find(query,
                    com.gov.landcheck.core.bo.entity.ParsedDataItem.class);
            long total = mongoTemplate.count(query.skip(-1).limit(-1),
                    com.gov.landcheck.core.bo.entity.ParsedDataItem.class);

            ParsedDataItemQueryResultDTO result = new ParsedDataItemQueryResultDTO();
            result.setRecords(parsedDataItems);
            result.setCurrent(pageNum);
            result.setSize(pageSize);
            result.setTotal(total);
            result.setPages((int) Math.ceil((double) total / pageSize));

            return AjaxJson.getSuccess("查询成功").setData(result);
        } catch (Exception e) {
            log.error("解析数据明细表查询失败: error={}", e.getMessage(), e);
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "解析数据明细表查询失败");
        }
    }

    @Override
    public AjaxJson queryParseJobs(ParseJobQueryDTO queryDTO) {
        try {
            Criteria criteria = new Criteria();

            // 构建查询条件
            if (queryDTO.getFileRecordId() != null) {
                criteria.and("file_record_id").is(queryDTO.getFileRecordId());
            }
            if (queryDTO.getJobStatus() != null) {
                criteria.and("job_status").is(queryDTO.getJobStatus());
            }
            addSafeRegex(criteria, "worker_node", queryDTO.getWorkerNode());
            if (queryDTO.getAttemptCount() != null) {
                criteria.and("attempt_count").is(queryDTO.getAttemptCount());
            }
            if (queryDTO.getProgress() != null) {
                criteria.and("progress").is(queryDTO.getProgress());
            }
            if (queryDTO.getStartedAtStart() != null || queryDTO.getStartedAtEnd() != null) {
                Criteria timeCriteria = Criteria.where("started_at");
                if (queryDTO.getStartedAtStart() != null) {
                    timeCriteria.gte(queryDTO.getStartedAtStart());
                }
                if (queryDTO.getStartedAtEnd() != null) {
                    timeCriteria.lte(queryDTO.getStartedAtEnd());
                }
                criteria.andOperator(timeCriteria);
            }
            if (queryDTO.getFinishedAtStart() != null || queryDTO.getFinishedAtEnd() != null) {
                Criteria timeCriteria = Criteria.where("finished_at");
                if (queryDTO.getFinishedAtStart() != null) {
                    timeCriteria.gte(queryDTO.getFinishedAtStart());
                }
                if (queryDTO.getFinishedAtEnd() != null) {
                    timeCriteria.lte(queryDTO.getFinishedAtEnd());
                }
                criteria.andOperator(timeCriteria);
            }
            if (StringUtils.hasText(queryDTO.getPreprocessStatus())) {
                criteria.and("preprocess_status").is(queryDTO.getPreprocessStatus());
            }
            if (StringUtils.hasText(queryDTO.getOcrStatus())) {
                criteria.and("ocr_status").is(queryDTO.getOcrStatus());
            }

            Query query = new Query(criteria);

            int pageNum = resolvePageNum(queryDTO.getPageNum());
            int pageSize = resolvePageSize(queryDTO.getPageSize());
            Sort sort = SafePageSort.resolve(
                    queryDTO.getSortDirection(),
                    queryDTO.getSortField(),
                    "createTime",
                    MongoSortFields.PARSE_JOB);
            Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
            query.with(pageable);

            // 执行查询
            List<com.gov.landcheck.core.bo.entity.ParseJob> parseJobs = mongoTemplate.find(query,
                    com.gov.landcheck.core.bo.entity.ParseJob.class);
            long total = mongoTemplate.count(query.skip(-1).limit(-1), com.gov.landcheck.core.bo.entity.ParseJob.class);

            ParseJobQueryResultDTO result = new ParseJobQueryResultDTO();
            result.setRecords(parseJobs);
            result.setCurrent(pageNum);
            result.setSize(pageSize);
            result.setTotal(total);
            result.setPages((int) Math.ceil((double) total / pageSize));

            return AjaxJson.getSuccess("查询成功").setData(result);
        } catch (Exception e) {
            log.error("解析任务表查询失败: error={}", e.getMessage(), e);
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "解析任务表查询失败");
        }
    }

    @Override
    public AjaxJson queryOcrExecutionResults(OCRExecutionResultQueryDTO queryDTO) {
        try {
            Criteria criteria = new Criteria();

            // 构建查询条件
            if (queryDTO.getFileRecordId() != null) {
                criteria.and("file_record_id").is(queryDTO.getFileRecordId());
            }
            if (queryDTO.getParseJobId() != null) {
                criteria.and("parse_job_id").is(queryDTO.getParseJobId());
            }
            if (queryDTO.getPageCount() != null) {
                criteria.and("page_count").is(queryDTO.getPageCount());
            }
            if (queryDTO.getAverageConfidenceStart() != null || queryDTO.getAverageConfidenceEnd() != null) {
                Criteria confidenceCriteria = Criteria.where("average_confidence");
                if (queryDTO.getAverageConfidenceStart() != null) {
                    confidenceCriteria.gte(queryDTO.getAverageConfidenceStart());
                }
                if (queryDTO.getAverageConfidenceEnd() != null) {
                    confidenceCriteria.lte(queryDTO.getAverageConfidenceEnd());
                }
                criteria.andOperator(confidenceCriteria);
            }
            if (queryDTO.getProcessingTimeMsStart() != null || queryDTO.getProcessingTimeMsEnd() != null) {
                Criteria timeCriteria = Criteria.where("processing_time_ms");
                if (queryDTO.getProcessingTimeMsStart() != null) {
                    timeCriteria.gte(queryDTO.getProcessingTimeMsStart());
                }
                if (queryDTO.getProcessingTimeMsEnd() != null) {
                    timeCriteria.lte(queryDTO.getProcessingTimeMsEnd());
                }
                criteria.andOperator(timeCriteria);
            }
            if (queryDTO.getExecutionTimeStart() != null || queryDTO.getExecutionTimeEnd() != null) {
                Criteria timeCriteria = Criteria.where("execution_time");
                if (queryDTO.getExecutionTimeStart() != null) {
                    timeCriteria.gte(queryDTO.getExecutionTimeStart());
                }
                if (queryDTO.getExecutionTimeEnd() != null) {
                    timeCriteria.lte(queryDTO.getExecutionTimeEnd());
                }
                criteria.andOperator(timeCriteria);
            }

            Query query = new Query(criteria);

            int pageNum = resolvePageNum(queryDTO.getPageNum());
            int pageSize = resolvePageSize(queryDTO.getPageSize());
            Sort sort = SafePageSort.resolve(
                    queryDTO.getSortDirection(),
                    queryDTO.getSortField(),
                    "createTime",
                    MongoSortFields.OCR_RESULT);
            Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
            query.with(pageable);

            // 执行查询
            List<OCRExecutionResult> ocrExecutionResults = mongoTemplate.find(query, OCRExecutionResult.class);
            long total = mongoTemplate.count(query.skip(-1).limit(-1), OCRExecutionResult.class);

            // 列表默认仅返回 GridFS 引用；详情场景可通过 loadGridFsPayload 加载正文
            boolean loadGridFsPayload = Boolean.TRUE.equals(queryDTO.getLoadGridFsPayload());
            List<OCRExecutionResultResponseDTO> responseRecords = ocrExecutionResults.stream()
                    .map(e -> convertToResponseDTO(e, loadGridFsPayload))
                    .toList();

            OCRExecutionResultQueryResultDTO result = new OCRExecutionResultQueryResultDTO();
            result.setRecords(responseRecords);
            result.setCurrent(pageNum);
            result.setSize(pageSize);
            result.setTotal(total);
            result.setPages((int) Math.ceil((double) total / pageSize));

            return AjaxJson.getSuccess("查询成功").setData(result);
        } catch (Exception e) {
            log.error("OCR执行结果表查询失败: error={}", e.getMessage(), e);
            return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "OCR执行结果表查询失败");
        }
    }

    private int resolvePageNum(Integer pageNum) {
        return (pageNum != null && pageNum > 0) ? pageNum : 1;
    }

    private int resolvePageSize(Integer pageSize) {
        return (pageSize != null && pageSize > 0) ? pageSize : 10;
    }

    private static void addSafeRegex(Criteria criteria, String mongoField, String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return;
        }
        criteria.andOperator(MongoRegexCriteria.like(mongoField, rawValue));
    }

    /**
     * 将OCRExecutionResult实体转换为响应DTO
     *
     * @param loadGridFsPayload 为 true 时读取 GridFS 中的 JSON/Markdown 大对象（详情场景）；列表查询应传
     *                          false
     */
    private OCRExecutionResultResponseDTO convertToResponseDTO(OCRExecutionResult entity, boolean loadGridFsPayload) {
        OCRExecutionResultResponseDTO dto = new OCRExecutionResultResponseDTO();
        dto.setId(entity.getId());
        dto.setFileRecordId(entity.getFileRecordId());
        dto.setParseJobId(entity.getParseJobId());
        dto.setPageCount(entity.getPageCount());
        dto.setProcessingTimeMs(entity.getProcessingTimeMs());
        dto.setExecutionTime(entity.getExecutionTime());
        dto.setOcrResultJsonGridfsId(entity.getOcrResultJsonGridfsId());
        dto.setMarkdownFileGridfsId(entity.getMarkdownFileGridfsId());

        if (!loadGridFsPayload) {
            return dto;
        }

        // 从GridFS获取OCR结果JSON内容
        if (StringUtils.hasText(entity.getOcrResultJsonGridfsId())) {
            try {
                byte[] jsonBytes = gridFSUtils.getFileBytes(entity.getOcrResultJsonGridfsId());
                dto.setOcrResultJson(new String(jsonBytes, java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.warn("获取OCR结果JSON内容失败: gridfsId={}, error={}", entity.getOcrResultJsonGridfsId(), e.getMessage());
                dto.setOcrResultJson("获取失败: " + e.getMessage());
            }
        }

        // 从GridFS获取Markdown内容
        if (StringUtils.hasText(entity.getMarkdownFileGridfsId())) {
            try {
                byte[] markdownBytes = gridFSUtils.getFileBytes(entity.getMarkdownFileGridfsId());
                dto.setMarkdownContent(new String(markdownBytes, java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.warn("获取Markdown内容失败: gridfsId={}, error={}", entity.getMarkdownFileGridfsId(), e.getMessage());
                dto.setMarkdownContent("获取失败: " + e.getMessage());
            }
        }

        return dto;
    }
}