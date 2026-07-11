package com.gov.landcheck.core.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.core.common.MessageConstant;
import com.gov.landcheck.core.config.logging.TraceContext;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 全局未捕获异常处理，避免内部错误信息透出到客户端。
 */
@RestControllerAdvice(basePackages = {
                "com.gov.landcheck.user",
                "com.gov.landcheck.file",
                "com.gov.landcheck.project",
                "com.gov.landcheck.core"
})
@Order(2)
public class LandcheckGlobalExceptionAdvice {

        private static final Logger log = LoggerFactory.getLogger(LandcheckGlobalExceptionAdvice.class);

        @ExceptionHandler(IllegalArgumentException.class)
        public AjaxJson onIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
                log.debug("非法参数: traceId={} uri={} msg={}",
                                TraceContext.getTraceId(), request.getRequestURI(), e.getMessage());
                String msg = e.getMessage() != null && !e.getMessage().isBlank()
                                ? e.getMessage()
                                : "参数错误";
                return AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, msg);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<AjaxJson> onException(Exception e, HttpServletRequest request) {
                log.error("未处理异常: traceId={} method={} uri={}",
                                TraceContext.getTraceId(), request.getMethod(), request.getRequestURI(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(AjaxJson.get(MessageConstant.PARAMS_ERROR_CODE, "系统繁忙，请稍后重试"));
        }
}
