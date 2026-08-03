package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具调用链明细：每次工具调用记录入参、结果、状态与耗时，
 * 用于会话级调用链路可视化与排查。逻辑外键 session_id / message_id。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_tool_call")
public class AgentToolCall extends BaseEntity {
    private Long sessionId;
    private Long messageId;
    private String toolName;
    /** 权限等级 SAFE/WRITE/DANGEROUS */
    private String permission;
    /** 入参 JSON */
    private String argsJson;
    /** 出参/错误 JSON */
    private String resultJson;
    /** success/failed/cancelled */
    private String status;
    /** 耗时毫秒 */
    private Long latencyMs;
}
