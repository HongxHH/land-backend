package com.gov.landcheck.project.utils;

import java.util.Set;

import org.springframework.data.domain.Sort;

import com.gov.landcheck.core.config.query.MongoSortFields;
import com.gov.landcheck.core.config.query.SafePageSort;

/**
 * 分页与排序字段安全解析（委托 lc-core {@link SafePageSort}）。
 */
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
        return SafePageSort.resolve(sortDirection, sortField, defaultField, MongoSortFields.PROJECT);
    }

    public static Sort resolveSort(String sortDirection, String sortField, String defaultField,
            Set<String> allowedFields) {
        return SafePageSort.resolve(sortDirection, sortField, defaultField, allowedFields);
    }
}
