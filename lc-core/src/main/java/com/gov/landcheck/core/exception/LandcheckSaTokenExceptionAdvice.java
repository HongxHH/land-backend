package com.gov.landcheck.core.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.common.MessageConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将 Sa-Token 异常转为项目统一的 {@link AjaxJson}。
 */
@RestControllerAdvice(basePackages = {
        "com.gov.landcheck.user",
        "com.gov.landcheck.file",
        "com.gov.landcheck.project",
        "com.gov.landcheck.core"
})
@Order(1)
public class LandcheckSaTokenExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(LandcheckSaTokenExceptionAdvice.class);

    @ExceptionHandler(NotLoginException.class)
    public AjaxJson onNotLogin(NotLoginException e) {
        log.debug("未登录: {}", e.getMessage());
        return AjaxJson.get(AjaxJson.CODE_NOT_LOGIN,
                e.getMessage() != null ? e.getMessage() : MessageConstant.NOT_LOGIN);
    }

    @ExceptionHandler(NotPermissionException.class)
    public AjaxJson onNotPermission(NotPermissionException e) {
        log.debug("无权限: {}", e.getMessage());
        return AjaxJson.getNotJur(e.getMessage() != null ? e.getMessage() : "无此权限");
    }

    @ExceptionHandler(NotRoleException.class)
    public AjaxJson onNotRole(NotRoleException e) {
        log.debug("角色不符: {}", e.getMessage());
        return AjaxJson.getNotJur(e.getMessage() != null ? e.getMessage() : "角色不符");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<AjaxJson> onRateLimit(RateLimitExceededException e) {
        log.debug("用户级限流: {}", e.getMessage());
        String msg = e.getMessage() != null ? e.getMessage() : "操作过于频繁，请稍后再试";
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(AjaxJson.get(429, msg));
    }
}
