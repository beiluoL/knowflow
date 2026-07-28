package com.knowflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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

    /** 可选模型列表，供前端对话模型切换使用（逗号分隔或 YAML 列表）。 */
    private List<String> models;

    /** 单次最大输出 token 数，默认 8000，支持万字长文。 */
    private int maxTokens = 8000;

    /** 请求超时时间（秒），默认 180 秒，长文本生成需要更长时间。 */
    private int timeoutSeconds = 180;

    /** 单条消息最大字符数限制，超出时提示用户（约 1 万汉字）。 */
    private int maxContentChars = 20000;
}
