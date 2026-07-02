package com.gov.landcheck.core.bo.entity;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;
import com.gov.landcheck.core.enums.FileContextType;
import com.gov.landcheck.core.enums.FileStateEnum;
import com.gov.landcheck.core.enums.FileType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 解析数据头表实体类（一次解析对应一条记录）
 *
 * @author system
 * @date 2025/12/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "parsed_data_header")
@Schema(description = "解析数据头表")
public class ParsedDataHeader extends MongoIdEntity {

    @Field(name = "file_record_id")
    @Schema(description = "关联 file_record.id（源文件）")
    private Long fileRecordId;

    @Field(name = "parse_job_id")
    @Schema(description = "关联 parse_job.id")
    private Long parseJobId;

    @Field(name = "project_id")
    @Schema(description = "项目ID")
    private Long projectId;

    @Field(name = "file_type")
    @Schema(description = "文件类型（引用 FileType 枚举，表示文件格式：PDF/JPG/XLS等）", example = "PDF")
    private FileType fileType;

    @Field(name = "file_context_type")
    @Schema(description = "文件内容类型（CONTRACT/SURVEY）", example = "CONTRACT")
    private FileContextType fileContextType;

    @Field(name = "parse_status_code")
    @Schema(description = "解析状态", example = "PARSE_COMPLETE")
    private FileStateEnum parseStatus;

    @Field(name = "parse_start_time")
    @Schema(description = "解析开始时间")
    private LocalDateTime parseStartTime;

    @Field(name = "parse_end_time")
    @Schema(description = "解析完成时间")
    private LocalDateTime parseEndTime;

    @Field(name = "parse_engine")
    @Schema(description = "使用的解析引擎或模板（如 OCR-v1 / template-2025-01 / LLM-GPT4）", example = "OCR-v1+LLM-GPT4")
    private String parseEngine;

    // ========== 文件信息 ==========
    @Field(name = "original_file_name")
    @Schema(description = "原始文件名")
    private String originalFileName;

    @Field(name = "file_size")
    @Schema(description = "文件大小(字节)")
    private Long fileSize;

    @Field(name = "is_scanned")
    @Schema(description = "是否为扫描件（0否 1是）")
    private Integer isScanned;

    // ========== 预处理信息 ==========

    @Field(name = "preprocess_gridfs_id")
    @Schema(description = "预处理后PDF的GridFS ID")
    private String preprocessGridfsId;
    // ========== OCR信息 ==========
    @Field(name = "ocr_info")
    @Schema(description = "OCR识别信息")
    private String ocrInfo;

    @Field(name = "llm_model")
    @Schema(description = "大模型名称")
    private String llmModel;

    @Field(name = "llm_api_url")
    @Schema(description = "大模型API地址")
    private String llmApiUrl;

    // ========== 数据解析 ==========
    @Field(name = "model_prompt")
    @Schema(description = "发送给大模型的提示词")
    private String modelPrompt;

    @Field(name = "model_analysis_result")
    @Schema(description = "模型分析用途返回的结果json")
    private String modelAnalysisResult;

    @Field(name = "model_retry_count")
    @Schema(description = "模型重试次数")
    private Integer modelRetryCount;

    // ========== 数据存储 ==========
    @Field(name = "ocr_raw_data_path")
    @Schema(description = "OCR原始数据存储路径（JSON格式）")
    private String ocrRawDataPath;

    @Field(name = "llm_raw_data_path")
    @Schema(description = "LLM原始返回数据存储路径（JSON格式）")
    private String llmRawDataPath;

    @Field(name = "markdown_data_path")
    @Schema(description = "Markdown数据存储路径")
    private String markdownDataPath;

    @Field(name = "execution_time_ms")
    @Schema(description = "执行时间(毫秒)", example = "180000")
    private Long executionTimeMs;

    public ParsedDataHeader() {
        this.isScanned = 0;
    }

    /**
     * 初始化解析开始
     */
    public void markParseStarted() {
        this.parseStatus = FileStateEnum.PARSING;
        this.parseStartTime = LocalDateTime.now();
    }

    /**
     * 标记解析完成
     */
    public void markParseCompleted() {
        this.parseStatus = FileStateEnum.PARSE_COMPLETE;
        this.parseEndTime = LocalDateTime.now();
    }

    /**
     * 标记解析失败
     */
    public void markParseFailed() {
        this.parseStatus = FileStateEnum.PARSE_FAIL;
        this.parseEndTime = LocalDateTime.now();
    }
}
