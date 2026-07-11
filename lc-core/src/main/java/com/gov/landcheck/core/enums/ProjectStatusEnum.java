package com.gov.landcheck.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 项目整体状态（用于项目报表展示）
 */
@Getter
@AllArgsConstructor
public enum ProjectStatusEnum {

    /**
     * 项目就绪：合同解析完成、实测解析完成且校验通过
     */
    READY("READY", "就绪"),

    /**
     * 项目存在但未解析完成（包含缺少合同/实测文件）
     */
    UNPARSED("UNPARSED", "未解析"),

    /**
     * 项目中存在解析失败的文件（合同/实测均可能触发）
     */
    PARSE_FAILED("PARSE_FAILED", "解析失败"),

    /**
     * 解析完成但实测校验失败
     */
    SURVEY_VALIDATION_FAILED("SURVEY_VALIDATION_FAILED", "实测校验失败");

    private final String code;
    private final String name;
}

