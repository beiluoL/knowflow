package com.knowflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.config.AiProviderRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容协议适配器：覆盖 OpenAI、DeepSeek、通义百炼、Ollama、vLLM、LocalAI 等
 * 所有符合 /v1/chat/completions 规范的接口。同时支持 tool_calls 工具调用与 SSE 流式。
 */
@Slf4j
public class OpenAiAdapter implements ModelAdapter {

    private final ObjectMapper objectMapper;

    public OpenAiAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProviderRegistry.Protocol protocol() {
        return AiProviderRegistry.Protocol.OPENAI;
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        Map<String, Object> body = buildBody(request, false);
        RestTemplate rt = buildRestTemplate(request.apiKey);
        String url = ensureChatCompletionsUrl(request.baseUrl);
        try {
            String resp = rt.postForObject(url, body, String.class);
            return parseChatCompletion(resp);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("模型调用失败：" + e.getMessage());
        }
    }

    @Override
    public void streamChat(ChatRequest request, Consumer<TokenDelta> onToken, Consumer<StreamDone> onDone) {
        Map<String, Object> body = buildBody(request, true);
        String url = ensureChatCompletionsUrl(request.baseUrl);
        StringBuilder full = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        Map<String, StringBuilder> argBuffers = new HashMap<>();

        SseClient.stream(url, request.apiKey, body, objectMapper, new SseClient.Handler() {
            @Override
            public void onData(JsonNode frame) {
                // SSE 帧结构：{"choices":[{"delta":{"content":"...","role":"assistant"},"finish_reason":null}]}
                JsonNode choices = frame.get("choices");
                if (choices == null || !choices.isArray() || choices.isEmpty()) {
                    // 兼容直接返回 content 的非标准协议
                    JsonNode direct = frame.get("content");
                    if (direct != null && !direct.isNull()) {
                        String text = direct.asText();
                        if (!text.isEmpty()) {
                            full.append(text);
                            onToken.accept(new TokenDelta(text));
                        }
                    }
                    return;
                }
                JsonNode choice0 = choices.get(0);
                JsonNode delta = choice0.get("delta");
                if (delta == null || delta.isNull()) {
                    // finish_reason 帧无 delta，跳过
                    return;
                }
                JsonNode content = delta.get("content");
                if (content != null && !content.isNull()) {
                    String text = content.asText();
                    if (!text.isEmpty()) {
                        full.append(text);
                        onToken.accept(new TokenDelta(text));
                    }
                }
                JsonNode toolNodes = delta.get("tool_calls");
                if (toolNodes != null && toolNodes.isArray()) {
                    for (JsonNode tc : toolNodes) {
                        String indexStr = tc.has("index") ? tc.get("index").asText() : "0";
                        JsonNode fn = tc.get("function");
                        String name = fn != null && fn.has("name") && !fn.get("name").isNull()
                                ? fn.get("name").asText() : null;
                        String argFrag = fn != null && fn.has("arguments") && !fn.get("arguments").isNull()
                                ? fn.get("arguments").asText() : null;
                        ToolCall call = ensureToolCall(toolCalls, argBuffers, indexStr, name);
                        if (argFrag != null) {
                            argBuffers.get(indexStr).append(argFrag);
                        }
                    }
                }
            }

            @Override
            public void onError(String message) {
                throw new AiException("模型流式响应异常：" + message);
            }
        });
        // 回填累积参数
        for (ToolCall call : toolCalls) {
            StringBuilder sb = argBuffers.get(call.id);
            if (sb != null) {
                call.arguments = sb.toString();
            }
        }
        onDone.accept(new StreamDone(full.toString(), toolCalls));
    }

    private ToolCall ensureToolCall(List<ToolCall> toolCalls, Map<String, StringBuilder> argBuffers, String indexStr, String name) {
        if (toolCalls.size() == 0) {
            ToolCall call = new ToolCall();
            call.id = indexStr;
            call.name = name;
            toolCalls.add(call);
            argBuffers.put(indexStr, new StringBuilder());
            return call;
        }
        return toolCalls.get(0);
    }

    /** 构造请求体（兼容 tools）。 */
    private Map<String, Object> buildBody(ChatRequest request, boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.model);
        body.put("messages", toOpenAiMessages(request.messages));
        body.put("stream", stream);
        if (request.temperature != null) body.put("temperature", request.temperature);
        if (request.maxTokens != null) body.put("max_tokens", request.maxTokens);
        if (request.topP != null) body.put("top_p", request.topP);
        if (request.tools != null && !request.tools.isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ToolSpec spec : request.tools) {
                Map<String, Object> t = new HashMap<>();
                t.put("type", "function");
                Map<String, Object> fn = new HashMap<>();
                fn.put("name", spec.name);
                fn.put("description", spec.description);
                fn.put("parameters", spec.parameters);
                t.put("function", fn);
                tools.add(t);
            }
            body.put("tools", tools);
        }
        return body;
    }

    private List<Map<String, Object>> toOpenAiMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatMessage m : messages) {
            Map<String, Object> o = new HashMap<>();
            o.put("role", m.role);
            o.put("content", m.content);
            out.add(o);
        }
        return out;
    }

    private ChatResult parseChatCompletion(String resp) {
        try {
            JsonNode root = objectMapper.readTree(resp);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new AiException("模型返回格式异常（无 choices）");
            }
            JsonNode message = choices.get(0).get("message");
            String content = message.has("content") && !message.get("content").isNull()
                    ? message.get("content").asText() : "";
            List<ToolCall> toolCalls = new ArrayList<>();
            JsonNode tcNodes = message.get("tool_calls");
            if (tcNodes != null && tcNodes.isArray()) {
                for (JsonNode tc : tcNodes) {
                    ToolCall call = new ToolCall();
                    call.id = tc.has("id") ? tc.get("id").asText() : null;
                    JsonNode fn = tc.get("function");
                    call.name = fn != null && fn.has("name") ? fn.get("name").asText() : null;
                    call.arguments = fn != null && fn.has("arguments") ? fn.get("arguments").asText() : "{}";
                    toolCalls.add(call);
                }
            }
            return new ChatResult(content, toolCalls);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("解析模型返回失败：" + e.getMessage());
        }
    }

    static RestTemplate buildRestTemplate(String apiKey) {
        RestTemplate rt = new RestTemplate();
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add((req, body, execution) -> {
            req.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            req.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            return execution.execute(req, body);
        });
        rt.setInterceptors(interceptors);
        return rt;
    }

    static String ensureChatCompletionsUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new AiException("缺少接口地址 baseUrl");
        }
        String u = baseUrl.trim();
        if (!u.endsWith("/")) u = u + "/";
        if (u.endsWith("/v1/")) return u + "chat/completions";
        if (u.endsWith("/v1")) return u + "/chat/completions";
        return u + "chat/completions";
    }
}
