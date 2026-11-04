package com.knowflow.framework.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** JWT 认证过滤器：每次请求解析并校验 token，校验通过后将用户信息写入 SecurityContext。 仅为持有有效、未撤销 token 的现存账号建立认证。 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtSubjectAuthenticator authenticator;

  /** 核心过滤逻辑：提取请求 token，依次校验有效性、黑名单， 按 token 主体读取当前账号和角色，避免已删除或降权账号继续使用旧权限。 */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    authenticator
        .authenticate(getTokenFromRequest(request))
        .ifPresent(
            result -> {
              var subject = result.subject();
              var authorities =
                  subject.authorities().stream().map(SimpleGrantedAuthority::new).toList();
              var authentication =
                  UsernamePasswordAuthenticationToken.authenticated(
                      subject.userId(), null, authorities);
              authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
              var context = SecurityContextHolder.createEmptyContext();
              context.setAuthentication(authentication);
              SecurityContextHolder.setContext(context);
            });
    filterChain.doFilter(request, response);
  }

  /**
   * 从 Authorization 头提取 Bearer token；非 Bearer 格式或为空时返回 null。 substring(7) 用于跳过 "Bearer "（含一个空格，共 7
   * 个字符）。
   *
   * <p>兼容查询参数 token：当 Header 缺失时，尝试从 query 参数 token 中读取， 用于图片/文件下载等无法附加 Authorization 头的场景（如
   * &lt;img src&gt;）。
   */
  private String getTokenFromRequest(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }
    // 查询参数 token（图片等场景）
    String queryToken = request.getParameter("token");
    if (StringUtils.hasText(queryToken)) {
      return queryToken;
    }
    return null;
  }
}
