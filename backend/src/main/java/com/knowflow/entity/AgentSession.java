package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_session")
/**
 * 编程 Agent 会话实体。
 * <p>
 * 一个会话对应一次连续的编程对话上下文，用户可在多个会话间切换。
 * 会话级配置包括使用的模型配置ID、项目目录名等。
 */
public class AgentSession extends BaseEntity {
    private Long userId;
    private String title;
    private Long configId;
    private String projectDir;
    private Integer messageCount;
    private String lastMessage;
    /** 上下文窗口上限（预估 token），超出后触发历史摘要压缩 */
    private Integer contextWindow;
    /** 会话模式：chat（纯对话）/ agent（允许工具调用） */
    private String agentMode;
}
