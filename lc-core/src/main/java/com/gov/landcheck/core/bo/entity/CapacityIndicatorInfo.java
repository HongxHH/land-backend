package com.gov.landcheck.core.bo.entity;

import java.math.BigDecimal;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 容量指标核查表（文件级主表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "capacity_indicator_info")
@CompoundIndexes({
        @CompoundIndex(name = "idx_project_file", def = "{'project_id': 1, 'file_record_id': 1}"),
        @CompoundIndex(name = "idx_file_record", def = "{'file_record_id': 1}")
})
@Schema(description = "容量指标核查表信息")
public class CapacityIndicatorInfo extends MongoIdEntity {

    @Field(name = "project_id")
    @Schema(description = "所属项目ID")
    private Long projectId;

    @Field(name = "file_record_id")
    @Schema(description = "关联 file_record.id")
    private Long fileRecordId;

    @Field(name = "is_parsed")
    @Schema(description = "是否已完成解析回填（0否 1是）")
    private Integer isParsed;

    @Field(name = "total_area")
    @Schema(description = "本次报建建筑面积-合计（平方米）", example = "206591.2400")
    private BigDecimal totalArea;

    @Field(name = "commercial_area")
    @Schema(description = "本次报建建筑面积-商业类（平方米）", example = "1158.4100")
    private BigDecimal commercialArea;

    @Field(name = "residential_area")
    @Schema(description = "本次报建建筑面积-住宅类（平方米）", example = "205432.8300")
    private BigDecimal residentialArea;

    @Field(name = "remark")
    @Schema(description = "备注")
    private String remark;
}
