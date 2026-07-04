package com.gov.landcheck.core.config.query;

import java.util.regex.Pattern;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.util.StringUtils;

/**
 * MongoDB 用户输入 regex 查询的安全构造（字面量匹配 + 长度限制）。
 */
public final class MongoRegexCriteria {

    private MongoRegexCriteria() {
    }

    public static Criteria like(String field, String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return new Criteria();
        }
        String trimmed = rawValue.trim();
        if (trimmed.length() > MongoQueryBuilder.MAX_REGEX_VALUE_LENGTH) {
            throw new IllegalArgumentException(
                    "Regex query value exceeds max length " + MongoQueryBuilder.MAX_REGEX_VALUE_LENGTH
                            + " for field: " + field);
        }
        return Criteria.where(field).regex(Pattern.quote(trimmed), "i");
    }
}
