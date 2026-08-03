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
    /** system / user / assistant */
    private String role;
    private String content;
    private String filePath;
    private Integer tokenCount;
    private Integer latencyMs;
    private Integer isError;
    private LocalDateTime createTime;
}
