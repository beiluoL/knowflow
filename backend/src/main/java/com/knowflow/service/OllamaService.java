package com.knowflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.OllamaConfig;
import com.knowflow.mapper.OllamaConfigMapper;
import com.knowflow.vo.OllamaModelVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama 本地模型管理服务。
 * <p>
 * 通过 Ollama 原生 API（/api/tags、/api/show、/api/generate）实现：
 * - 连接测试与健康检查
 * - 获取已安装模型列表（名称、大小、量化级别）
 * - 模型加载/卸载（keep_alive 控制）
 * - 模型删除
 * - 配置持久化（服务地址、默认模型、参数预设）
 * <p>
 * 容错策略：连接超时 5 秒，请求超时 30 秒，失败时返回空列表或明确错误信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class OllamaService {

    private final OllamaConfigMapper ollamaConfigMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 默认 Ollama 服务地址 */
    public static final String DEFAULT_BASE_URL = "http://localhost:11434";

    // ==================== 配置管理 ====================

    /**
     * 获取用户的 Ollama 配置，不存在则返回默认配置（不持久化）。
     */
    public OllamaConfig getOrCreateConfig(Long userId) {
        OllamaConfig config = ollamaConfigMapper.selectOne(
                new LambdaQueryWrapper<OllamaConfig>()
                        .eq(OllamaConfig::getUserId, userId)
                        .last("LIMIT 1"));
        if (config == null) {
            config = new OllamaConfig();
            config.setUserId(userId);
            config.setBaseUrl(DEFAULT_BASE_URL);
            config.setTemperature(0.7);
            config.setTopP(0.9);
            config.setMaxTokens(4000);
            config.setTimeoutSeconds(60);
            ollamaConfigMapper.insert(config);
        }
        return config;
    }

    /**
     * 更新用户 Ollama 配置。
     */
    public OllamaConfig updateConfig(Long userId, OllamaConfig dto) {
        OllamaConfig config = getOrCreateConfig(userId);
        if (dto.getBaseUrl() != null) config.setBaseUrl(dto.getBaseUrl());
        if (dto.getDefaultModel() != null) config.setDefaultModel(dto.getDefaultModel());
        if (dto.getTemperature() != null) config.setTemperature(dto.getTemperature());
        if (dto.getTopP() != null) config.setTopP(dto.getTopP());
        if (dto.getMaxTokens() != null) config.setMaxTokens(dto.getMaxTokens());
        if (dto.getTimeoutSeconds() != null) config.setTimeoutSeconds(dto.getTimeoutSeconds());
        ollamaConfigMapper.updateById(config);
        return config;
    }

    // ==================== 连接测试 ====================

    /**
     * 测试 Ollama 服务连接。
     * @return { ok: true/false, latencyMs: n, version?: "...", error?: "..." }
     */
    public Map<String, Object> testConnection(String baseUrl) {
        long start = System.currentTimeMillis();
        try {
            String url = normalizeBaseUrl(baseUrl) + "/api/tags";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;
            if (resp.statusCode() == 200) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", true);
                result.put("latencyMs", latency);
                // 尝试提取版本信息
                try {
                    Map<String, Object> body = objectMapper.readValue(resp.body(), Map.class);
                    if (body.containsKey("models")) {
                        result.put("modelCount", ((List<?>) body.get("models")).size());
                    }
                } catch (Exception ignored) {
                }
                return result;
            } else {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", false);
                result.put("latencyMs", latency);
                result.put("error", "HTTP " + resp.statusCode() + "：" + truncate(resp.body(), 200));
                return result;
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Ollama 连接测试失败: baseUrl={}, err={}", baseUrl, e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("latencyMs", latency);
            result.put("error", "连接失败：" + e.getMessage());
            return result;
        }
    }

    // ==================== 模型列表 ====================

    /**
     * 获取 Ollama 已安装的模型列表。
     */
    public List<OllamaModelVO> listModels(String baseUrl) {
        try {
            String url = normalizeBaseUrl(baseUrl) + "/api/tags";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Ollama listModels 失败: status={}", resp.statusCode());
                return new ArrayList<>();
            }
            Map<String, Object> body = objectMapper.readValue(resp.body(), Map.class);
            List<Map<String, Object>> models = (List<Map<String, Object>>) body.get("models");
            if (models == null) return new ArrayList<>();

            List<OllamaModelVO> result = new ArrayList<>();
            for (Map<String, Object> m : models) {
                OllamaModelVO vo = new OllamaModelVO();
                vo.setName((String) m.get("name"));
                vo.setDigest((String) m.get("digest"));
                vo.setSize(m.get("size") != null ? ((Number) m.get("size")).longValue() : null);
                vo.setModifiedAt((String) m.get("modified_at"));
                vo.setSizeReadable(formatSize(vo.getSize()));

                // details 子对象
                Map<String, Object> details = (Map<String, Object>) m.get("details");
                if (details != null) {
                    vo.setFormat((String) details.get("format"));
                    vo.setFamily((String) details.get("family"));
                    vo.setParameterSize((String) details.get("parameter_size"));
                    vo.setQuantizationLevel((String) details.get("quantization_level"));
                }
                result.add(vo);
            }
            return result;
        } catch (Exception e) {
            log.warn("Ollama 获取模型列表失败: baseUrl={}, err={}", baseUrl, e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== 模型加载/卸载 ====================

    /**
     * 加载模型到内存（keep_alive 默认 10 分钟）。
     */
    public Map<String, Object> loadModel(String baseUrl, String modelName) {
        return sendKeepAlive(baseUrl, modelName, "10m");
    }

    /**
     * 从内存卸载模型（keep_alive = 0）。
     */
    public Map<String, Object> unloadModel(String baseUrl, String modelName) {
        return sendKeepAlive(baseUrl, modelName, 0);
    }

    /**
     * 通过 /api/generate 发送空 prompt 来控制模型内存驻留。
     * keep_alive: "10m" 加载、0 卸载。
     */
    private Map<String, Object> sendKeepAlive(String baseUrl, String modelName, Object keepAlive) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            body.put("prompt", "");
            body.put("stream", false);
            body.put("keep_alive", keepAlive);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(baseUrl) + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> result = new LinkedHashMap<>();
            if (resp.statusCode() == 200) {
                result.put("ok", true);
                result.put("action", keepAlive.equals(0) ? "unloaded" : "loaded");
                result.put("model", modelName);
            } else {
                result.put("ok", false);
                result.put("error", "HTTP " + resp.statusCode() + "：" + truncate(resp.body(), 300));
            }
            return result;
        } catch (Exception e) {
            log.warn("Ollama keep_alive 失败: model={}, keepAlive={}, err={}", modelName, keepAlive, e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    // ==================== 模型删除 ====================

    /**
     * 删除本地已安装的模型。
     */
    public Map<String, Object> deleteModel(String baseUrl, String modelName) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", modelName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(baseUrl) + "/api/delete"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> result = new LinkedHashMap<>();
            if (resp.statusCode() == 200 || resp.statusCode() == 404) {
                result.put("ok", true);
                result.put("model", modelName);
            } else {
                result.put("ok", false);
                result.put("error", "HTTP " + resp.statusCode() + "：" + truncate(resp.body(), 300));
            }
            return result;
        } catch (Exception e) {
            log.warn("Ollama 删除模型失败: model={}, err={}", modelName, e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    // ==================== 工具方法 ====================

    /** 规范化 baseUrl：去除尾部斜杠，移除 /v1 后缀（原生 API 不需要 /v1） */
    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return DEFAULT_BASE_URL;
        String url = baseUrl.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (url.endsWith("/v1")) url = url.substring(0, url.length() - 3);
        return url;
    }

    /** 将字节数格式化为人类可读大小 */
    private static String formatSize(Long bytes) {
        if (bytes == null) return "未知";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
