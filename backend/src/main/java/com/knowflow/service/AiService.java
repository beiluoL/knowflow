package com.knowflow.service;

import com.knowflow.entity.DocDocument;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;

/** AI 问答业务服务接口。 */
public interface AiService {

    /** 基于检索到的 contextDocs 作为上下文，调用大模型生成回复。 */
    String chat(String userMessage, List<DocDocument> contextDocs);

    /** 带模型覆盖的对话接口，model 为 null 时回退到配置默认模型。 */
    String chat(String userMessage, List<DocDocument> contextDocs, String model);

    /** 直接补全（无 RAG 上下文），用于摘要、出题等结构化生成任务。 */
    String complete(String systemPrompt, String userPrompt);

    /** 带模型覆盖的结构化补全接口。 */
    String complete(String systemPrompt, String userPrompt, String model);

    /** 带用户ID的对话接口，优先使用用户自带 Key，否则回退到全局配置。 */
    String chat(String userMessage, List<DocDocument> contextDocs, String model, Long userId);

    /** 带用户ID的结构化补全接口，优先使用用户自带 Key，否则回退到全局配置。 */
    String complete(String systemPrompt, String userPrompt, String model, Long userId);

    /** 有明确JSON契约的调用；实现可按已知供应商能力控制输出，默认保留原适配行为。 */
    default String completeStructured(String systemPrompt,String userPrompt,String model,Long userId){
        return complete(systemPrompt,userPrompt,model,userId);
    }

    /** 返回当前可切换的模型列表。 */
    List<String> getAvailableModels();

    boolean isConfigured();

    /**
     * 指定用户的生效配置是否可用（用户自带 Key 优先，否则看系统级 Key；同时尊重系统 AI 总开关）。
     * Phase 3H：所有需要判断“AI 能不能用”的业务层都应调用本方法，而不是各自猜配置。
     */
    default boolean isConfigured(Long userId) {
        return isConfigured();
    }

    /**
     * 指定用户当前生效模型的熔断/统计作用域（provider:model），供能力状态查询使用。
     */
    default String currentScope(Long userId) {
        return "default";
    }

    /**
     * @deprecated Phase 3H 起废弃：本方法历史上在 AI 失败时返回伪造的 85/40 分，违反“AI 失败不造假分”
     * 原则，且生产链路（考试 AI 评卷）使用 {@code ExamAiService.gradeSubjective}，本方法无生产调用方。
     * 保留仅为二进制兼容，任何新代码不得使用。
     */
    @Deprecated
    String gradeEssay(String question, String userAnswer, String correctAnswer);

    /**
     * 带图片的多模态对话：发送文本+图片到视觉模型，返回生成回复。
     * images 为 base64 编码的图片数据列表（不含 data:image/...;base64, 前缀）。
     *
     * @param text     用户文本输入
     * @param images   base64 图片列表
     * @param contextDocs 检索到的文档上下文
     * @param model    模型名称（应使用视觉模型，如 gpt-4o）
     * @param userId   用户ID
     * @return AI 回复文本
     */
    String chatWithImages(String text, List<String> images, List<DocDocument> contextDocs, String model, Long userId);

    /**
     * 带图片的对话（无用户 Key 的简化版本）。
     */
    default String chatWithImages(String text, List<String> images, List<DocDocument> contextDocs, String model) {
        return chatWithImages(text, images, contextDocs, model, null);
    }

    /**
     * 流式对话（编程 Agent 专用）：通过 SSE 逐 token 推送模型输出。
     * <p>
     * 推送事件：
     * <ul>
     *   <li>{@code delta}：{ content: "token" }</li>
     *   <li>{@code done}：{ content: "完整文本" }</li>
     *   <li>{@code error}：{ error: "错误信息" }</li>
     * </ul>
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户输入
     * @param userId       用户 ID（用于解析其个人配置）
     * @param configId     指定使用哪条用户配置；为 null 时回退到 active 配置或全局配置
     * @param emitter      SSE 推送器
     */
    void streamChat(String systemPrompt, String userPrompt, Long userId, Long configId, SseEmitter emitter);

    /**
     * 流式对话（带完成回调）：在流结束时回调通知调用方完整内容与成功/失败状态，
     * 便于 Controller 持久化 assistant 消息与调用日志。
     *
     * @param callback 流结束时的回调；success 为 true 时 content 为完整回复，为 false 时 content 为错误信息
     */
    void streamChat(String systemPrompt, String userPrompt, Long userId, Long configId,
                    SseEmitter emitter, StreamCompletionCallback callback);

    /**
     * 带运行时参数的流式对话：允许前端按模型自定义 temperature / maxTokens / topP。
     * 任意参数为 null 时回退到默认值。
     *
     * @param temperature 采样温度（0~2），null 则用默认 0.7
     * @param maxTokens   最大输出 token 数，null 则用全局配置
     * @param topP        核采样阈值（0~1），null 则不传
     */
    void streamChat(String systemPrompt, String userPrompt, Long userId, Long configId,
                    SseEmitter emitter, StreamCompletionCallback callback,
                    Double temperature, Integer maxTokens, Double topP);

