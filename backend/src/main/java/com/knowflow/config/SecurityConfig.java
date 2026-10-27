package com.knowflow.config;

import com.knowflow.security.JwtAccessDeniedHandler;
import com.knowflow.security.JwtAuthenticationFilter;
import com.knowflow.security.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // F-11 修复：未登录→401，已登录无权限→403，语义分离
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // Only a continuation of an already-authorized request, never a new HTTP request.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll()
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                // 第三方 OAuth 登录与回调：未登录态可访问，回调后会签发 JWT
                                "/api/auth/oauth/**",
                                "/api/public/**",
                                // 上传文件静态资源
                                "/uploads/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/docs",
                                "/api/docs/{id}",
                                "/api/docs/recommend"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/learning/paths",
                                "/api/learning/paths/{id}",
                                // P0-1：`/api/learning/flashcards` 已从此白名单移除 ——
                                // learning_flashcard.user_id 为 NOT NULL，该表按设计即「用户私有闪卡」，
                                // 原 permitAll 叠加「条件全空→全表」的查询缺陷，导致匿名可读全站闪卡。
                                // 现改为需登录，并在 Service 层无条件按当前用户过滤。
                                // 管理员的全站视图走独立的 GET /api/admin/learning/flashcards（ADMIN 门禁）。
                                // G-CERT-01 数字证书：验证码核验可匿名（列表/详情仍需登录）
                                "/api/learning/certificates/verify"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/community/posts",
                                "/api/community/posts/{id}",
                                "/api/community/posts/{id}/comments",
                                // F-06：评论与回复只读接口匿名可浏览，发表/编辑/删除/点赞仍需登录
                                "/api/community/comments/post/{postId}",
                                "/api/community/comments/{commentId}/replies"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/knowledge/graph",
                                "/api/knowledge/entity-graph"
                        ).permitAll()
                        // 编程挑战只读接口：匿名可浏览赛道与排行榜，提交/个人统计仍需登录
                        .requestMatchers(HttpMethod.GET,
                                "/api/challenges",
                                "/api/challenges/{id}",
                                "/api/challenges/leaderboard"
                        ).permitAll()
                        // 全局排行榜匿名可浏览
                        .requestMatchers(HttpMethod.GET,
                                "/api/ranking"
                        ).permitAll()
                        // WebSocket 握手由 WebSocketAuthInterceptor 用 token 参数鉴权
                        .requestMatchers("/ws/**").permitAll()
                        // 技术英语词库只读接口匿名可浏览；/api/tech-english/me/** 仍要求登录
                        .requestMatchers(HttpMethod.GET,
                                "/api/tech-english/terms",
                                "/api/tech-english/terms/{id}",
                                "/api/tech-english/terms/{id}/pronunciations",
                                "/api/tech-english/filters",
                                // P2 划词查询：只传选区文本，不传整篇文档；匿名可查公共词库
                                "/api/tech-english/lookup",
                                // V3 按技术方向专题：匿名可浏览公开词库，登录用户额外返回个人学习状态
                                "/api/tech-english/topics",
                                "/api/tech-english/topics/{domain}"
                        ).permitAll()
                        // SSE 流式对话：JwtAuthenticationFilter 仍解析 token 设置 Authentication，
                        // permitAll 避免 SseEmitter 异步分发时 SecurityContext 丢失导致 AccessDeniedException
                        .requestMatchers("/api/chat/stream").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 默认仅管理员 JWT；显式开启的本地浏览器认证由独立过滤链处理。
                        .requestMatchers(
                                "/h2-console/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api-docs/**",
                                "/api-docs.yaml",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
