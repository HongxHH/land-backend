package com.gov.landcheck.core.common;

/**
 * 验证结果封装类
 *
 * @author system
 * @date 2025/01/25
 */
public class ValidationResult {

    /**
     * 验证是否通过
     */
    private boolean valid;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 默认构造函数
     */
    public ValidationResult() {
    }

    /**
     * 构造函数
     *
     * @param valid 验证结果
     */
    public ValidationResult(boolean valid) {
        this.valid = valid;
        this.errorMessage = valid ? null : "";
    }

    /**
     * 构造函数
     *
     * @param valid 验证结果
     * @param errorMessage 错误信息
     */
    public ValidationResult(boolean valid, String errorMessage) {
        this.valid = valid;
        this.errorMessage = errorMessage;
    }

    /**
     * 创建成功的验证结果
     *
     * @return ValidationResult
     */
    public static ValidationResult success() {
        return new ValidationResult(true);
    }

    /**
     * 创建失败的验证结果
     *
     * @param errorMessage 错误信息
     * @return ValidationResult
     */
    public static ValidationResult failure(String errorMessage) {
        return new ValidationResult(false, errorMessage);
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "valid=" + valid +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}