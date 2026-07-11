package com.gov.landcheck.core.config.cache.key;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class KeyNormalizer {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final String DEFAULT_SORT_FIELD = "createTime";
    private static final String DEFAULT_SORT_DIRECTION = "desc";

    private final ObjectMapper objectMapper;

    /**
     * 使用默认（无 default typing）的 ObjectMapper，避免 DTO 转 Map 时因 @class 多态解析报错。
     */
    public KeyNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Object normalize(Object source) {
        if (source == null) {
            return null;
        }
        if (source instanceof String value) {
            String trimmed = value.trim();
            return StringUtils.hasText(trimmed) ? trimmed : null;
        }
        if (source instanceof Number || source instanceof Boolean || source instanceof Enum<?>) {
            return source;
        }
        if (source instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>();
            for (Object element : collection) {
                Object normalized = normalize(element);
                if (normalized != null) {
                    result.add(normalized);
                }
            }
            return result;
        }
        if (source instanceof Map<?, ?> map) {
            return normalizeMap(castToStringMap(map));
        }

        Map<String, Object> valueMap = objectMapper.convertValue(source, new TypeReference<>() {});
        return normalizeMap(valueMap);
    }

    private Map<String, Object> normalizeMap(Map<String, Object> source) {
        TreeMap<String, Object> sorted = new TreeMap<>();
        source.forEach((key, value) -> {
            Object normalized = normalize(value);
            if (normalized != null) {
                sorted.put(key, normalized);
            }
        });
        applyQueryDefaults(sorted);
        return new LinkedHashMap<>(sorted);
    }

    private void applyQueryDefaults(Map<String, Object> map) {
        if (map.containsKey("pageNum") || map.containsKey("pageSize")
                || map.containsKey("sortField") || map.containsKey("sortDirection")) {
            map.putIfAbsent("pageNum", DEFAULT_PAGE_NUM);
            map.putIfAbsent("pageSize", DEFAULT_PAGE_SIZE);
            map.putIfAbsent("sortField", DEFAULT_SORT_FIELD);
            Object sortDirection = map.get("sortDirection");
            if (sortDirection == null) {
                map.put("sortDirection", DEFAULT_SORT_DIRECTION);
            } else {
                map.put("sortDirection", String.valueOf(sortDirection).trim().toLowerCase());
            }
        }
    }

    private Map<String, Object> castToStringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }
}
