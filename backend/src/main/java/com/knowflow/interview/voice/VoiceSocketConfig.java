package com.knowflow.interview.voice;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** 独立精确Origin与一次性票据，不修改旧IM/群聊的认证流程。 */
@Configuration
@EnableWebSocket
@EnableScheduling
@RequiredArgsConstructor
public class VoiceSocketConfig implements WebSocketConfigurer {

  private final InterviewVoiceSocket handler;
  private final VoiceAccess access;
  private final VoiceTransportProperties config;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    String[] origins = config.validOrigins().toArray(String[]::new);
    registry
      .addHandler(handler, "/ws/interview/voice/{sessionId}")
      .setAllowedOrigins(origins)
      .addInterceptors(
        new HandshakeInterceptor() {
          public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler selected,
            Map<String, Object> attributes
          ) {
            try {
              if (
                !(request instanceof ServletServerHttpRequest servlet)
              ) return false;
              String path = request.getURI().getPath();
              String last = path.substring(path.lastIndexOf('/') + 1);
              if (!last.matches("[1-9][0-9]{0,18}")) return false;
              var identity = access.consume(
                servlet.getServletRequest().getParameter("ticket"),
                Long.parseLong(last),
                request.getHeaders().getOrigin()
              );
              attributes.put("voiceIdentity", identity);
              return true;
            } catch (RuntimeException invalid) {
              response.setStatusCode(
                org.springframework.http.HttpStatus.FORBIDDEN
              );
              return false;
            }
          }

          public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler selected,
            Exception failure
          ) {}
        }
      );
  }
}
