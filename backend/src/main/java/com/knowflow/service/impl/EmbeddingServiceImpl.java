package com.knowflow.service.impl;

import com.knowflow.service.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Embedding 向量服务实现：通过 OpenAI 兼容接口调用文本嵌入模型。
 * 配置项：ai.embedding-model（如 text-embedding-3-small）。
 * 未配置 embedding API 时自动降级返回空向量。
 */
@Slf4j
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private final RestTemplate restTemplate;

    @Value("${ai.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${ai.api-key:sk-placeholder}")
    private String apiKey;

    @Value("${ai.embedding-model:text-embedding-3-small}")
    private String model;

    private static final float EPSILON = 1e-12f;

    public EmbeddingServiceImpl(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    @Override
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        // 未配置有效 API Key 时降级
        if (apiKey == null || apiKey.isBlank() || "sk-placeholder".equals(apiKey)) {
            log.warn("AI API Key 未配置，embedding 服务降级返回空向量");
            return Collections.emptyList();
        }
        try {
            String url = baseUrl.replaceAll("/$", "") + "/embeddings";
            Map<String, Object> body = Map.of("model", model, "input", text);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> respBody = response.getBody();
            if (respBody == null || !respBody.containsKey("data")) {
                log.warn("embedding API 返回异常：{}", respBody);
                return Collections.emptyList();
            }
            List<Map<String, Object>> data = (List<Map<String, Object>>) respBody.get("data");
            if (data == null || data.isEmpty()) {
                return Collections.emptyList();
            }
            List<Double> raw = (List<Double>) data.get(0).get("embedding");
            if (raw == null) return Collections.emptyList();
            List<Float> result = new ArrayList<>(raw.size());
            for (Double v : raw) result.add(v.floatValue());
            return result;
        } catch (Exception e) {
            log.warn("embedding API 调用失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public double cosineSimilarity(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.size() != b.size() || a.isEmpty()) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            double va = a.get(i);
            double vb = b.get(i);
            dot += va * vb;
            normA += va * va;
            normB += vb * vb;
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom < EPSILON ? 0 : dot / denom;
    }
}
