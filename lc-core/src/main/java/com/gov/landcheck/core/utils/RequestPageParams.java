package com.gov.landcheck.core.utils;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从当前 HTTP 请求读取分页参数（替代 SoMap 的 Web 辅助能力）。
 */
public final class RequestPageParams {

    private RequestPageParams() {
    }

    public static boolean isWebRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes;
    }

    public static int getPageNo() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return 1;
        }
        return parsePositiveInt(request.getParameter("pageNo"), 1, 1, Integer.MAX_VALUE);
    }

    public static int getPageSize() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return 10;
        }
        return parsePositiveInt(request.getParameter("pageSize"), 10, 1, 1000);
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private static int parsePositiveInt(String raw, int defaultValue, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) {
                return defaultValue;
            }
            return value;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
