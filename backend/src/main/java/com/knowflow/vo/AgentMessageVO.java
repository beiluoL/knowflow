package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 编程 Agent 消息 VO，用于会话历史回放。
 */
@Data
public class AgentMessageVO {
    private Long id;
    private Long sessionId;
    /** system / user / assistant / tool */
    private String role;
    private String content;
    private String filePath;
    /** normal / tool_call / tool_result / summary，前端据此渲染不同气泡 */
    private String messageType;
    /** 工具调用 ID，用于把 tool_call 与 tool_result 配对 */
    private String toolCallId;
    /** 触发的工具名 */
    private String toolName;
    private Integer tokenCount;
    private Integer latencyMs;
    private Integer isError;
    private LocalDateTime createTime;
}
