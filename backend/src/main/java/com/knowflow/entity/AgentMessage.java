package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_message")
/**
 * 编程 Agent 消息实体，记录会话中的每一条消息（user/assistant/system）。
 * <p>
 * assistant 消息额外记录响应耗时 latencyMs 与是否为错误消息 is_error，
 * 用于会话回放与问题排查。
 */
public class AgentMessage extends BaseEntity {
    private Long sessionId;
    private Long userId;
    /** system / user / assistant / tool */
    private String role;
    private String content;
    /** user 消息附带的文件路径，可空 */
    private String filePath;
    /** normal / tool_call / tool_result / summary */
    private String messageType;
    /** 工具调用ID（tool 角色回灌时关联） */
    private String toolCallId;
    /** 触发的工具名（可视化用） */
    private String toolName;
    /** 父消息ID（多轮/工具循环溯源） */
    private Long parentId;
    private Integer tokenCount;
    /** assistant 消息的响应耗时（毫秒），可空 */
    private Integer latencyMs;
    /** 0 否 / 1 是 */
    private Integer isError;
}
