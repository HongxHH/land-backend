package com.gov.landcheck.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 实测报告校验状态（用于项目报表展示）
 */
@Getter
@AllArgsConstructor
public enum SurveyValidationStatusEnum {

    /**
     * 项目下没有实测报告文件
     */
    NOT_EXIST("NOT_EXIST", "未上传实测报告"),

    /**
     * 实测报告存在，但尚未完成校验/尚未进入校验结果阶段
     */
    NOT_PARSED("NOT_PARSED", "未校验完成"),

    /**
     * 校验通过
     */
    VALID("VALID", "校验通过"),

    /**
     * 校验失败
     */
    VALIDATION_FAILED("VALIDATION_FAILED", "校验失败");

    private final String code;
    private final String name;
}

