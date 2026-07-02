package com.gov.landcheck.core.config.notification;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket（STOMP）配置：站内广播推送。
 * 客户端连接 /ws（需携带 satoken），按项目订阅 /topic/project/{projectId}/{topicKey}。
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketNotificationConfig implements WebSocketMessageBrokerConfigurer {

    /** WebSocket 端点路径，例如 ws://host:port/ws */
    public static final String WS_ENDPOINT = "/ws";

    private final SaTokenWebSocketHandshakeInterceptor saTokenWebSocketHandshakeInterceptor;
    private final SaTokenWebSocketHandshakeHandler saTokenWebSocketHandshakeHandler;
    private final WebSocketStompChannelInterceptor webSocketStompChannelInterceptor;

    public WebSocketNotificationConfig(
            SaTokenWebSocketHandshakeInterceptor saTokenWebSocketHandshakeInterceptor,
            SaTokenWebSocketHandshakeHandler saTokenWebSocketHandshakeHandler,
            WebSocketStompChannelInterceptor webSocketStompChannelInterceptor) {
        this.saTokenWebSocketHandshakeInterceptor = saTokenWebSocketHandshakeInterceptor;
        this.saTokenWebSocketHandshakeHandler = saTokenWebSocketHandshakeHandler;
        this.webSocketStompChannelInterceptor = webSocketStompChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(WS_ENDPOINT)
                .setAllowedOriginPatterns("*")
                .addInterceptors(saTokenWebSocketHandshakeInterceptor)
                .setHandshakeHandler(saTokenWebSocketHandshakeHandler)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketStompChannelInterceptor);
    }
}
