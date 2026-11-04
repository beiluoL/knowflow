package com.knowflow.framework.web;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/** 跨域配置：全局放行 CORS 预检与跨域请求。 */
@Configuration
public class CorsConfig {

  @Bean
  public CorsFilter corsFilter(
      Environment environment, @Value("${knowflow.web.cors.allowed-origins:}") String origins) {
    CorsConfiguration config = new CorsConfiguration();
    List<String> configuredOrigins =
        Arrays.stream(origins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList();
    if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
      if (configuredOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
        throw new IllegalArgumentException("Production CORS requires explicit origins");
      }
      config.setAllowedOrigins(configuredOrigins);
    } else {
      config.setAllowedOriginPatterns(
          configuredOrigins.isEmpty() ? List.of("*") : configuredOrigins);
    }
    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Last-Event-ID"));
    // 允许跨域请求携带 Cookie 等凭证
    config.setAllowCredentials(true);
    // 预检结果缓存 1 小时（3600 秒），减少重复 OPTIONS 请求
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsFilter(source);
  }
}
