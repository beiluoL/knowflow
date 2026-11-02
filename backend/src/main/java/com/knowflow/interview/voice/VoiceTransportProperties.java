package com.knowflow.interview.voice;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 必须显式开启并配置精确Origin，不继承IM通道的宽泛来源。 */
@Data
@Component
@ConfigurationProperties(prefix = "knowflow.interview.voice")
public class VoiceTransportProperties {

  private boolean enabled = false;
  private List<String> allowedOrigins = List.of();
  private int maxConnectionsPerUser = 2;

  public List<String> validOrigins() {
    return allowedOrigins == null
      ? List.of()
      : allowedOrigins
          .stream()
          .filter(VoiceTransportProperties::validOrigin)
          .distinct()
          .toList();
  }

  public boolean allows(String origin) {
    return validOrigin(origin) && validOrigins().contains(origin);
  }

  private static boolean validOrigin(String origin) {
    if (origin == null || origin.length() > 255) return false;
    try {
      var uri = java.net.URI.create(origin);
      return (
        ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) &&
        uri.getHost() != null &&
        uri.getUserInfo() == null &&
        uri.getRawQuery() == null &&
        uri.getRawFragment() == null &&
        (uri.getRawPath() == null || uri.getRawPath().isEmpty()) &&
        uri.getPort() <= 65535 &&
        uri.getPort() != 0
      );
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }
}
