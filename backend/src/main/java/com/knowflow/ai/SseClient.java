package com.knowflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

            try (InputStream bodyStream = resp.body()) {
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    throw new AiException("模型返回状态码 " + resp.statusCode());
                }
                readFrames(bodyStream, resp.headers().allValues("Content-Encoding").contains("gzip"),
                        objectMapper, handler);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            handler.onError("模型请求已中断");
        } catch (AiException failure) {
            handler.onError(failure.getMessage());
        } catch (IOException | RuntimeException failure) {
            handler.onError(AiTransportErrors.from("模型流式请求失败", failure).getMessage());
        }
    }

    static void readFrames(InputStream body, boolean gzip, ObjectMapper mapper, Handler handler)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                gzip ? new GZIPInputStream(body) : body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) break;

                JsonNode node;
                try {
                    node = mapper.readTree(data);
                } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
                    log.debug("跳过无法解析的模型 SSE 帧");
                    continue;
                }
                if (node != null && node.isObject()) {
                    handler.onData(node);
                }
            }
        }
    }
}
