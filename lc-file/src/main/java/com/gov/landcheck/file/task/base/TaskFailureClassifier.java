package com.gov.landcheck.file.task.base;

/**
 * 解析管道失败分类：区分文件/业务确定性失败与可重试的基础设施故障。
 */
public final class TaskFailureClassifier {

    private TaskFailureClassifier() {
    }

    public static TaskException resolveTaskException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TaskException taskException) {
                return taskException;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 将异常映射为重试策略使用的错误类型字符串（timeout / database / parse 等）。
     */
    public static String toRetryErrorType(Throwable throwable) {
        TaskException taskException = resolveTaskException(throwable);
        if (taskException != null) {
            return mapErrorCodeToRetryType(taskException.getErrorCode());
        }
        return mapMessageToRetryType(messageOf(throwable));
    }

    public static TaskException.ErrorCode classifyFillFailure(Exception exception) {
        if (exception instanceof TaskException taskException) {
            return taskException.getErrorCode();
        }
        String message = messageOf(exception);
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("timeout") || lowerMessage.contains("time out")) {
            return TaskException.ErrorCode.FILL_TIMEOUT;
        }
        if (isNonRetryableBusinessFailure(exception, message)) {
            return TaskException.ErrorCode.PARSE_DATA_INVALID;
        }
        return TaskException.ErrorCode.FILL_FAILED;
    }

    /**
     * 与 {@link com.gov.landcheck.file.task.processor.command.ParseCommand}
     * 中业务失败识别保持一致。
     */
    public static boolean isNonRetryableBusinessFailure(Throwable throwable) {
        TaskException taskException = resolveTaskException(throwable);
        if (taskException != null) {
            return isNonRetryableErrorCode(taskException.getErrorCode());
        }
        return isNonRetryableBusinessFailure(throwable instanceof Exception ex ? ex : new Exception(throwable),
                messageOf(throwable));
    }

    static boolean isNonRetryableBusinessFailure(Exception exception, String message) {
        if (exception instanceof IllegalStateException) {
            return true;
        }
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("未解析")
                || message.contains("为空")
                || message.contains("不可恢复")
                || message.contains("缺少");
    }

    private static boolean isNonRetryableErrorCode(TaskException.ErrorCode errorCode) {
        if (errorCode == null) {
            return true;
        }
        return switch (errorCode) {
            case PREPROCESS_FAILED, OCR_INVALID_INPUT, OCR_FAILED,
                    PARSE_FAILED, PARSE_DATA_INVALID,
                    FILL_FAILED, VALIDATE_FAILED,
                    TASK_CANCELLED, TASK_STATE_INVALID, SYSTEM_ERROR ->
                true;
            default -> false;
        };
    }

    private static String mapErrorCodeToRetryType(TaskException.ErrorCode errorCode) {
        if (errorCode == null) {
            return "unknown";
        }
        return switch (errorCode) {
            case PREPROCESS_TIMEOUT, PREPROCESS_RESOURCE_ERROR,
                    PARSE_TIMEOUT, FILL_TIMEOUT, VALIDATE_TIMEOUT,
                    OCR_TIMEOUT, TASK_TIMEOUT, TASK_RESOURCE_ERROR ->
                "timeout";
            case OCR_API_ERROR, NETWORK_ERROR -> "network";
            case DATABASE_ERROR, FILL_FAILED, VALIDATE_FAILED -> "database";
            case IO_ERROR -> "io";
            case PREPROCESS_FAILED, OCR_INVALID_INPUT, OCR_FAILED,
                    PARSE_FAILED, PARSE_DATA_INVALID ->
                "parse";
            case TASK_CANCELLED, TASK_STATE_INVALID, SYSTEM_ERROR -> "unknown";
        };
    }

    private static String mapMessageToRetryType(String message) {
        if (message == null || message.isBlank()) {
            return "unknown";
        }
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("timeout") || lowerMessage.contains("time out")) {
            return "timeout";
        }
        if (lowerMessage.contains("connection") || lowerMessage.contains("connect")) {
            return "connection";
        }
        if (lowerMessage.contains("network") || lowerMessage.contains("socket")) {
            return "network";
        }
        if (lowerMessage.contains("database") || lowerMessage.contains("mongo")) {
            return "database";
        }
        return "unknown";
    }

    private static String messageOf(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        String message = throwable.getMessage();
        return message != null ? message : throwable.getClass().getSimpleName();
    }
}
