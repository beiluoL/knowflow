package com.knowflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.config.AiProviderRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 百度文心一言（千帆）协议适配器：先以 apiKey+secret 换取 access_token，再调用对话接口。
 * 工具调用采用文心 {@code functions} 字段（各模型能力不一，工具编排视为受限能力）。
 */
@Slf4j
public class QianfanAdapter implements ModelAdapter {

    private final ObjectMapper objectMapper;

    public QianfanAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProviderRegistry.Protocol protocol() {
        return AiProviderRegistry.Protocol.QIANFAN;
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        String token = obtainToken(request);
        String url = buildChatUrl(request.baseUrl, request.model, token);
        Map<String, Object> body = buildBody(request);
        String resp = new SimplePost(url, null, null, body, objectMapper).execute();
        return parseResponse(resp);
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<TokenDelta> onToken, Consumer<StreamDone> onDone) {
        String token = obtainToken(request);
        String url = buildChatUrl(request.baseUrl, request.model, token);
        Map<String, Object> body = buildBody(request);
        body.put("stream", true);
        StringBuilder full = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        SseClient.stream(url, null, body, objectMapper, new SseClient.Handler() {
            @Override
            public void onData(JsonNode node) {
                JsonNode result = node.get("result");
                if (result != null && !result.isNull()) {
                    full.append(result.asText());
                    onToken.accept(new TokenDelta(result.asText()));
                }
                JsonNode fn = node.get("function_call");
                if (fn != null && fn.has("name")) {
                    ToolCall call = new ToolCall();
                    call.id = "qf_" + toolCalls.size();
                    call.name = fn.get("name").asText();
                    call.arguments = fn.has("arguments") ? fn.get("arguments").toString() : "{}";
                    toolCalls.add(call);
                }
            }

            @Override
            public void onError(String message) {
                throw new AiException("文心流式响应异常：" + message);
            }
        });
        onDone.accept(new StreamDone(full.toString(), toolCalls));
    }

    /** 用 apiKey(apiKey)+secret 换取 access_token。 */
    private String obtainToken(ChatRequest request) {
        if (request.apiSecret == null || request.apiSecret.isBlank()) {
            throw new AiException("文心接口需要 secret 换取 access_token");
        }
        String tokenUrl = "https://qianfan.baidubce.com/oauth/2.0/token"
                + "?grant_type=client_credentials"
                + "&client_id=" + request.apiKey
                + "&client_secret=" + request.apiSecret;
        try {
            String resp = new SimplePost(tokenUrl, null, null, new HashMap<>(), objectMapper).execute();
            JsonNode node = objectMapper.readTree(resp);
            if (node.has("error")) {
                throw new AiException("文心鉴权失败：" + node.get("error_description").asText());
            }
            return node.get("access_token").asText();
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("获取文心 access_token 失败：" + e.getMessage());
        }
    }

    private String buildChatUrl(String baseUrl, String model, String token) {
        // 文心对话统一入口；model 决定具体路由，token 作为 query 参数
        String host = baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "https://qianfan.baidubce.com/v2";
        if (!host.endsWith("/")) host = host + "/";
        return host + "chat/completions?access_token=" + token;
    }

    private Map<String, Object> buildBody(ChatRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.model);
        List<Map<String, Object>> msgs = new ArrayList<>();
        for (ChatMessage m : request.messages) {
            if ("tool".equals(m.role)) continue; // 文心暂不支持 tool_result 回灌
            Map<String, Object> o = new HashMap<>();
            o.put("role", m.role);
            o.put("content", m.content);
            msgs.add(o);
        }
        body.put("messages", msgs);
        if (request.temperature != null) body.put("temperature", request.temperature);
        if (request.maxTokens != null) body.put("max_output_tokens", request.maxTokens);
        if (request.tools != null && !request.tools.isEmpty()) {
            List<Map<String, Object>> functions = new ArrayList<>();
            for (ToolSpec spec : request.tools) {
                Map<String, Object> f = new HashMap<>();
                f.put("name", spec.name);
                f.put("description", spec.description);
                f.put("parameters", spec.parameters);
                functions.add(f);
            }
            body.put("functions", functions);
        }
        return body;
    }

    private ChatResult parseResponse(String resp) {
        try {
            JsonNode root = objectMapper.readTree(resp);
            if (root.has("error_code")) {
                throw new AiException("文心错误：" + root.get("error_msg").asText());
            }
            JsonNode result = root.get("result");
            String content = result != null ? result.asText() : "";
            List<ToolCall> toolCalls = new ArrayList<>();
            JsonNode fn = root.get("function_call");
            if (fn != null && fn.has("name")) {
                ToolCall call = new ToolCall();
                call.id = "qf_0";
                call.name = fn.get("name").asText();
                call.arguments = fn.has("arguments") ? fn.get("arguments").toString() : "{}";
                toolCalls.add(call);
            }
            return new ChatResult(content, toolCalls);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("解析文心返回失败：" + e.getMessage());
        }
    }
}
