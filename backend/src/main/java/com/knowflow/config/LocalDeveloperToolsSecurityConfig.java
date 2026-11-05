package com.knowflow.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.identity.entity.SysUser;
import com.knowflow.identity.mapper.SysUserMapper;
import com.knowflow.security.JwtAccessDeniedHandler;
import com.knowflow.security.JwtAuthenticationEntryPoint;
import com.knowflow.framework.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import java.util.List;
import java.util.Set;

/** 浏览器 Basic 认证仅用于显式启用且仅监听回环地址的本地工具。 */
@Configuration
@Profile("local & !prod & !production")
@ConditionalOnProperty(name = "knowflow.developer-tools.enabled", havingValue = "true")
@RequiredArgsConstructor
public class LocalDeveloperToolsSecurityConfig {
    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
    private final SysUserMapper users;
    private final PasswordEncoder passwords;
    private final JwtAuthenticationFilter jwtFilter;
    private final JwtAuthenticationEntryPoint entryPoint;
    private final JwtAccessDeniedHandler deniedHandler;

    @Bean
    @Order(0)
    public SecurityFilterChain localDeveloperToolsChain(HttpSecurity http) throws Exception {
        var browserEntryPoint = (org.springframework.security.web.AuthenticationEntryPoint) (request, response, failure) -> {
            response.setHeader("WWW-Authenticate", "Basic realm=\"KnowFlow local tools\"");
            entryPoint.commence(request, response, failure);
        };
        // 同时匹配 DispatcherServlet 与 H2 独立 Servlet 映射下的请求路径。
        http.securityMatcher(new OrRequestMatcher(
                        new AntPathRequestMatcher("/h2-console/**"),
                        new AntPathRequestMatcher("/swagger-ui/**"),
                        new AntPathRequestMatcher("/swagger-ui.html"),
                        new AntPathRequestMatcher("/api-docs/**"),
                        new AntPathRequestMatcher("/api-docs.yaml"),
                        new AntPathRequestMatcher("/v3/api-docs/**"),
                        new AntPathRequestMatcher("/v3/api-docs.yaml")))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                // 认证管理器仅限本过滤链，Basic 凭据不得用于认证普通业务 API。
                .authenticationManager(new ProviderManager(new LocalToolsAuthenticationProvider()))
                .httpBasic(basic -> basic.authenticationEntryPoint(browserEntryPoint))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(browserEntryPoint).accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(auth -> auth.anyRequest().access((authentication, context) -> {
                    Authentication actor = authentication.get();
                    boolean allowed = LOOPBACK.contains(context.getRequest().getRemoteAddr())
                            && actor != null && actor.isAuthenticated() && actor.getPrincipal() instanceof Long;
                    if (allowed) {
                        // 读取数据库中的当前身份，不信任 URL 或过期 JWT 中的用户名、角色。
                        SysUser user = users.selectById((Long) actor.getPrincipal());
                        allowed = user != null && Integer.valueOf(0).equals(user.getDeleted())
                                && ("ADMIN".equals(user.getRole()) || "seed-personal-v1".equals(user.getUsername()));
                    }
                    return new AuthorizationDecision(allowed);
                }))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private class LocalToolsAuthenticationProvider implements AuthenticationProvider {
        @Override public Authentication authenticate(Authentication authentication) {
            SysUser user = users.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, authentication.getName()));
            if (user == null || !Integer.valueOf(0).equals(user.getDeleted())
                    || authentication.getCredentials() == null
                    || !passwords.matches(authentication.getCredentials().toString(), user.getPassword())) {
                throw new BadCredentialsException("用户名或密码错误");
            }
            return new UsernamePasswordAuthenticationToken(user.getId(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        }

        @Override public boolean supports(Class<?> authentication) {
            return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
        }
    }
}
