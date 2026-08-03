package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 编程 Agent 会话列表项 VO。
 * <p>
 * 用于会话管理侧边栏展示，包含会话标题、消息数、最后消息摘要、时间等。
 */
@Data
public class AgentSessionVO {
    private Long id;
    private Long userId;
    private String title;
    private Long configId;
    /** 当前会话使用的模型显示名（由 Controller 回填） */
    private String configLabel;
    private String projectDir;
    private Integer messageCount;
    private String lastMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