    /**
     * 带深度思考开关的流式对话：deepThinking=true 时按模型能力读取可展示推理内容
     * （DeepSeek reasoning_content / OpenAI reasoning summary），并与最终回答分开推送。
     * 默认实现忽略开关，保证既有调用方兼容。
     */
    default void streamChat(String systemPrompt, String userPrompt, Long userId, Long configId,
                            SseEmitter emitter, StreamCompletionCallback callback,
                            Double temperature, Integer maxTokens, Double topP, boolean deepThinking) {
        streamChat(systemPrompt, userPrompt, userId, configId, emitter, callback,
                temperature, maxTokens, topP, deepThinking, null);
    }

    /**
     * 指定模型的深度思考流式对话：model 非空时覆盖生效配置中的模型，
     * 保证前端模型下拉选择与深度思考能力判定使用同一个真实模型。
     * 默认实现忽略 model 与 deepThinking，回退到基础三参版本；
     * 需要真实推理内容的实现必须覆盖本方法（否则不会产生推理事件）。
     */
    default void streamChat(String systemPrompt, String userPrompt, Long userId, Long configId,
                            SseEmitter emitter, StreamCompletionCallback callback,
                            Double temperature, Integer maxTokens, Double topP, boolean deepThinking,
                            String model) {
        streamChat(systemPrompt, userPrompt, userId, configId, emitter, callback,
                temperature, maxTokens, topP);
    }

    /**
     * 聊天模型能力（深度思考）列表，供前端开关判断；不包含任何密钥信息。
     */
    default com.knowflow.vo.ChatModelCapabilityVO getModelCapabilities(Long userId) {
        throw new UnsupportedOperationException("模型能力查询未实现");
    }

    /**
     * 流式对话完成回调函数式接口。
     */
    @FunctionalInterface
    interface StreamCompletionCallback {
        /**
         * @param content 成功时为完整回复文本；失败时为错误信息
         * @param success 是否成功
         */
        void onComplete(String content, boolean success);

        /**
         * 携带真实推理内容的重载：reasoning 为 Provider 返回的可展示推理文本或摘要，
         * 无推理内容时为 null。默认回退到两参数版本，兼容既有 lambda 调用方。
         */
        default void onComplete(String content, String reasoning, boolean success) {
            onComplete(content, success);
        }

        /**
         * 完整重载：额外携带推理类型（provider_text / summary）与真实耗时，供持久化使用。
         */
        default void onComplete(String content, String reasoning, String reasoningKind,
                                Long reasoningDurationMs, boolean success) {
            onComplete(content, reasoning, success);
        }
    }

    /**
     * 模型可用性检测：发起一个轻量请求验证配置是否可用。
     * 返回 { ok: true/false, latencyMs: n, error: "..." }。
     */
    String healthCheck(Long userId, Long configId);

    /**
     * 编程 Agent 多轮 + 工具调用通道：返回模型输出，可能携带 toolCalls 供上层 ReAct 编排。
     * 供 AgentRuntimeService 在工具循环中使用，屏蔽各 Provider 协议差异。
     *
     * @param messages  完整多轮对话（含 system/user/assistant/tool 角色）
     * @param tools     当前可用的工具声明（JSON Schema）
     * @param userId    用户 ID
     * @param configId  指定模型配置
     * @return 模型结果（content + toolCalls）
     */
    com.knowflow.ai.ModelAdapter.ChatResult chatMulti(
            List<com.knowflow.ai.ModelAdapter.ChatMessage> messages,
            List<com.knowflow.ai.ModelAdapter.ToolSpec> tools,
            Long userId, Long configId);

    /**
     * 判断指定配置对应的模型是否支持 tools（Function Calling）。
     * 不支持时（如 Ollama 上的 deepseek-coder:6.7b），Agent 应跳过 ReAct 工具循环，退化为普通对话，
     * 避免向模型下发 tools 参数触发 400。
     */
    boolean supportsTools(Long userId, Long configId);

    /**
     * 编程 Agent 流式多轮通道：逐 token 回调 onToken，结束时回调 onDone（含 toolCalls）。
     */
    void streamMulti(
            List<com.knowflow.ai.ModelAdapter.ChatMessage> messages,
            List<com.knowflow.ai.ModelAdapter.ToolSpec> tools,
            Long userId, Long configId,
            java.util.function.Consumer<com.knowflow.ai.ModelAdapter.TokenDelta> onToken,
            java.util.function.Consumer<com.knowflow.ai.ModelAdapter.StreamDone> onDone,
            Double temperature, Integer maxTokens, Double topP);
}
