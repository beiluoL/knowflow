package com.knowflow.agent.tool;

import lombok.Builder;
import lombok.Data;

/**
 * 工具执行结果，统一结构以便回灌模型与前端可视化。
 */
@Data
@Builder
public class ToolResult {
    /** 是否成功 */
    private boolean success;
    /** 给模型看的结构化文本结果（已截断到安全长度） */
    private String output;
    /** 错误描述（success=false 时填充） */
    private String error;
    /** 执行耗时（毫秒） */
    private long latencyMs;
    /** 输出是否被截断 */
    private boolean truncated;

    public static ToolResult ok(String output, long latencyMs) {
        boolean truncated = output != null && output.length() > 4000;
        String safe = truncated ? output.substring(0, 4000) + "\n...[输出已截断]" : output;
        return ToolResult.builder().success(true).output(safe).latencyMs(latencyMs).truncated(truncated).build();
    }

    public static ToolResult fail(String error, long latencyMs) {
        return ToolResult.builder().success(false).error(error).latencyMs(latencyMs).build();
    }
}
