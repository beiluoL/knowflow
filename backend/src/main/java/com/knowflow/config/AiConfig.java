package com.knowflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 服务配置：绑定配置文件 ai.* 前缀（apiKey / baseUrl / model）。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiConfig {

    private String apiKey;

    private String baseUrl;

    private String model;
}
