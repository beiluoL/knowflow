package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_message")
/** 对话消息实体，记录会话中单条发言及其角色与文档引用。 */
public class ChatMessage extends BaseEntity {

    private Long conversationId;

    private Long userId;

    /** 消息角色，如 user / assistant。 */
    private String role;

    private String content;

    /** 引用的文档信息（JSON 或结构化字符串）。 */
    private String docReferences;

    private Integer tokenCount;

    /** F1：是否被截断（用户点击「停止」中断流式生成时为 1）。 */
    private Integer truncated;

    /** 可展示的推理内容（Provider 真实返回；摘要类型会在 reasoningKind 中标注）。 */
    private String reasoningContent;

    /** 推理内容类型：provider_text / summary；无推理内容时为空。 */
    private String reasoningKind;

    /** 真实思考耗时（毫秒）。 */
    private Integer reasoningDurationMs;

    /** RAG / 执行轨迹 JSON（步骤、计数、耗时，已脱敏）。 */
    private String traceJson;
}
