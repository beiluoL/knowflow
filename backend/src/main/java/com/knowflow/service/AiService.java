package com.knowflow.service;

import com.knowflow.entity.DocDocument;

import java.util.List;

/** AI 问答业务服务接口。 */
public interface AiService {

    /** 基于检索到的 contextDocs 作为上下文，调用大模型生成回复。 */
    String chat(String userMessage, List<DocDocument> contextDocs);

    boolean isConfigured();
}
