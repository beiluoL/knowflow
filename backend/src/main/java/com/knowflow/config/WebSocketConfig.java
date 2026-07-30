package com.knowflow.config;

import com.knowflow.websocket.GroupWebSocketHandler;
import com.knowflow.websocket.ImWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GroupWebSocketHandler groupWebSocketHandler;
    private final ImWebSocketHandler imWebSocketHandler;
    private final WebSocketAuthInterceptor authInterceptor;

    public WebSocketConfig(GroupWebSocketHandler groupWebSocketHandler,
                           ImWebSocketHandler imWebSocketHandler,
                           WebSocketAuthInterceptor authInterceptor) {
        this.groupWebSocketHandler = groupWebSocketHandler;
        this.imWebSocketHandler = imWebSocketHandler;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(groupWebSocketHandler, "/ws/group")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins("*");
        registry.addHandler(imWebSocketHandler, "/ws/im")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins("*");
    }
}