package com.gov.landcheck.core.config.query;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.util.StringUtils;

/**
 * 基于反射与 {@link QueryField} 注解的 MongoDB 通用查询条件构建器。
 * 仅处理标注了 {@link QueryField} 的字段，忽略 null 与空字符串。
 * <p>
 * <b>继承链</b>：自当前 DTO 类型起向上直至 {@link Object}，合并各层声明字段；对同一 Mongo 字段名
 * （{@link QueryField#value()}）仅保留子类中的定义（子类优先）。
 * </p>
 * <p>
 * <b>REGEX 安全</b>：{@link QueryType#REGEX} 分支对用户输入做
 * {@link Pattern#quote(String)} 转义，
 * 按<strong>字面量</strong>匹配（不再将输入当作正则语法）；单字段值长度超过
 * {@value #MAX_REGEX_VALUE_LENGTH}
 * 时抛出 {@link IllegalArgumentException}，防止过长模式与过大扫描。
 * </p>
 *
 * @author system
 * @date 2026/02/13
 */
public final class MongoQueryBuilder {

    /** REGEX 类型查询值最大长度（字符数），超出视为非法参数 */
    public static final int MAX_REGEX_VALUE_LENGTH = 100;

    private MongoQueryBuilder() {
    }

    /**
     * 根据 DTO 上标注的 {@link QueryField} 构建 Criteria。
     *
     * @param queryDTO 查询 DTO（当前类及父类声明字段上可标注 @QueryField）
     * @return 组合后的 Criteria，无任何条件时返回空 Criteria（匹配全部）
     */
    public static Criteria buildCriteria(Object queryDTO) {
        if (queryDTO == null) {
            return new Criteria();
        }
        List<Criteria> list = new ArrayList<>();
        Map<String, Field> fieldByMongoName = collectQueryFields(queryDTO.getClass());
        for (Field field : fieldByMongoName.values()) {
            QueryField annotation = field.getAnnotation(QueryField.class);
            if (annotation == null) {
                continue;
            }
            Object value;
            try {
                field.setAccessible(true);
                value = field.get(queryDTO);
            } catch (IllegalAccessException e) {
                continue;
            }
            if (isEmpty(value)) {
                continue;
            }
            String mongoField = annotation.value();
            QueryType type = annotation.type();
            if (type == QueryType.REGEX) {
                String raw = value.toString();
                if (raw.length() > MAX_REGEX_VALUE_LENGTH) {
                    throw new IllegalArgumentException(
                            "Regex query value exceeds max length " + MAX_REGEX_VALUE_LENGTH + " for field: "
                                    + mongoField);
                }
                list.add(Criteria.where(mongoField).regex(Pattern.quote(raw), "i"));
            } else {
                list.add(Criteria.where(mongoField).is(value));
            }
        }
        if (list.isEmpty()) {
            return new Criteria();
        }
        return new Criteria().andOperator(list.toArray(Criteria[]::new));
    }

    /**
     * 自子类向父类遍历 {@link QueryField}，同一 Mongo 字段名保留子类声明（先见为子类）。
     */
    private static Map<String, Field> collectQueryFields(Class<?> clazz) {
        Map<String, Field> fieldByMongoName = new LinkedHashMap<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                QueryField annotation = field.getAnnotation(QueryField.class);
                if (annotation == null) {
                    continue;
                }
                String mongoField = annotation.value();
                fieldByMongoName.putIfAbsent(mongoField, field);
            }
        }
        return fieldByMongoName;
    }

    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return !StringUtils.hasText(s);
        }
        return false;
    }
}
