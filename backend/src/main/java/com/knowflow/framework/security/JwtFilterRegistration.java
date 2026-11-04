package com.knowflow.framework.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** JWT 过滤器仅由 Spring Security 的链注册。 */
@Configuration
class JwtFilterRegistration {
  @Bean
  FilterRegistrationBean<JwtAuthenticationFilter> jwtServletRegistration(
      JwtAuthenticationFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
