package com.gov.landcheck.core.bo.dto;

import com.gov.landcheck.core.bo.entity.OperationAuditLog;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作审计日志分页查询结果 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "操作审计日志分页查询结果")
public class OperationAuditLogQueryResultDTO extends BaseQueryResultDTO<OperationAuditLog> {
}
