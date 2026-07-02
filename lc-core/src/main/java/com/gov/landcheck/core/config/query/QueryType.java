package com.gov.landcheck.core.config.query;

/**
 * 查询条件类型，用于通用查询构建器
 *
 * @author system
 * @date 2026/02/13
 */
public enum QueryType {

    /** 精确匹配（eq） */
    EQUAL,

    /** 字符串模糊匹配（正则，忽略大小写） */
    REGEX
}
