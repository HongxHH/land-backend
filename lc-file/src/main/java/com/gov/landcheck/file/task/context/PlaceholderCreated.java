package com.gov.landcheck.file.task.context;

import com.gov.landcheck.core.audit.TargetType;
import com.gov.landcheck.core.config.cache.event.ProjectDataChangedEvent;

/**
 * 上传时新建的业务占位。已有记录时模块返回 null，调用方不再审计、同步或发事件。
 */
public record PlaceholderCreated(
                TargetType auditTarget,
                Object entity,
                ProjectDataChangedEvent event,
                boolean syncSurveyContractApproval) {
}
