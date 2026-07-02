package com.gov.landcheck.core.config.mq.exception;

/**
 * 消息发送异常，用于生产端统一异常处理与监控。
 *
 * @author landcheck
 */
public class MessageSendException extends RuntimeException {

    private final boolean retryable;

    public MessageSendException(String message) {
        super(message);
        this.retryable = false;
    }

    public MessageSendException(String message, Throwable cause) {
        super(message, cause);
        this.retryable = false;
    }

    public MessageSendException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public MessageSendException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
