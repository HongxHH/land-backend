package com.gov.landcheck.core.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注需要做操作审计的 Service 方法。
 * 切面根据 operation、targetType 及方法参数/返回值自动记录审计日志。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditOperation {

    OperationType operation();

    TargetType targetType();

    /**
     * 从方法参数解析目标 ID 的 SpEL，如 "p0.id" 表示第一个参数的 id 属性
     */
    String idParam() default "";

    /**
     * 从方法参数解析 projectId 的 SpEL，如 "p0.projectId"；可选，UPDATE/DELETE 时也可从旧实体取
     */
    String projectIdParam() default "";

    /**
     * 从方法参数解析 contractId 的 SpEL；可选
     */
    String contractIdParam() default "";
}
