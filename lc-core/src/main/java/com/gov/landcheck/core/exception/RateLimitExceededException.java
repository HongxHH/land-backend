package com.gov.landcheck.core.exception;

/**
 * 用户级接口限流（AOP）触发时抛出，由全局异常处理转为 429。
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
