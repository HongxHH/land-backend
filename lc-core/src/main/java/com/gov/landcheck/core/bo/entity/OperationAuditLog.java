package com.gov.landcheck.core.bo.entity;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.gov.landcheck.core.config.mongo.MongoIdEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作审计日志：记录谁、何时、对何对象、做了何种操作及变更摘要。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "operation_audit_log")
@Schema(description = "操作审计日志")
public class OperationAuditLog extends MongoIdEntity {

    @Field(name = "operate_time")
    @Schema(description = "操作时间")
    private java.time.LocalDateTime operateTime;

    @Field(name = "operator_id")
    @Schema(description = "操作人ID，空表示系统")
    private Long operatorId;

    @Field(name = "operator_name")
    @Schema(description = "操作人姓名")
    private String operatorName;

    @Field(name = "operation")
    @Schema(description = "操作类型：CREATE/UPDATE/DELETE/UPLOAD/MOVE")
    private String operation;

    @Field(name = "target_type")
    @Indexed
    @Schema(description = "目标类型：project/contract/land_parcel/room_info/survey_report/file/file_archive")
    private String targetType;

    @Field(name = "target_id")
    @Indexed
    @Schema(description = "目标实体主键")
    private String targetId;

    @Field(name = "project_id")
    @Indexed
    @Schema(description = "关联项目ID")
    private Long projectId;

    @Field(name = "contract_id")
    @Schema(description = "关联合同ID")
    private Long contractId;

    @Field(name = "change_summary")
    @Schema(description = "变更摘要：CREATE 为快照摘要，UPDATE 为字段 diff，DELETE 为删除前摘要")
    private String changeSummary;

    @Field(name = "request_id")
    @Schema(description = "请求追踪ID")
    private String requestId;

    @Field(name = "extra")
    @Schema(description = "扩展信息")
    private Object extra;
}
