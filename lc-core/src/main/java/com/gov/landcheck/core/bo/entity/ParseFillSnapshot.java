package com.gov.landcheck.core.bo.entity;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;
import com.gov.landcheck.core.enums.FileContextType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 回填前落盘的 current 业务快照。ParseJob SUCCESS 前用于失败/取消/崩溃恢复，SUCCESS 后删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "parse_fill_snapshot")
@Schema(description = "解析回填前业务快照")
public class ParseFillSnapshot extends MongoIdEntity {

    @Indexed(unique = true)
    @Field(name = "parse_job_id")
    @Schema(description = "关联 parse_job.id，同一任务只保留首次快照")
    private Long parseJobId;

    @Field(name = "file_record_id")
    @Schema(description = "关联 file_record.id")
    private Long fileRecordId;

    @Field(name = "project_id")
    @Schema(description = "所属项目ID")
    private Long projectId;

    @Field(name = "file_context_type")
    @Schema(description = "文件内容类型")
    private FileContextType fileContextType;

    @Field(name = "had_existing")
    @Schema(description = "回填前是否已有对应业务主表")
    private Boolean hadExisting;

    @Field(name = "contract_snapshot")
    private ContractInfo contractSnapshot;

    @Field(name = "survey_report_snapshot")
    private SurveyReportInfo surveyReportSnapshot;

    @Field(name = "rooms_snapshot")
    private List<RoomInfo> roomsSnapshot = new ArrayList<>();

    @Field(name = "planning_form_snapshot")
    private PlanningReviewForm planningFormSnapshot;

    @Field(name = "planning_rows_snapshot")
    private List<PlanningReviewRow> planningRowsSnapshot = new ArrayList<>();

    @Field(name = "capacity_snapshot")
    private CapacityIndicatorInfo capacitySnapshot;

    @Field(name = "party_summary_snapshot")
    private ProjectPartySurveySummaryForm partySummarySnapshot;
}
