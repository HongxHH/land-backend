package com.gov.landcheck.core.bo.entity;

import java.time.LocalDateTime;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OCR执行结果实体类
 * 用于存储完整的OCR识别结果，便于分析和调试
 *
 * @author system
 * @date 2026/01/18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "ocr_execution_result")
@Schema(description = "OCR执行结果")
public class OCRExecutionResult extends MongoIdEntity {

    @Field(name = "file_record_id")
    @Schema(description = "关联 file_record.id")
    private Long fileRecordId;

    @Field(name = "parse_job_id")
    @Schema(description = "关联 parse_job.id")
    private Long parseJobId;

    @Field(name = "ocr_result_json_gridfs_id")
    @Schema(description = "OCR处理结果JSON文件的GridFS ID")
    private String ocrResultJsonGridfsId;

    @Field(name = "page_count")
    @Schema(description = "OCR处理的页面数量")
    private Integer pageCount;

    @Field(name = "processing_time_ms")
    @Schema(description = "OCR处理耗时(毫秒)")
    private Long processingTimeMs;

    @Field(name = "markdown_file_gridfs_id")
    @Schema(description = "Markdown文件的GridFS ID")
    private String markdownFileGridfsId;

    @Field(name = "image_file_path")
    @Schema(description = "图片文件保存路径")
    private String imageFilePath;

    @Field(name = "execution_time")
    @Schema(description = "执行时间")
    private LocalDateTime executionTime;
}