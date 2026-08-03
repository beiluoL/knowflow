package com.knowflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 轻量 SSE 流式 HTTP 客户端：用于 OpenAI / Anthropic 等基于 text/event-stream 的模型接口。
 * 不依赖 WebClient，简化依赖；按行解析 {@code data:} 帧，遇 {@code [DONE]} 结束。
 */
@Slf4j
public final class SseClient {

    private SseClient() {}

    public interface Handler {
        /** 每收到一个 data 帧的 JSON 节点时回调（OpenAI 为 choices[0].delta，Anthropic 为 content_block_delta）。 */
        void onData(JsonNode data);

        /** 连接或解析出错时回调，抛出 {@link AiException} 以中断流。 */
        void onError(String message);
    }

    /**
     * 发起 POST 流式请求并逐帧回调。
     * @param url 完整接口地址（需自行拼接 /chat/completions 或 /messages）
     * @param apiKey 鉴权密钥（OpenAI 走 Bearer；Anthropic 由调用方通过 extraHeaders 注入）
     */
    public static void stream(String url, String apiKey, Map<String, Object> body,
                              ObjectMapper objectMapper, Handler handler) {
        stream(url, apiKey, body, objectMapper, handler, null);
    }

    public static void stream(String url, String apiKey, Map<String, Object> body,
                              ObjectMapper objectMapper, Handler handler,
                              Map<String, String> extraHeaders) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000);
        factory.setReadTimeout(300000);
        RestTemplate rt = new RestTemplate(factory);

        org.springframework.http.HttpEntity<Map<String, Object>> entity;
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + (apiKey == null ? "" : apiKey));
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
        if (extraHeaders != null) {
            extraHeaders.forEach(headers::set);
        }
        entity = new org.springframework.http.HttpEntity<>(body, headers);

        try {
            org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> resp =
                    rt.postForEntity(url, entity, org.springframework.core.io.Resource.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                handler.onError("模型返回状态码 " + resp.getStatusCode());
                return;
            }
            try (InputStream raw = resp.getBody().getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(maybeGunzip(raw, resp), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    if (line.startsWith("data:")) {
                        String payload = line.substring(5).trim();
                        if ("[DONE]".equals(payload)) break;
                        try {
                            JsonNode node = objectMapper.readTree(payload);
                            handler.onData(node);
                        } catch (Exception parseEx) {
                            // 跳过无法解析的帧（如空对象、注释行）
                            log.debug("跳过 SSE 帧: {}", payload);
                        }
                    } else if (line.startsWith("event:")) {
                        // Anthropic 使用 event: 前缀，content 仍在后续 data: 帧
                        // 这里仅记录，具体数据仍由 data: 帧处理
                    }
                }
            }
        } catch (AiException e) {
            handler.onError(e.getMessage());
        } catch (Exception e) {
            handler.onError(e.getMessage());
        }
    }

    private static InputStream maybeGunzip(InputStream raw, org.springframework.http.ResponseEntity<?> resp) throws Exception {
        List<String> enc = resp.getHeaders().get(HttpHeaders.CONTENT_ENCODING);
        if (enc != null && enc.contains("gzip")) {
            return new GZIPInputStream(raw);
        }
        return raw;
    }
}
