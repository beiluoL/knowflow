package com.knowflow.service;

import com.knowflow.entity.DocDocument;

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
}
