package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.config.OAuthConfig;
import com.knowflow.dto.LoginDTO;
import com.knowflow.dto.RegisterDTO;
import com.knowflow.identity.oauth.OAuthStateService;
import com.knowflow.security.TokenBlacklistService;
import com.knowflow.service.UserService;
import com.knowflow.utils.JwtUtils;
import com.knowflow.vo.LoginVO;
import com.knowflow.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

/** 认证 REST 接口，提供登录、注册、获取当前用户与登出（token 失效）。 */
@Slf4j
@Tag(name = "认证接口")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final UserService userService;
  private final JwtUtils jwtUtils;
  private final TokenBlacklistService tokenBlacklistService;
  private final OAuthConfig oauthConfig;
  private final RestTemplate restTemplate;
  private final OAuthStateService oauthStates;

  @Operation(summary = "登录")
  @PostMapping("/login")
  public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
    return Result.success(userService.login(dto));
  }

  @Operation(summary = "注册")
  @PostMapping("/register")
  public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto) {
    return Result.success(userService.register(dto));
  }

  @Operation(summary = "获取当前用户信息")
  @GetMapping("/me")
  public Result<UserVO> me(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || "anonymousUser".equals(authentication.getPrincipal())) {
      return Result.error(401, "未登录");
    }
    Long userId = SecurityUtils.getCurrentUserId();
    return Result.success(userService.getCurrentUser(userId));
  }

  /** 登出：将当前 token 加入黑名单使其立即失效。 */
  @Operation(summary = "登出（使当前 token 失效）")
  @PostMapping("/logout")
  public Result<Void> logout(HttpServletRequest request) {
    String token = extractToken(request);
    if (token != null) {
      tokenBlacklistService.add(token, jwtUtils.getExpirationFromToken(token));
    }
    return Result.success();
  }

  // ====================== 第三方 OAuth 登录 ======================

  /** GitHub 登录：重定向到 GitHub 授权页。 前端点击「GitHub 登录」按钮时跳转到此接口，后端再 302 到 GitHub。 */
  @Operation(summary = "GitHub 登录（重定向到 GitHub 授权页）")
  @GetMapping("/oauth/github")
  public void githubAuth(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String state = issueOAuthState("github", request, response);
    String url =
        "https://github.com/login/oauth/authorize"
            + "?client_id="
            + oauthConfig.getGithub().getClientId()
            + "&redirect_uri="
            + URLEncoder.encode(oauthConfig.getGithub().getRedirectUri(), StandardCharsets.UTF_8)
            + "&scope=user:email&state="
            + state;
    response.sendRedirect(url);
  }

  /** GitHub 回调：GitHub 授权后带 code 回调到此接口， 后端用 code 换 access_token，再用 token 拉用户信息，最后生成 JWT 并重定向回前端。 */
  @Operation(summary = "GitHub 登录回调")
  @GetMapping("/oauth/github/callback")
  public void githubCallback(
      @RequestParam("code") String code,
      @RequestParam(value = "state", required = false) String state,
      @CookieValue(name = "kf_oauth_github", required = false) String binding,
      HttpServletResponse response)
      throws IOException {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
    if (!oauthStates.consume("github", state, binding)) {
      redirectToFrontendWithError(response, "授权状态无效或已过期，请重新登录");
      return;
    }
    try {
      // 1. 用 code 换 access_token
      String tokenUrl = "https://github.com/login/oauth/access_token";
      var form = new LinkedMultiValueMap<String, String>();
      form.add("client_id", oauthConfig.getGithub().getClientId());
      form.add("client_secret", oauthConfig.getGithub().getClientSecret());
      form.add("code", code);
      form.add("redirect_uri", oauthConfig.getGithub().getRedirectUri());
      var headers = new HttpHeaders();
      headers.setAccept(java.util.List.of(org.springframework.http.MediaType.APPLICATION_JSON));
      headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
      var entity = new org.springframework.http.HttpEntity<>(form, headers);
      Map<?, ?> tokenResp = restTemplate.postForObject(tokenUrl, entity, Map.class);
      String accessToken = tokenResp == null ? null : (String) tokenResp.get("access_token");
      if (accessToken == null) {
        log.warn("GitHub OAuth 换 token 失败");
        redirectToFrontendWithError(response, "GitHub 授权失败：未获取到 access_token");
        return;
      }

      // 2. 用 access_token 拉用户信息
      org.springframework.http.HttpHeaders userHeaders = new org.springframework.http.HttpHeaders();
      userHeaders.set("Authorization", "Bearer " + accessToken);
      userHeaders.set("Accept", "application/vnd.github+json");
      org.springframework.http.HttpEntity<Void> userEntity =
          new org.springframework.http.HttpEntity<>(userHeaders);
      @SuppressWarnings("unchecked")
      Map<String, Object> userResp =
          restTemplate
              .exchange(
                  "https://api.github.com/user",
                  org.springframework.http.HttpMethod.GET,
                  userEntity,
                  Map.class)
              .getBody();
      if (userResp == null || userResp.get("id") == null) {
        redirectToFrontendWithError(response, "GitHub 授权失败：未获取到用户信息");
        return;
      }
      String providerUid = String.valueOf(userResp.get("id"));
      String nickname = (String) userResp.get("name");
      if (nickname == null || nickname.isEmpty()) nickname = (String) userResp.get("login");
      String avatar = (String) userResp.get("avatar_url");
      String email = (String) userResp.get("email");

      // 3. 调用社交登录（自动注册/登录）并重定向回前端
      LoginVO vo = userService.oauthLogin("github", providerUid, nickname, avatar, email);
      redirectToFrontendWithToken(response, vo);
    } catch (Exception e) {
      log.warn("GitHub OAuth 回调失败: {}", e.getClass().getSimpleName());
      redirectToFrontendWithError(response, "GitHub 登录失败，请重试");
    }
  }

  /** 微信登录：重定向到微信扫码授权页。 前端点击「微信登录」按钮时跳转到此接口，后端再 302 到微信。 */
  @Operation(summary = "微信登录（重定向到微信授权页）")
  @GetMapping("/oauth/wechat")
  public void wechatAuth(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String state = issueOAuthState("wechat", request, response);
    String url =
        "https://open.weixin.qq.com/connect/qrconnect"
            + "?appid="
            + oauthConfig.getWechat().getAppId()
            + "&redirect_uri="
            + URLEncoder.encode(oauthConfig.getWechat().getRedirectUri(), StandardCharsets.UTF_8)
            + "&response_type=code"
            + "&scope=snsapi_login"
            + "&state="
            + state;
    response.sendRedirect(url);
  }

  /** 微信回调：微信授权后带 code 回调到此接口， 后端用 code 换 access_token，再用 token 拉用户信息，最后生成 JWT 并重定向回前端。 */
  @Operation(summary = "微信登录回调")
  @GetMapping("/oauth/wechat/callback")
  public void wechatCallback(
      @RequestParam("code") String code,
      @RequestParam(value = "state", required = false) String state,
      @CookieValue(name = "kf_oauth_wechat", required = false) String binding,
      HttpServletResponse response)
      throws IOException {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
    if (!oauthStates.consume("wechat", state, binding)) {
      redirectToFrontendWithError(response, "授权状态无效或已过期，请重新登录");
      return;
    }
    try {
      // 1. 用 code 换 access_token
      String tokenUrl =
          "https://api.weixin.qq.com/sns/oauth2/access_token"
              + "?appid="
              + oauthConfig.getWechat().getAppId()
              + "&secret="
              + oauthConfig.getWechat().getAppSecret()
              + "&code="
              + code
              + "&grant_type=authorization_code";
      @SuppressWarnings("unchecked")
      Map<String, Object> tokenResp = restTemplate.getForObject(tokenUrl, Map.class);
      String accessToken = tokenResp == null ? null : (String) tokenResp.get("access_token");
      String openid = tokenResp == null ? null : (String) tokenResp.get("openid");
      if (accessToken == null || openid == null) {
        log.warn("微信 OAuth 换 token 失败");
        redirectToFrontendWithError(response, "微信授权失败：未获取到 access_token");
        return;
      }

      // 2. 用 access_token + openid 拉用户信息
      String userUrl =
          "https://api.weixin.qq.com/sns/userinfo"
              + "?access_token="
              + accessToken
              + "&openid="
              + openid;
      @SuppressWarnings("unchecked")
      Map<String, Object> userResp = restTemplate.getForObject(userUrl, Map.class);
      if (userResp == null || userResp.get("openid") == null) {
        redirectToFrontendWithError(response, "微信授权失败：未获取到用户信息");
        return;
      }
      String providerUid = (String) userResp.get("openid");
      String nickname = (String) userResp.get("nickname");
      String avatar = (String) userResp.get("headimgurl");

      // 3. 调用社交登录（自动注册/登录）并重定向回前端
      LoginVO vo = userService.oauthLogin("wechat", providerUid, nickname, avatar, null);
      redirectToFrontendWithToken(response, vo);
    } catch (Exception e) {
      log.warn("微信 OAuth 回调失败: {}", e.getClass().getSimpleName());
      redirectToFrontendWithError(response, "微信登录失败：" + e.getMessage());
    }
  }

  // ====================== 内部工具方法 ======================

  private String issueOAuthState(
      String provider, HttpServletRequest request, HttpServletResponse response) {
    var issued = oauthStates.issue(provider);
    var cookie =
        ResponseCookie.from("kf_oauth_" + provider, issued.browserBinding())
            .httpOnly(true)
            .secure(request.isSecure())
            .sameSite("Lax")
            .path("/api/auth/oauth/" + provider + "/callback")
            .maxAge(OAuthStateService.TTL)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    return issued.state();
  }

  /** 从 Authorization 请求头中解析 Bearer token，无则返回 null。 */
  private String extractToken(HttpServletRequest request) {
    String bearer = request.getHeader("Authorization");
    if (bearer != null && bearer.startsWith("Bearer ")) {
      return bearer.substring(7);
    }
    return null;
  }

  /** 登录成功：带 token 重定向回前端 OAuth 回调页。 */
  private void redirectToFrontendWithToken(HttpServletResponse response, LoginVO vo)
      throws IOException {
    String base = oauthConfig.getFrontendBaseUrl().replaceAll("/+$", "");
    String url =
        base + "/oauth/callback?token=" + URLEncoder.encode(vo.getToken(), StandardCharsets.UTF_8);
    response.sendRedirect(url);
  }

  /** 登录失败：带 error 重定向回前端 OAuth 回调页。 */
  private void redirectToFrontendWithError(HttpServletResponse response, String error)
      throws IOException {
    String base = oauthConfig.getFrontendBaseUrl().replaceAll("/+$", "");
    String url = base + "/oauth/callback?error=" + URLEncoder.encode(error, StandardCharsets.UTF_8);
    response.sendRedirect(url);
  }
}
