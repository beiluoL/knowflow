package com.knowflow.dto;

import lombok.Data;

/**
 * 重新生成 / 分支续写请求参数。
 * 兼容既有模型切换、知识库检索与学习教练上下文，不改变原流式协议。
 */
@Data
public class ChatRegenerateDTO {

    /** 可选模型标识，为空时沿用服务端默认模型。 */
    private String model;

    /** 是否检索知识库，默认开启。 */
    private Boolean useKnowledgeBase = true;

    /** 引用的历史消息 ID（可空）。 */
    private Long quoteMessageId;

    /** 对话模式：GENERAL / LEARNING_COACH。 */
    private String mode = "GENERAL";

    /** 学习教练上下文（仅 mode=LEARNING_COACH 时使用）。 */
    private String coachContext;

    /** 是否开启深度思考（仅对支持推理内容的模型生效）。 */
    private Boolean deepThinking = false;
}
