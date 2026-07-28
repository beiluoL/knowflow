package com.knowflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 第三方 OAuth 登录配置。
 * 对应 application.yml 中的 oauth.* 配置项。
 */
@Configuration
@ConfigurationProperties(prefix = "oauth")
@Data
public class OAuthConfig {

    private Github github = new Github();
    private Wechat wechat = new Wechat();
    /** 前端回调地址基础路径，例如 http://localhost:5173 */
    private String frontendBaseUrl = "http://localhost:5173";

    @Data
    public static class Github {
        private String clientId = "";
        private String clientSecret = "";
        /** 授权回调地址（后端接口），需与 GitHub OAuth App 配置一致 */
        private String redirectUri = "http://localhost:8080/api/auth/oauth/github/callback";
    }

    @Data
    public static class Wechat {
        private String appId = "";
        private String appSecret = "";
        /** 授权回调地址（后端接口），需与微信开放平台配置一致 */
        private String redirectUri = "http://localhost:8080/api/auth/oauth/wechat/callback";
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
