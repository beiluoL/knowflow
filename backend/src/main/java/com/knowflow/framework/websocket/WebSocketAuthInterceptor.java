package com.knowflow.framework.websocket;

import com.knowflow.framework.security.JwtSubjectAuthenticator;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** WebSocket 认证拦截器 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

  private final JwtSubjectAuthenticator authenticator;

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes)
      throws Exception {
    if (!(request instanceof ServletServerHttpRequest servletRequest)) {
      return false;
    }
    var subject =
        authenticator.authenticate(servletRequest.getServletRequest().getParameter("token"));
    if (subject.isEmpty()) {
      return false;
    }
    attributes.put("userId", subject.get().subject().userId());
    attributes.put("username", subject.get().username());
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {
    // 握手后处理，无需操作
  }
}
