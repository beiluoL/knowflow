package com.knowflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses API 流式客户端，用于读取推理模型的 Reasoning Summary。
 * <p>
 * 仅当模型能力声明为 {@code summary}（如 o 系列 / gpt-5）且用户开启深度思考时使用。
 * 官方 Chat Completions 不返回推理摘要，因此必须走 {@code /v1/responses}：
 * <ul>
 *   <li>{@code response.reasoning_summary_text.delta} → 思考摘要增量</li>
 *   <li>{@code response.output_text.delta} → 最终回答增量</li>
 *   <li>{@code response.failed} / {@code error} → 失败</li>
 * </ul>
 * 不生成任何本地伪造的推理文本；Summary 也不得被标称为完整内部思维链。
 */
public class OpenAiResponsesStreamClient {

    public interface Handler {
        void onReasoning(String delta);
        void onContent(String delta);
    }

    private final ObjectMapper objectMapper;

    public OpenAiResponsesStreamClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void stream(ModelAdapter.ChatRequest request, Handler handler) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model);
        List<Map<String, Object>> input = new ArrayList<>();
        for (ModelAdapter.ChatMessage message : request.messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.role);
            item.put("content", message.content);
            input.add(item);
        }
        body.put("input", input);
        body.put("stream", true);
        // 显式关闭服务端存储，降低推理内容被长期保留的风险
        body.put("store", false);
        body.put("reasoning", Map.of("summary", "auto"));
        if (request.maxTokens != null) {
            body.put("max_output_tokens", request.maxTokens);
        }

        SseClient.stream(ensureResponsesUrl(request.baseUrl), request.apiKey, body, objectMapper,
                new SseClient.Handler() {
                    @Override
                    public void onData(JsonNode frame) {
                        String type = frame.has("type") ? frame.get("type").asText() : "";
                        if ("response.reasoning_summary_text.delta".equals(type)) {
                            String delta = frame.has("delta") ? frame.get("delta").asText() : "";
                            if (!delta.isEmpty()) handler.onReasoning(delta);
                            return;
                        }
                        if ("response.output_text.delta".equals(type)) {
                            String delta = frame.has("delta") ? frame.get("delta").asText() : "";
                            if (!delta.isEmpty()) handler.onContent(delta);
                            return;
                        }
                        if ("response.failed".equals(type) || "error".equals(type)) {
                            JsonNode error = frame.get("error");
                            String message = error != null && error.has("message")
                                    ? error.get("message").asText() : "模型返回失败事件";
                            throw new AiException("模型流式响应异常：" + message);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        throw new AiException("模型流式响应异常：" + message);
                    }
                });
    }

    static String ensureResponsesUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new AiException("缺少接口地址 baseUrl");
        }
        String u = baseUrl.trim();
        if (!u.endsWith("/")) u = u + "/";
        if (u.endsWith("/v1/")) return u + "responses";
        if (u.endsWith("/v1")) return u + "/responses";
        return u + "responses";
    }
}
