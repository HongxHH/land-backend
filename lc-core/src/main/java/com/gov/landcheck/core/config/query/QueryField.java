package com.gov.landcheck.core.config.query;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在查询 DTO 字段上，用于声明该字段对应的 MongoDB 字段名及查询方式。
 * 配合 {@link MongoQueryBuilder} 使用，实现通用动态查询构建。
 *
 * @author system
 * @date 2026/02/13
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryField {

    /**
     * MongoDB 文档中的字段名（如 _id、project_id）
     */
    String value();

    /**
     * 查询类型，默认精确匹配
     */
    QueryType type() default QueryType.EQUAL;
}
