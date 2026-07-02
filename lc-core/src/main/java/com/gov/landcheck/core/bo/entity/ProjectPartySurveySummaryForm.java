package com.gov.landcheck.core.bo.entity;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目方上传的实测汇总主表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "project_party_survey_summary_form")
@CompoundIndexes({
        @CompoundIndex(name = "idx_project_file", def = "{'project_id': 1, 'file_record_id': 1}"),
        @CompoundIndex(name = "idx_file_record", def = "{'file_record_id': 1}")
})
@Schema(description = "项目方实测汇总主表")
public class ProjectPartySurveySummaryForm extends MongoIdEntity {

    /** 已废弃子表集合名；删文件/删项目时仍物理清理历史数据。 */
    public static final String LEGACY_ROW_COLLECTION = "project_party_survey_summary_row";

    @Field(name = "project_id")
    @Schema(description = "所属项目ID")
    private Long projectId;

    @Field(name = "file_record_id")
    @Schema(description = "关联 file_record.id")
    private Long fileRecordId;

    @Field(name = "is_parsed")
    @Schema(description = "是否已解析（0否 1是）")
    private Integer isParsed;

    @Field(name = "declared_totals")
    @Schema(description = "项目方声明的底部三行汇总")
    private ProjectPartyDeclaredTotals declaredTotals;

    @Field(name = "parse_status")
    @Schema(description = "解析状态：PENDING（待解析）/SUCCESS/PARTIAL/FAILED")
    private String parseStatus;

    @Field(name = "remark")
    @Schema(description = "备注")
    private String remark;
}
