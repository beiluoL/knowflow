package com.knowflow.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowflow.agent.ToolContext;

/**
 * 编程 Agent 工具抽象：任何可被大模型调用执行的能力都应实现本接口。
 * 内置工具（代码执行/文件读写/数据库查询）以 Spring Bean 形式注册到 {@link ToolRegistry}。
 */
public interface AgentTool {

    /** 工具唯一名，如 code_run / fs_read / fs_write / db_query。 */
    String name();

    /** 给大模型看的工具说明。 */
    String description();

    /** 参数 JSON Schema（object 结构，properties/required 描述）。 */
    JsonNode parameters();

    /** 默认是否启用。 */
    boolean enabledByDefault();

    /** 权限等级：决定是否需要用户二次确认。 */
    ToolPermission permission();

    /**
     * 执行工具。
     * @param args      模型解析出的参数（JSON 对象）
     * @param ctx       运行时上下文（用户/会话/工作区）
     * @return 结构化结果，供回灌模型或前端可视化
     */
    ToolResult execute(JsonNode args, ToolContext ctx) throws ToolException;
}
