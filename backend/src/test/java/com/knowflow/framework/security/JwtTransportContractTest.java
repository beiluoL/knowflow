package com.knowflow.framework.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knowflow.framework.websocket.WebSocketAuthInterceptor;
import com.knowflow.security.TokenBlacklistService;
import com.knowflow.utils.JwtUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTransportContractTest {
  @Test
  void bothTransportsUseCurrentSubjectAndRejectRevokedTokens() throws Exception {
    var jwt = new JwtUtils();
    ReflectionTestUtils.setField(jwt, "secret", "test-only-secret-for-transport-contract-hmac");
    ReflectionTestUtils.setField(jwt, "expire", 60_000L);
    var blacklist = new TokenBlacklistService();
    var authenticator =
        new JwtSubjectAuthenticator(
            jwt,
            blacklist,
            userId ->
                userId == 42
                    ? Optional.of(
                        new AuthenticationSubjectResolver.Subject(userId, List.of("ROLE_USER")))
                    : Optional.empty());
    var filter = new JwtAuthenticationFilter(authenticator);
    var socket = new WebSocketAuthInterceptor(authenticator);
    var token = jwt.generateToken(42L, "test-user", "ADMIN");
    var request = new MockHttpServletRequest("GET", "/ws");
    request.setParameter("token", token);
    var response = new MockHttpServletResponse();
    var attributes = new HashMap<String, Object>();
    var servletRequest = new ServletServerHttpRequest(request);
    var servletResponse = new ServletServerHttpResponse(response);

    try {
      var principal = new AtomicReference<Object>();
      filter.doFilter(
          request,
          response,
          (ignoredRequest, ignoredResponse) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            principal.set(authentication.getPrincipal());
            assertEquals(
                "ROLE_USER", authentication.getAuthorities().iterator().next().getAuthority());
          });
      assertEquals(42L, principal.get());
      assertTrue(socket.beforeHandshake(servletRequest, servletResponse, null, attributes));
      assertEquals(42L, attributes.get("userId"));

      blacklist.add(token, jwt.getExpirationFromToken(token));
      assertTrue(authenticator.authenticate(token).isEmpty());
      assertFalse(socket.beforeHandshake(servletRequest, servletResponse, null, new HashMap<>()));
      assertTrue(authenticator.authenticate(jwt.generateToken(43L, "deleted", "USER")).isEmpty());
      assertTrue(authenticator.authenticate("invalid").isEmpty());
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
