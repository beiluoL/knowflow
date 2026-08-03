package com.knowflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 非流式 JSON POST 工具：供 Anthropic / 文心 等非 OpenAI 协议适配器复用，
 * 返回完整响应文本。异常统一封装为 {@link AiException}。
 */
class SimplePost {

    private final String url;
    private final String apiKey;
    private final Map<String, String> extraHeaders;
    private final Map<String, Object> body;

    SimplePost(String url, String apiKey, Map<String, String> extraHeaders,
               Map<String, Object> body, ObjectMapper objectMapper) {
        this.url = url;
        this.apiKey = apiKey;
        this.extraHeaders = extraHeaders;
        this.body = body;
    }

    String execute() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000);
        factory.setReadTimeout(120000);
        RestTemplate rt = new RestTemplate(factory);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + (apiKey == null ? "" : apiKey));
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (extraHeaders != null) {
            extraHeaders.forEach(headers::set);
        }
        try {
            org.springframework.http.HttpEntity<Map<String, Object>> entity =
                    new org.springframework.http.HttpEntity<>(body, headers);
            org.springframework.http.ResponseEntity<String> resp =
                    rt.postForEntity(url, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new AiException("模型返回状态码 " + resp.getStatusCode());
            }
            return resp.getBody();
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("HTTP 请求失败：" + e.getMessage());
        }
    }
}
