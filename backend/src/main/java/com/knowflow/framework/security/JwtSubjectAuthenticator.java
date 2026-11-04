package com.knowflow.framework.security;

import com.knowflow.security.TokenBlacklistService;
import com.knowflow.utils.JwtUtils;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 为 HTTP 与 WebSocket 提供统一的 JWT 主体验证。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtSubjectAuthenticator {
  private final JwtUtils jwtUtils;
  private final TokenBlacklistService blacklist;
  private final AuthenticationSubjectResolver subjects;

  public Optional<AuthenticatedSubject> authenticate(String token) {
    if (!StringUtils.hasText(token) || blacklist.isBlacklisted(token)) {
      return Optional.empty();
    }
    try {
      var claims = jwtUtils.parseToken(token);
      Long userId = claims.get("userId", Long.class);
      if (userId == null) {
        return Optional.empty();
      }
      return subjects
          .resolve(userId)
          .map(subject -> new AuthenticatedSubject(subject, claims.get("username", String.class)));
    } catch (RuntimeException exception) {
      log.debug("JWT 主体验证失败: {}", exception.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  public record AuthenticatedSubject(
      AuthenticationSubjectResolver.Subject subject, String username) {}
}
