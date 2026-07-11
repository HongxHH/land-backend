package com.gov.landcheck.file.task.base;

/**
 * 任务执行异常
 * 提供统一的异常处理框架，包含错误码和处理阶段信息
 *
 * @author system
 * @date 2026/01/27
 */
public class TaskException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String stage;
    private final Long fileId;
    private final Long taskId;

    public TaskException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.stage = null;
        this.fileId = null;
        this.taskId = null;
    }

    public TaskException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.stage = null;
        this.fileId = null;
        this.taskId = null;
    }

    public TaskException(ErrorCode errorCode, String stage, String message) {
        super(message);
        this.errorCode = errorCode;
        this.stage = stage;
        this.fileId = null;
        this.taskId = null;
    }

    public TaskException(ErrorCode errorCode, String stage, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.stage = stage;
        this.fileId = null;
        this.taskId = null;
    }

    public TaskException(ErrorCode errorCode, String stage, Long fileId, Long taskId, String message) {
        super(message);
        this.errorCode = errorCode;
        this.stage = stage;
        this.fileId = fileId;
        this.taskId = taskId;
    }

    public TaskException(ErrorCode errorCode, String stage, Long fileId, Long taskId, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.stage = stage;
        this.fileId = fileId;
        this.taskId = taskId;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getStage() {
        return stage;
    }

    public Long getFileId() {
        return fileId;
    }

    public Long getTaskId() {
        return taskId;
    }

    @Override
    public String toString() {
        return String.format("TaskException{errorCode=%s, stage='%s', fileId=%s, taskId=%s, message='%s'}",
                errorCode, stage, fileId, taskId, getMessage());
    }

    /**
     * 任务执行错误码枚举
     */
    public enum ErrorCode {
        // 预处理阶段错误
        PREPROCESS_FAILED("预处理失败"),
        PREPROCESS_TIMEOUT("预处理超时"),
        PREPROCESS_RESOURCE_ERROR("预处理资源错误"),

        // OCR阶段错误
        OCR_FAILED("OCR识别失败"),
        OCR_TIMEOUT("OCR超时"),
        OCR_API_ERROR("OCR API调用错误"),
        OCR_INVALID_INPUT("OCR输入无效"),

        // 数据解析阶段错误
        PARSE_FAILED("数据解析失败"),
        PARSE_TIMEOUT("数据解析超时"),
        PARSE_DATA_INVALID("解析数据无效"),

        // 数据回填阶段错误
        FILL_FAILED("数据回填失败"),
        FILL_TIMEOUT("数据回填超时"),

        // 数据校验阶段错误
        VALIDATE_FAILED("数据校验失败"),
        VALIDATE_TIMEOUT("数据校验超时"),

        // 任务管理错误
        TASK_CANCELLED("任务被取消"),
        TASK_TIMEOUT("任务执行超时"),
        TASK_RESOURCE_ERROR("任务资源错误"),
        TASK_STATE_INVALID("任务状态无效"),

        // 系统级别错误
        SYSTEM_ERROR("系统错误"),
        DATABASE_ERROR("数据库错误"),
        NETWORK_ERROR("网络错误"),
        IO_ERROR("IO错误");

        private final String description;

        ErrorCode(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}