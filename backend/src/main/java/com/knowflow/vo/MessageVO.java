package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话消息视图对象，封装单条聊天的角色、内容与文档引用。
 */
@Data
public class MessageVO {

    private Long id;

    private Long conversationId;

    /** 消息角色，如 user、assistant */
    private String role;

    private String content;

    /** 引用的文档信息，通常为 JSON 字符串 */
    private String docReferences;

    /** 可展示的推理内容（仅 Provider 真实返回时存在）。 */
    private String reasoningContent;

    /** 推理内容类型：provider_text（模型推理内容）/ summary（思考摘要）。 */
    private String reasoningKind;

    /** 真实思考耗时（毫秒）。 */
    private Integer reasoningDurationMs;

    /** RAG / 执行轨迹 JSON（历史会话可还原时间线）。 */
    private String traceJson;

    private LocalDateTime createTime;
}
