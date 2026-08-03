package com.knowflow.agent;

import lombok.Builder;
import lombok.Data;

/**
 * 工具执行的运行时上下文：携带调用者身份与可用的工作区信息，
 * 供工具（如文件读写）定位资源、做权限判定。
 */
@Data
@Builder
public class ToolContext {
    private Long userId;
    private Long sessionId;
    /** 会话绑定的项目目录（来自 agent_session.projectDir），可为空表示无工作区 */
    private String workspaceDir;
    /** 工具调用来源消息 ID，用于串联调用链 */
    private Long messageId;
}
