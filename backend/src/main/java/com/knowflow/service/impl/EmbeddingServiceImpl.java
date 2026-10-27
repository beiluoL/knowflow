package com.knowflow.service.impl;

import com.knowflow.service.EmbeddingService;
import jakarta.annotation.PostConstruct;
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
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Embedding 向量服务实现：通过 OpenAI 兼容接口调用文本嵌入模型。
 *
 * <p>配置说明：嵌入模型与对话模型往往不是同一厂商，因此这里支持独立的
 * ai.embedding-base-url / ai.embedding-api-key。若未配置独立嵌入地址，则默认使用
 * 本地 Ollama，而不是回退到对话模型厂商。这样可避免「对话走 DeepSeek、嵌入也被迫
 * 发往 DeepSeek /embeddings（该接口不存在）」导致向量恒为空的问题。
 *
 * <p>未配置有效 Key 时自动降级返回空向量，调用方据此走关键词兜底而非报错。
 */
@Slf4j
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private final RestTemplate restTemplate;

    @Value("${ai.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${ai.api-key:sk-placeholder}")
    private String apiKey;

    @Value("${ai.embedding-base-url:}")
    private String embeddingBaseUrl;

    @Value("${ai.embedding-api-key:}")
    private String embeddingApiKey;

    @Value("${ai.embedding-model:nomic-embed-text}")
    private String model;

    @Value("${ai.embedding-batch-size:16}")
    private int batchSize;

    private static final float EPSILON = 1e-12f;
    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434/v1";
    private final AtomicBoolean localHealthChecked = new AtomicBoolean(false);
    private volatile boolean localReady = true;
    private volatile long nextLocalProbeAtNanos = Long.MAX_VALUE;
    private static final Duration LOCAL_RETRY_INTERVAL = Duration.ofSeconds(30);

    /** 占位 Key 前缀，命中即视为未配置 */
    private static final String PLACEHOLDER_KEY_PREFIX = "sk-placeholder";

    public EmbeddingServiceImpl(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(8))
                .build();
    }

    /**
     * 启动期做一次配置自检：把「配置错误导致语义检索静默失效」暴露在日志里，
     * 而不是等到用户搜索无结果才发现。
     */
    @PostConstruct
    public void checkConfig() {
        if (!isAvailable()) {
            log.warn("[Embedding] 未配置有效 API Key，语义检索将降级为关键词检索");
            return;
        }
        String url = resolveBaseUrl();
        if (isLocalOllama(url)) {
            checkLocalOllamaOnce();
            return;
        }
        log.info("[Embedding] 嵌入服务已就绪：base-url={}, model={}, batchSize={}", url, model, batchSize);
    }

    /** 优先使用嵌入专用地址，未配置则使用本地 Ollama；绝不回退对话模型地址。 */
    private String resolveBaseUrl() {
        return (embeddingBaseUrl != null && !embeddingBaseUrl.isBlank())
                ? embeddingBaseUrl : DEFAULT_OLLAMA_BASE_URL;
    }

    /** Embedding 凭据必须独立配置；本地 Ollama 无需 Key。 */
    private String resolveApiKey() {
        return embeddingApiKey == null ? "" : embeddingApiKey.trim();
    }

    @Override
    public boolean isAvailable() {
        if (isLocalOllama(resolveBaseUrl())) {
            if (localHealthChecked.get() && !localReady
                    && System.nanoTime() >= nextLocalProbeAtNanos) {
                probeLocalOllama();
            }
            return !localHealthChecked.get() || localReady;
        }
        String key = resolveApiKey();
        return key != null && !key.isBlank() && !key.startsWith(PLACEHOLDER_KEY_PREFIX);
    }

    private boolean isLocalOllama(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return uri.getPort() == 11434
                    && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void checkLocalOllamaOnce() {
        if (!localHealthChecked.compareAndSet(false, true)) {
            return;
        }
        probeLocalOllama();
    }

    @SuppressWarnings("unchecked")
    private synchronized void probeLocalOllama() {
        try {
            String tagsUrl = resolveBaseUrl().replaceAll("/v1/?$", "") + "/api/tags";
            Map<String, Object> response = restTemplate.getForObject(tagsUrl, Map.class);
            List<Map<String, Object>> models = response == null ? List.of()
                    : (List<Map<String, Object>>) response.getOrDefault("models", List.of());
            boolean installed = models.stream().anyMatch(item -> {
                Object name = item.get("name");
                return name != null && name.toString().startsWith(model);
            });
            if (installed) {
                localReady = true;
                nextLocalProbeAtNanos = Long.MAX_VALUE;
                log.info("[Embedding] 本地 Ollama 已就绪：model={}", model);
            } else {
                markLocalUnavailable();
                log.warn("[Embedding] Ollama 已运行但未安装 {}，请执行：ollama pull {}。"
                        + "安装前语义检索会降级为关键词检索", model, model);
            }
        } catch (Exception e) {
            markLocalUnavailable();
            log.warn("[Embedding] 无法连接本地 Ollama，请执行 `ollama serve`，并确认已运行 "
                    + "`ollama pull {}`。当前语义检索将降级为关键词检索", model);
        }
    }

    private void markLocalUnavailable() {
        localHealthChecked.set(true);
        localReady = false;
        nextLocalProbeAtNanos = System.nanoTime() + LOCAL_RETRY_INTERVAL.toNanos();
    }

    @Override
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        List<List<Float>> result = embedBatch(List.of(text));
        return result.isEmpty() ? Collections.emptyList() : result.get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        // 预填空向量，保证返回列表与入参严格等长，避免调用方下标错位
        List<List<Float>> results = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            results.add(Collections.emptyList());
        }
        if (!isAvailable()) {
            log.warn("[Embedding] 服务不可用，批量向量化降级返回空向量");
            return results;
        }

        int effectiveBatch = batchSize > 0 ? batchSize : 1;
        for (int start = 0; start < texts.size(); start += effectiveBatch) {
            int end = Math.min(start + effectiveBatch, texts.size());
            List<String> slice = texts.subList(start, end);
            // 单批失败不影响其余批次，对应位置保留空向量
            List<List<Float>> batchResult = requestEmbeddings(slice);
            for (int i = 0; i < batchResult.size() && start + i < results.size(); i++) {
                results.set(start + i, batchResult.get(i));
            }
        }
        return results;
    }

    /**
     * 发起一次 /embeddings 请求。OpenAI 兼容协议的 input 支持字符串数组，
     * 响应 data 数组带 index 字段用于回填顺序（部分实现不保证返回顺序）。
     */
    @SuppressWarnings("unchecked")
    private List<List<Float>> requestEmbeddings(List<String> texts) {
        List<List<Float>> batch = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            batch.add(Collections.emptyList());
        }
        try {
            String url = resolveBaseUrl().replaceAll("/$", "") + "/embeddings";
            Map<String, Object> body = Map.of("model", model, "input", texts);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String embeddingKey = resolveApiKey();
            if (!embeddingKey.isBlank()) {
                headers.setBearerAuth(embeddingKey);
            }
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> respBody = response.getBody();
            if (respBody == null || !respBody.containsKey("data")) {
                log.warn("[Embedding] API 返回异常：{}", respBody);
                return batch;
            }
            List<Map<String, Object>> data = (List<Map<String, Object>>) respBody.get("data");
            if (data == null || data.isEmpty()) {
                return batch;
            }
            for (int i = 0; i < data.size(); i++) {
                Map<String, Object> item = data.get(i);
                Object idxObj = item.get("index");
                int idx = (idxObj instanceof Number) ? ((Number) idxObj).intValue() : i;
                List<Number> raw = (List<Number>) item.get("embedding");
                if (raw == null || idx < 0 || idx >= batch.size()) {
                    continue;
                }
                List<Float> vec = new ArrayList<>(raw.size());
                for (Number v : raw) {
                    vec.add(v.floatValue());
                }
                batch.set(idx, vec);
            }
            return batch;
        } catch (Exception e) {
            if (isLocalOllama(resolveBaseUrl())) {
                markLocalUnavailable();
            }
            log.warn("[Embedding] API 调用失败（本批 {} 条降级为空向量）: {}", texts.size(), e.getMessage());
            return batch;
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
