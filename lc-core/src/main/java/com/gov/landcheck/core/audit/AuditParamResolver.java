package com.gov.landcheck.core.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * 从切点方法参数中按 SpEL 解析 targetId、projectId、contractId。
 * 约定：idParam 等为 SpEL，如 "p0.id" 表示第一个参数的 id 属性；p0..pN 对应 args[0]..args[N]。
 */
public final class AuditParamResolver {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private AuditParamResolver() {
    }

    public static Long resolveIdAsLong(ProceedingJoinPoint pjp, String idParam) {
        Object val = resolve(pjp, idParam);
        return toLong(val);
    }

    public static String resolveIdAsString(ProceedingJoinPoint pjp, String idParam) {
        Object val = resolve(pjp, idParam);
        return val != null ? String.valueOf(val) : null;
    }

    public static Long resolveProjectId(ProceedingJoinPoint pjp, String projectIdParam) {
        Object val = resolve(pjp, projectIdParam);
        return toLong(val);
    }

    public static Long resolveContractId(ProceedingJoinPoint pjp, String contractIdParam) {
        Object val = resolve(pjp, contractIdParam);
        return toLong(val);
    }

    /**
     * SpEL 变量需用 #varName 访问，如 #p0.id。此处将注解中常用的 "p0" 形式自动转为 "#p0"。
     */
    public static Object resolve(ProceedingJoinPoint pjp, String spelExpression) {
        if (spelExpression == null || spelExpression.isBlank()) {
            return null;
        }
        String expr = toSpelVariableExpression(spelExpression);
        Object[] args = pjp.getArgs();
        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
        }
        try {
            return PARSER.parseExpression(expr).getValue(context);
        } catch (Exception e) {
            return null;
        }
    }

    /** 将 p0、p1 等参数引用转为 SpEL 变量 #p0、#p1（避免注解里写 # 前缀） */
    private static String toSpelVariableExpression(String expression) {
        if (expression == null) {
            return null;
        }
        String s = expression.trim();
        for (int i = 0; i <= 9; i++) {
            String p = "p" + i;
            String sharp = "#" + p;
            if (s.equals(p)) {
                return sharp;
            }
            if (s.startsWith(p + ".")) {
                return sharp + s.substring(p.length());
            }
        }
        return s;
    }

    public static Long toLong(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Long l) {
            return l;
        }
        if (val instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
