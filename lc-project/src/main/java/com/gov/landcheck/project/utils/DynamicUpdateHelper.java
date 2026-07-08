package com.gov.landcheck.project.utils;

import java.lang.reflect.Field;

import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.StringUtils;

/**
 * 供各 Service 实现共用的动态更新构建工具（根据 DTO 非空字段生成 Mongo Update）。
 */
public final class DynamicUpdateHelper {

    private DynamicUpdateHelper() {
    }

    public static Update buildDynamicUpdate(Object dto) {
        Update update = new Update();
        Field[] fields = dto.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(dto);
                if (value != null) {
                    if (value instanceof String) {
                        if (StringUtils.hasText((String) value)) {
                            update.set(convertFieldName(field.getName()), value);
                        }
                    } else {
                        update.set(convertFieldName(field.getName()), value);
                    }
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return update;
    }

    /**
     * 可清空字符串字段：空白或 null 写入 Mongo null（用于备注等允许用户删空的场景）。
     * 仅用于请求体始终携带该字段的整表保存接口。
     */
    public static void applyClearableString(Update update, String fieldName, String value) {
        update.set(convertFieldName(fieldName), StringUtils.hasText(value) ? value.trim() : null);
    }

    public static String convertFieldName(String fieldName) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append("_").append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
