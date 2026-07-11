package com.gov.landcheck.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 实测报告文件解析状态（用于项目报表展示）
 */
@Getter
@AllArgsConstructor
public enum SurveyParseStatusEnum {
    /**
     * 项目下没有实测报告文件
     */
    NOT_EXIST("NOT_EXIST", "未上传实测报告"),

    /**
     * 实测报告文件存在，但尚未全部解析完成
     */
    UNPARSED("UNPARSED", "实测报告未解析"),

    /**
     * 实测报告存在解析失败
     */
    PARSE_FAILED("PARSE_FAILED", "实测报告解析失败"),

    /**
     * 实测报告全部解析完成
     */
    PARSE_COMPLETE("PARSE_COMPLETE", "实测报告解析完成");

    private final String code;
    private final String name;
}

