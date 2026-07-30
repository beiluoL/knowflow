package com.knowflow.service;

import com.knowflow.entity.DocDocument;

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
}
