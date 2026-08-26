package com.knowflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 轻量 SSE 流式 HTTP 客户端：用于 OpenAI / Anthropic 等基于 text/event-stream 的模型接口。
 * <p>
 * 使用 {@link HttpClient}（Java 11+）发送请求，通过 {@link HttpResponse.BodyHandlers#ofInputStream()}
 * 获取原始输入流，正确处理 SSE chunked 响应。按行解析 {@code data:} 帧，遇 {@code [DONE]} 结束。
 */
@Slf4j
public final class SseClient {

    private SseClient() {}

    public interface Handler {
        void onData(JsonNode data);
        void onError(String message);
    }

    public static void stream(String url, String apiKey, Map<String, Object> body,
                              ObjectMapper objectMapper, Handler handler) {
        stream(url, apiKey, body, objectMapper, handler, null);
    }

    public static void stream(String url, String apiKey, Map<String, Object> body,
                              ObjectMapper objectMapper, Handler handler,
                              Map<String, String> extraHeaders) {
        try {
            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(180))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Authorization", "Bearer " + (apiKey == null ? "" : apiKey))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (extraHeaders != null) {
                extraHeaders.forEach(reqBuilder::header);
            }

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<InputStream> resp = client.send(
                    reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofInputStream());

            int statusCode = resp.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                String errBody = readAll(resp.body());
                handler.onError("模型返回状态码 " + statusCode + (errBody != null ? "：" + errBody : ""));
                return;
            }

            InputStream raw = resp.body();
            // 处理 gzip 压缩
            List<String> enc = resp.headers().allValues("Content-Encoding");
            if (enc != null && enc.contains("gzip")) {
                raw = new GZIPInputStream(raw);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(raw, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if ("[DONE]".equals(data)) break;
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            handler.onData(node);
                        } catch (Exception parseEx) {
                            log.debug("跳过 SSE 帧: {}", data);
                        }
                    } else if (line.startsWith("event:")) {
                        // Anthropic 使用 event: 前缀
                    }
                }
            }
        } catch (AiException e) {
            handler.onError(e.getMessage());
        } catch (Exception e) {
            handler.onError(e.getMessage());
        }
    }

    private static String readAll(InputStream is) {
        if (is == null) return null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
