package com.knowflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.config.AiProviderRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Anthropic Claude 协议适配器：使用 /v1/messages 接口，鉴权走 {@code x-api-key} 与
 * {@code anthropic-version}。支持 tool_use 工具调用与 SSE 流式（content_block_delta）。
 */
@Slf4j
public class AnthropicAdapter implements ModelAdapter {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final ObjectMapper objectMapper;

    public AnthropicAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProviderRegistry.Protocol protocol() {
        return AiProviderRegistry.Protocol.ANTHROPIC;
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        Map<String, Object> body = buildBody(request, false);
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", request.apiKey);
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        String url = ensureMessagesUrl(request.baseUrl);
        try {
            String resp = postJson(url, request.apiKey, headers, body);
            return parseMessage(resp);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("Anthropic 调用失败：" + e.getMessage());
        }
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<TokenDelta> onToken, Consumer<StreamDone> onDone) {
        Map<String, Object> body = buildBody(request, true);
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", request.apiKey);
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        String url = ensureMessagesUrl(request.baseUrl);

        StringBuilder full = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        Map<String, StringBuilder> argBuffers = new LinkedHashMap<>();

        SseClient.stream(url, request.apiKey, body, objectMapper, new SseClient.Handler() {
            @Override
            public void onData(JsonNode node) {
                JsonNode delta = node.get("delta");
                if (delta == null) return;
                JsonNode text = delta.get("text");
                if (text != null && !text.isNull()) {
                    full.append(text.asText());
                    onToken.accept(new TokenDelta(text.asText()));
                }
                JsonNode inputJson = delta.get("input_json_delta");
                if (inputJson != null && inputJson.has("partial_json")) {
                    String frag = inputJson.get("partial_json").asText();
                    String tcId = currentToolId(node);
                    argBuffers.computeIfAbsent(tcId, k -> new StringBuilder()).append(frag);
                }
                JsonNode toolUse = delta.get("tool_use");
                if (toolUse != null) {
                    ToolCall call = new ToolCall();
                    call.id = toolUse.has("id") ? toolUse.get("id").asText() : "tool_" + toolCalls.size();
                    call.name = toolUse.has("name") ? toolUse.get("name").asText() : null;
                    toolCalls.add(call);
                    argBuffers.put(call.id, new StringBuilder());
                }
            }

            @Override
            public void onError(String message) {
                throw new AiException("Anthropic 流式响应异常：" + message);
            }
        }, headers);

        for (ToolCall call : toolCalls) {
            StringBuilder sb = argBuffers.get(call.id);
            if (sb != null) {
                call.arguments = sb.toString();
            }
        }
        onDone.accept(new StreamDone(full.toString(), toolCalls));
    }

    private String currentToolId(JsonNode node) {
        // Anthropic 的 input_json_delta 不直接带 tool id，按顺序维护当前工具
        JsonNode index = node.get("index");
        return index != null ? "idx_" + index.asText() : "idx_0";
    }

    private Map<String, Object> buildBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.model);
        body.put("max_tokens", request.maxTokens != null ? request.maxTokens : 4096);
        if (request.temperature != null) body.put("temperature", request.temperature);
        // 拆分 system 与 messages
        List<Map<String, Object>> msgs = new ArrayList<>();
        StringBuilder system = new StringBuilder();
        for (ChatMessage m : request.messages) {
            if ("system".equals(m.role)) {
                system.append(m.content).append("\n");
            } else if ("tool".equals(m.role)) {
                Map<String, Object> toolMsg = new HashMap<>();
                toolMsg.put("role", "user");
                Map<String, Object> content = new HashMap<>();
                content.put("type", "tool_result");
                content.put("tool_use_id", m.toolCallId);
                content.put("content", m.content);
                toolMsg.put("content", List.of(content));
                msgs.add(toolMsg);
            } else {
                Map<String, Object> o = new HashMap<>();
                o.put("role", m.role);
                o.put("content", m.content);
                msgs.add(o);
            }
        }
        if (!system.isEmpty()) body.put("system", system.toString().trim());
        body.put("messages", msgs);
        if (stream) body.put("stream", true);
        if (request.tools != null && !request.tools.isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ToolSpec spec : request.tools) {
                Map<String, Object> t = new HashMap<>();
                t.put("name", spec.name);
                t.put("description", spec.description);
                t.put("input_schema", spec.parameters);
                tools.add(t);
            }
            body.put("tools", tools);
        }
        return body;
    }

    private ChatResult parseMessage(String resp) {
        try {
            JsonNode root = objectMapper.readTree(resp);
            if (root.has("error")) {
                throw new AiException("Anthropic 错误：" + root.get("error").get("message").asText());
            }
            JsonNode content = root.get("content");
            StringBuilder text = new StringBuilder();
            List<ToolCall> toolCalls = new ArrayList<>();
            if (content != null && content.isArray()) {
                for (JsonNode block : content) {
                    String type = block.has("type") ? block.get("type").asText() : "";
                    if ("text".equals(type)) {
                        text.append(block.get("text").asText());
                    } else if ("tool_use".equals(type)) {
                        ToolCall call = new ToolCall();
                        call.id = block.has("id") ? block.get("id").asText() : null;
                        call.name = block.has("name") ? block.get("name").asText() : null;
                        call.arguments = block.has("input") ? block.get("input").toString() : "{}";
                        toolCalls.add(call);
                    }
                }
            }
            return new ChatResult(text.toString(), toolCalls);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("解析 Anthropic 返回失败：" + e.getMessage());
        }
    }

    private String postJson(String url, String apiKey, Map<String, String> headers, Map<String, Object> body) {
        // 复用 SseClient 的 HTTP 能力：非流式直接读取完整响应
        SimplePost post = new SimplePost(url, apiKey, headers, body, objectMapper);
        return post.execute();
    }

    static String ensureMessagesUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new AiException("缺少接口地址 baseUrl");
        }
        String u = baseUrl.trim();
        if (!u.endsWith("/")) u = u + "/";
        if (u.endsWith("/v1/")) return u + "messages";
        if (u.endsWith("/v1")) return u + "/messages";
        return u + "messages";
    }
}
