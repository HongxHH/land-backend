package com.gov.landcheck.core.audit;

/**
 * 审计操作类型
 */
public enum OperationType {
    CREATE,
    UPDATE,
    DELETE,
    UPLOAD,
    MOVE,
    PARSE,
    PARSE_COMPLETE,
    PARSE_CANCEL
}
