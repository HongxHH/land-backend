package com.gov.landcheck.core.config.mq.exception;

/**
 * 消息消费异常，用于消费端统一异常处理与重试/死信决策。
 *
 * @author landcheck
 */
public class MessageConsumeException extends RuntimeException {

    private final boolean retryable;

    public MessageConsumeException(String message) {
        super(message);
        this.retryable = false;
    }

    public MessageConsumeException(String message, Throwable cause) {
        super(message, cause);
        this.retryable = false;
    }

    public MessageConsumeException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public MessageConsumeException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
