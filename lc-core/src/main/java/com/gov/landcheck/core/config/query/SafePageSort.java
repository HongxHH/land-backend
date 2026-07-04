package com.gov.landcheck.core.config.query;

import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 分页排序字段白名单解析，防止用户可控 sortField 注入非法 Mongo 字段名。
 */
@Slf4j
public final class SafePageSort {

    private SafePageSort() {
    }

    public static Sort resolve(String sortDirection, String sortField, String defaultField, Set<String> allowedFields) {
        Sort.Direction direction = Sort.Direction.DESC;
        if (StringUtils.hasText(sortDirection)) {
            try {
                direction = Sort.Direction.fromString(sortDirection.trim());
            } catch (IllegalArgumentException ex) {
                log.debug("非法排序方向 [{}]，回退为 DESC", sortDirection);
            }
        }
        String field = defaultField;
        if (StringUtils.hasText(sortField)) {
            String candidate = sortField.trim();
            if (allowedFields != null && allowedFields.contains(candidate)) {
                field = candidate;
            } else {
                log.debug("非法排序字段 [{}]，回退为 {}", candidate, defaultField);
            }
        }
        return Sort.by(direction, field);
    }
}
