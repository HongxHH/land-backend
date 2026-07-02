package com.gov.landcheck.core.audit;

/**
 * 操作人上下文，用于审计记录“谁”执行了操作。
 * 由 Filter/Interceptor 从请求头 X-User-Id 设置，请求结束时清除。
 */
public final class OperatorContext {

    private static final String SYSTEM_OPERATOR = "system";

    private static final ThreadLocal<OperatorHolder> HOLDER = ThreadLocal.withInitial(() -> null);

    private OperatorContext() {
    }

    /**
     * 设置当前请求的操作人
     *
     * @param operatorId   操作人 ID，可为 null 表示系统
     * @param operatorName 操作人姓名，可选
     */
    public static void setOperator(Long operatorId, String operatorName) {
        HOLDER.set(new OperatorHolder(operatorId, operatorName));
    }

    /**
     * 设置当前请求的操作人（仅 ID，从请求头等解析的字符串）
     *
     * @param operatorIdStr 操作人 ID 字符串，可为 null 或空
     */
    public static void setOperatorFromHeader(String operatorIdStr) {
        if (operatorIdStr == null || operatorIdStr.isBlank()) {
            HOLDER.set(new OperatorHolder(null, null));
            return;
        }
        String trimmed = operatorIdStr.trim();
        try {
            Long id = Long.parseLong(trimmed);
            HOLDER.set(new OperatorHolder(id, null));
        } catch (NumberFormatException e) {
            HOLDER.set(new OperatorHolder(null, trimmed));
        }
    }

    /**
     * 获取当前操作人 ID
     *
     * @return 操作人 ID，无则 null
     */
    public static Long getOperatorId() {
        OperatorHolder h = HOLDER.get();
        return h != null ? h.operatorId : null;
    }

    /**
     * 获取当前操作人姓名
     *
     * @return 操作人姓名，无则 null
     */
    public static String getOperatorName() {
        OperatorHolder h = HOLDER.get();
        return h != null ? h.operatorName : null;
    }

    /**
     * 获取用于审计显示的操作人标识（有 ID 用 ID，否则用 name，再否则 "system"）
     */
    public static String getOperatorIdOrSystem() {
        OperatorHolder h = HOLDER.get();
        if (h == null) {
            return SYSTEM_OPERATOR;
        }
        if (h.operatorId != null) {
            return String.valueOf(h.operatorId);
        }
        if (h.operatorName != null && !h.operatorName.isBlank()) {
            return h.operatorName;
        }
        return SYSTEM_OPERATOR;
    }

    /**
     * 获取当前操作人 ID，无则返回默认值
     */
    public static Long getOperatorIdOrDefault(Long defaultId) {
        Long operatorId = getOperatorId();
        return operatorId != null ? operatorId : defaultId;
    }

    /**
     * 获取当前操作人姓名，无则返回默认值
     */
    public static String getOperatorNameOrDefault(String defaultName) {
        String operatorName = getOperatorName();
        return operatorName != null && !operatorName.isBlank() ? operatorName : defaultName;
    }

    /**
     * 清除当前线程的操作人上下文（请求结束时调用）
     */
    public static void clear() {
        HOLDER.remove();
    }

    private static final class OperatorHolder {
        private final Long operatorId;
        private final String operatorName;

        OperatorHolder(Long operatorId, String operatorName) {
            this.operatorId = operatorId;
            this.operatorName = operatorName;
        }
    }
}
