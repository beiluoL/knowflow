package com.knowflow.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
/**
 * 编程 Agent 流式对话请求。
 * <p>
 * 前端传入完整对话历史（messages）+ 指定使用的模型配置（configId）+ 可选的文件上下文。
 * 后端将历史拼接为多轮对话上下文，调用模型流式接口，通过 SSE 推送 token。
 * <p>
 * 扩展字段：
 * <ul>
 *   <li>{@link #sessionId}：会话ID，传入后后端会自动持久化 user/assistant 消息并记录调用日志</li>
 *   <li>{@link #title}：新会话标题，仅在 sessionId 为 null（创建新会话）时使用</li>
 * </ul>
 */
public class CodeAgentChatDTO {

    /** 对话历史，按时间顺序排列；最后一条应为 role=user 的当前问题。 */
    @NotEmpty(message = "对话消息不能为空")
    private List<ChatMessage> messages;

    /** 指定使用的模型配置 ID；为 null 时回退到用户激活配置或全局配置。 */
    private Long configId;

    /** 可选：当前打开的文件内容，作为附加上下文注入 system 提示。 */
    private String fileContext;

    /** 可选：当前文件路径名（用于在 system 提示中告知模型）。 */
    private String filePath;

    /** 可选：会话ID。传入则将本次对话追加到该会话；为 null 则由后端自动创建新会话。 */
    private Long sessionId;

    /** 可选：新会话标题。仅在 sessionId 为 null 时生效；为空时取首条 user 消息前 30 字。 */
    private String title;

    /** 可选：采样温度（0~2），为 null 时使用默认 0.7。 */
    private Double temperature;

    /** 可选：最大输出 token 数，为 null 时使用全局配置。 */
    private Integer maxTokens;

    /** 可选：核采样概率阈值（0~1），为 null 时不传该字段。 */
    private Double topP;

    @Data
    public static class ChatMessage {
        @NotNull(message = "role 不能为空")
        private String role;
        @NotNull(message = "content 不能为空")
        private String content;
    }
}
