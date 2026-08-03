package com.knowflow.ai;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 大模型对接统一适配层接口。
 * <p>
 * 屏蔽 OpenAI / Anthropic / 文心 等各厂商接口差异，向上层（{@code AiService}）提供一致的
 * 单轮/多轮对话与流式调用能力。各协议实现见 {@code OpenAiAdapter} / {@code AnthropicAdapter} /
 * {@code QianfanAdapter}，由 {@link ModelAdapterFactory} 按 provider 的 protocol 选择。
 */
public interface ModelAdapter {

    /** 适配的协议类型，与 {@code AiProviderRegistry.Protocol} 对应。 */
    com.knowflow.config.AiProviderRegistry.Protocol protocol();

    /** 非流式对话：返回完整结果。 */
    ChatResult chat(ChatRequest request);

    /**
     * 流式对话：逐 token 回调 onToken，结束时回调 onDone（content 为完整文本）。
     * 实现内部需处理 SSE 解析、异常与链接关闭。
     */
    void streamChat(ChatRequest request, Consumer<TokenDelta> onToken, Consumer<StreamDone> onDone);

    /** 对话消息：role 取值 system/user/assistant/tool。 */
    class ChatMessage {
        public String role;
        public String content;
        /** tool 角色消息携带的调用ID，用于回灌工具结果 */
        public String toolCallId;
        /** assistant 消息携带的工具调用意图（由适配器解析并填充） */
        public List<ToolCall> toolCalls;

        public ChatMessage() {}

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    /** 模型请求的工具声明（JSON Schema 描述）。 */
    class ToolSpec {
        public String name;
        public String description;
        /** 参数 JSON Schema，Jackson 序列化为 object 节点 */
        public Map<String, Object> parameters;
    }

    /** 工具调用意图：模型要求执行某工具并给出参数。 */
    class ToolCall {
        public String id;
        public String name;
        public String arguments; // 原始 JSON 字符串
    }

    /** 统一对话请求。 */
    class ChatRequest {
        public List<ChatMessage> messages;
        public List<ToolSpec> tools;
        public String model;
        public String apiKey;
        /** 文心 qianfan 需要 secret 换取 access_token */
        public String apiSecret;
        public String baseUrl;
        public Double temperature;
        public Integer maxTokens;
        public Double topP;
    }

    /** 流式 token 增量。 */
    class TokenDelta {
        public String delta;
        /** 若为工具调用片段，填充此字段（适配器负责累积解析） */
        public ToolCall toolCall;

        public TokenDelta(String delta) {
            this.delta = delta;
        }
    }

    /** 流式结束回调：content 为完整文本，toolCalls 为解析出的工具调用意图。 */
    class StreamDone {
        public String content;
        public List<ToolCall> toolCalls;

        public StreamDone(String content, List<ToolCall> toolCalls) {
            this.content = content;
            this.toolCalls = toolCalls;
        }
    }

    /** 非流式结果。 */
    class ChatResult {
        public String content;
        public List<ToolCall> toolCalls;

        public ChatResult(String content, List<ToolCall> toolCalls) {
            this.content = content;
            this.toolCalls = toolCalls;
        }
    }
}
