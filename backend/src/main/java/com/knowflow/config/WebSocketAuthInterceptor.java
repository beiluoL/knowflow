package com.knowflow.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 认证拦截器
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    @Value("${jwt.secret:knowflow-secret-key-for-jwt-token-generation}")
    private String jwtSecret;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = servletRequest.getServletRequest().getParameter("token");
            if (token != null && !token.isEmpty()) {
                try {
                    Claims claims = Jwts.parser()
                            .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
                    
                    Long userId = claims.get("userId", Long.class);
                    if (userId != null) {
                        attributes.put("userId", userId);
                        attributes.put("username", claims.get("username", String.class));
                        return true;
                    }
                } catch (Exception e) {
                    // Token 无效，拒绝连接
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手后处理，无需操作
    }
}