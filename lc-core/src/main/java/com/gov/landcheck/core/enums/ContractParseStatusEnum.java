package com.gov.landcheck.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 合同文件解析状态（用于项目报表展示）
 */
@Getter
@AllArgsConstructor
public enum ContractParseStatusEnum {
    /**
     * 项目下没有合同文件
     */
    NOT_EXIST("NOT_EXIST", "未上传合同"),

    /**
     * 合同文件存在，但尚未全部解析完成
     */
    UNPARSED("UNPARSED", "合同未解析"),

    /**
     * 合同存在解析失败
     */
    PARSE_FAILED("PARSE_FAILED", "合同解析失败"),

    /**
     * 合同全部解析完成
     */
    PARSE_COMPLETE("PARSE_COMPLETE", "合同解析完成");

    private final String code;
    private final String name;
}

