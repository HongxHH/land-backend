package com.gov.landcheck.project.utils;

import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 分页与排序字段安全解析（与 lc-file 模块 {@code OtherDataServiceImpl} 中逻辑保持一致）。
 */
@Slf4j
public final class PageSortSupport {

    private PageSortSupport() {
    }

    public static int resolvePageNum(Integer pageNum) {
        return (pageNum != null && pageNum > 0) ? pageNum : 1;
    }

    public static int resolvePageSize(Integer pageSize) {
        return (pageSize != null && pageSize > 0) ? pageSize : 10;
    }

    public static Sort resolveSort(String sortDirection, String sortField, String defaultField) {
        Sort.Direction direction = Sort.Direction.DESC;
        if (StringUtils.hasText(sortDirection)) {
            try {
                direction = Sort.Direction.fromString(sortDirection.trim());
            } catch (IllegalArgumentException ex) {
                log.debug("非法排序方向 [{}]，回退为 DESC", sortDirection);
            }
        }
        String field = StringUtils.hasText(sortField) ? sortField.trim() : defaultField;
        return Sort.by(direction, field);
    }
}
