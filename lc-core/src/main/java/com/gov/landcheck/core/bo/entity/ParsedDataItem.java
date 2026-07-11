package com.gov.landcheck.core.bo.entity;

import java.math.BigDecimal;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 解析数据明细实体类（键值对形式，灵活存储所有字段）
 *
 * @author system
 * @date 2025/12/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "parsed_data_item")
@Schema(description = "解析数据明细")
public class ParsedDataItem extends MongoIdEntity {

    @Field(name = "header_id")
    @Schema(description = "关联 parsed_data_header.id")
    private Long headerId;

    @Field(name = "data_category")
    @Schema(description = "数据类别", example = "BASIC_INFO（BASIC_INFO/ROOM_INFO/CONTRACT_INFO/SURVEY_INFO/TABLE_DATA）")
    private String dataCategory;

    @Field(name = "field_key")
    @Schema(description = "原始字段名",example = "容积率")
    private String fieldKey;

    @Field(name = "field_value")
    @Schema(description = "字段值（原始字符串，包含单位）", example = "2.5")
    private String fieldValue;

    @Field(name = "value_number")
    @Schema(description = "解析出的数值（若能转换则存入）", example = "2.5000")
    private BigDecimal valueNumber;

    @Field(name = "value_unit")
    @Schema(description = "单位（如 平方米 / m / 层 / 元）", example = "平方米")
    private String valueUnit;

    @Field(name = "normalized_key")
    @Schema(description = "标准化后字段名（如 volume_rate）", example = "volume_rate")
    private String normalizedKey;

    @Field(name = "normalized_number")
    @Schema(description = "标准化后数值（换算/统一单位后的数值，便于比对）", example = "2.5000")
    private BigDecimal normalizedNumber;

    @Field(name = "normalized_value")
    @Schema(description = "标准化后字符串值（原始展示）", example = "2.5")
    private String normalizedValue;

    @Field(name = "source_page")
    @Schema(description = "源文件页码（PDF页码，从1开始）", example = "1")
    private Integer sourcePage;

    @Field(name = "source_position")
    @Schema(description = "在页内位置信息（如 x,y/表格第几行）", example = "100,200")
    private String sourcePosition;

    @Field(name = "source_field_name")
    @Schema(description = "原始表单/PDF中字段位置或标签（便于回溯）", example = "第3行第2列")
    private String sourceFieldName;

    @Field(name = "table_info")
    @Schema(description = "表格相关信息（JSON格式，包含表格ID、行列信息等）")
    private String tableInfo;

    @Field(name = "sort_order")
    @Schema(description = "同类型字段排序（便于前端展示）", example = "1")
    private Integer sortOrder;

    @Field(name = "field_type")
    @Schema(description = "字段类型（NUMBER/STRING/DATE/BOOLEAN/JSON）", example = "NUMBER")
    private String fieldType;

    @Field(name = "extraction_method")
    @Schema(description = "提取方法（OCR/LLM/MANUAL）", example = "LLM")
    private String extractionMethod;

    @Field(name = "data_source")
    @Schema(description = "数据来源（OCR/LLM/MANUAL）", example = "LLM")
    private String dataSource;

    @Field(name = "confidence_score")
    @Schema(description = "置信度评分（0~1）", example = "0.95")
    private BigDecimal confidenceScore;

    
    @Field(name = "is_modified")
    @Schema(description = "是否手动修改过（0否 1是）", example = "0")
    private Integer isModified;

    @Field(name = "modified_by")
    @Schema(description = "最后修改人（sys_user.id）")
    private Long modifiedBy;

    @Field(name = "modified_reason")
    @Schema(description = "修改原因简述")
    private String modifiedReason;


    @Field(name = "validation_status")
    @Schema(description = "验证状态（PENDING/VALID/INVALID）", example = "VALID")
    private String validationStatus;

    @Field(name = "validation_message")
    @Schema(description = "验证信息")
    private String validationMessage;

    public ParsedDataItem() {
        this.isModified = 0;
        this.validationStatus = "PENDING";
    }

    /**
     * 标记为已修改
     */
    public void markAsModified(Long userId, String reason) {
        this.isModified = 1;
        this.modifiedBy = userId;
        this.modifiedReason = reason;
    }

    /**
     * 标记验证通过
     */
    public void markValidationValid() {
        this.validationStatus = "VALID";
        this.validationMessage = null;
    }

    /**
     * 标记验证失败
     */
    public void markValidationInvalid(String message) {
        this.validationStatus = "INVALID";
        this.validationMessage = message;
    }
}

