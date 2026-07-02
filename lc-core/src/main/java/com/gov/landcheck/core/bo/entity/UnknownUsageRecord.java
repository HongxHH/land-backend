package com.gov.landcheck.core.bo.entity;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 未知用途记录实体类
 * 记录系统遇到的所有未知用途，便于人工审核和后续配置
 *
 * @author system
 * @date 2025/01/21
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "unknown_usage_record")
@CompoundIndexes({
        @CompoundIndex(name = "uk_project_usage_file", def = "{'project_id': 1, 'usage_name': 1, 'file_record_id': 1}", unique = true)
})
@Schema(description = "未知用途记录")
public class UnknownUsageRecord extends MongoIdEntity {

    /**
     * 未知用途名称
     */
    @Field(name = "usage_name")
    @Indexed
    @Schema(description = "未知用途名称", example = "科技馆")
    private String usageName;

    /**
     * 关联的项目ID
     */
    @Field(name = "project_id")
    @Schema(description = "关联的项目ID")
    private Long projectId;

    /**
     * 来源文件ID
     */
    @Field(name = "file_record_id")
    @Schema(description = "来源文件ID")
    private Long fileRecordId;

    /**
     * 来源RoomInfo ID
     */
    @Field(name = "room_info_id")
    @Schema(description = "来源RoomInfo ID")
    private Long roomInfoId;

    /**
     * 来源SurveyReportInfo ID
     */
    @Field(name = "survey_report_info_id")
    @Schema(description = "来源SurveyReportInfo ID")
    private Long surveyReportInfoId;

    /**
     * 该用途在当前文件中的出现次数
     */
    @Field(name = "occurrence_count")
    @Schema(description = "出现次数", example = "1")
    private Integer occurrenceCount;

    /**
     * 处理状态
     * 0: 待处理
     * 1: 已处理（已添加到配置）
     * 2: 已忽略
     */
    @Field(name = "status")
    @Schema(description = "处理状态（0待处理 1已处理 2已忽略）", example = "0")
    private Integer status;

    /**
     * 处理人
     */
    @Field(name = "handled_by")
    @Schema(description = "处理人")
    private String handledBy;

    /**
     * 处理备注
     */
    @Field(name = "handle_remark")
    @Schema(description = "处理备注")
    private String handleRemark;
}
