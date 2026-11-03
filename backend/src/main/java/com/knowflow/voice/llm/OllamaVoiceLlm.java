package com.knowflow.voice.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.voice.VoiceRuntimeProperties;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * 本机 Ollama 语音对话客户端。
 * <p>必须走原生 {@code /api/chat} 并显式 {@code think=false}：实测 qwen3.5:9b 在
 * OpenAI 兼容端点上只流 reasoning，content 恒为空，直接接 TTS 会「有思考没回答」。
 */
@Component
public class OllamaVoiceLlm {

    private static final int MAX_MODEL_RESPONSE_CHARS = 12000;

    private final VoiceRuntimeProperties.Ollama config;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public OllamaVoiceLlm(VoiceRuntimeProperties properties, ObjectMapper json) {
        this.config = properties.getOllama();
        this.json = json;
    }

    public boolean configured() {
        return config.usable() && loopback(config.getBaseUrl());
    }

    public String model() {
        return config.getModel();
    }

    public record Status(boolean ok, boolean modelPresent, List<String> models, long latencyMs, String detail) { }

    /** 只读 /api/tags，不触发推理、不产生费用。 */
    public Status status() {
        if (!configured()) return new Status(false, false, List.of(), 0, "未启用或地址不是回环地址");
        long started = System.nanoTime();
        try {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(base() + "/api/tags"))
                            .timeout(Duration.ofMillis(1500)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            if (response.statusCode() != 200) {
                return new Status(false, false, List.of(), latency, "Ollama 返回 " + response.statusCode());
            }
            JsonNode root = json.readTree(response.body());
            List<String> models = new ArrayList<>();
            boolean present = false;
            for (JsonNode model : root.path("models")) {
                String name = model.path("name").asText("");
                if (!name.isBlank()) {
                    models.add(name);
                    if (name.equals(config.getModel()) || name.startsWith(config.getModel() + ":")) present = true;
                }
            }
            return new Status(true, present, List.copyOf(models), latency,
                    present ? "模型已就绪" : "Ollama 在线，但未找到模型 " + config.getModel());
        } catch (Exception unavailable) {
            return new Status(false, false, List.of(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), "Ollama 未运行");
        }
    }

    public record Message(String role, String content) { }

    public record Result(String text, long ttftMs, long totalMs, int charsPerSecond, boolean cancelled) { }

    /**
     * 流式生成。cancelled 为真时立即停止读取并返回已生成内容，
     * 调用方负责关闭连接（读取循环退出即释放）。
     */
    public Result stream(List<Message> messages, Consumer<String> onDelta, BooleanSupplier cancelled) {
        if (!configured()) throw new IllegalStateException("本地大模型未启用");
        if (messages == null || messages.isEmpty()) throw new IllegalArgumentException("消息不能为空");
        long started = System.nanoTime();
        long firstAt = 0;
        StringBuilder text = new StringBuilder();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.getModel());
        payload.put("messages", messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList());
        payload.put("stream", true);
        payload.put("think", config.isThink());
        payload.put("options", Map.of("num_predict", Math.max(32, config.getMaxTokens()), "temperature", 0.6));

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/api/chat"))
                    .timeout(Duration.ofSeconds(Math.max(10, Math.min(300, config.getTimeoutSeconds()))))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                try (InputStream ignored = response.body()) {
                    throw new IllegalStateException("Ollama 返回 " + response.statusCode());
                }
            }
            try (InputStream input = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelled.getAsBoolean()) {
                        return result(text, started, firstAt, true);
                    }
                    if (line.isBlank()) continue;
                    JsonNode node = json.readTree(line);
                    JsonNode message = node.path("message");
                    String delta = message.path("content").asText("");
                    if (!delta.isEmpty()) {
                        if (firstAt == 0) firstAt = System.nanoTime();
                        text.append(delta);
                        if (text.length() > MAX_MODEL_RESPONSE_CHARS) throw new IllegalStateException("模型输出超过限制");
                        onDelta.accept(delta);
                    }
                    if (node.path("done").asBoolean(false)) break;
                }
            }
            return result(text, started, firstAt, false);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("本地模型调用被中断");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("本地模型不可用");
        }
    }

    private Result result(StringBuilder text, long started, long firstAt, boolean cancelled) {
        long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        long ttft = firstAt == 0 ? -1 : TimeUnit.NANOSECONDS.toMillis(firstAt - started);
        double seconds = Math.max(0.001, totalMs / 1000.0);
        return new Result(text.toString(), ttft, totalMs, (int) Math.round(text.length() / seconds), cancelled);
    }

    private String base() {
        String value = config.getBaseUrl();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean loopback(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
