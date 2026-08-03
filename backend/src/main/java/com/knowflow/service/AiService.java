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

    /** 返回当前可切换的模型列表。 */
    List<String> getAvailableModels();

    boolean isConfigured();

    /**
     * 简答题/主观题 AI 评分：将题目、学生答案、参考答案发给大模型，返回打分（0-100）与评语。
     *
     * @param question  题目文本
     * @param userAnswer 用户作答
     * @param correctAnswer 参考答案
     * @return JSON 格式：{"score": 85, "feedback": "基本正确，缺少细节说明"}
     */
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
     * 流式对话完成回调函数式接口。
     */
    @FunctionalInterface
    interface StreamCompletionCallback {
        /**
         * @param content 成功时为完整回复文本；失败时为错误信息
         * @param success 是否成功
         */
        void onComplete(String content, boolean success);
    }

    /**
     * 模型可用性检测：发起一个轻量请求验证配置是否可用。
     * 返回 { ok: true/false, latencyMs: n, error: "..." }。
     */
    String healthCheck(Long userId, Long configId);
}
