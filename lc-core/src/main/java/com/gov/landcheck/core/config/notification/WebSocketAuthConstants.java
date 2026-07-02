package com.gov.landcheck.core.config.notification;

/**
 * WebSocket 鉴权相关常量。
 */
public final class WebSocketAuthConstants {

    private WebSocketAuthConstants() {
    }

    /** 握手阶段写入 WebSocket session attributes 的登录用户 ID */
    public static final String LOGIN_ID = "loginId";
}
