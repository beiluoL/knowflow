package com.knowflow.identity.oauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 验证 OAuth 状态与供应方、浏览器绑定且仅消费一次。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OAuthStateContractTest {
  @Autowired OAuthStateService states;
  @Autowired MockMvc mvc;

  @Test
  void consumesOnlyTheMatchingProviderAndBrowserOnce() {
    var issued = states.issue("github");
    var other = states.issue("github");
    assertFalse(states.consume("wechat", issued.state(), issued.browserBinding()));
    assertFalse(states.consume("github", issued.state(), other.browserBinding()));
    assertFalse(states.consume("github", issued.state(), null));
    assertTrue(states.consume("github", issued.state(), issued.browserBinding()));
    assertFalse(states.consume("github", issued.state(), issued.browserBinding()));
  }

  @Test
  void callbackWithoutStateRedirectsBeforeProviderExchange() throws Exception {
    mvc.perform(get("/api/auth/oauth/github/callback").param("code", "unused"))
        .andExpect(status().isFound())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(
            header()
                .string(
                    "Location", org.hamcrest.Matchers.containsString("/oauth/callback?error=")));
  }
}
