package com.knowflow.agent.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.agent.ToolContext;
import com.knowflow.agent.tool.AgentTool;
import com.knowflow.agent.tool.ToolException;
import com.knowflow.agent.tool.ToolPermission;
import com.knowflow.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 内置工具：只读数据库查询。仅允许单条 SELECT 语句，禁止写入/多语句，
 * 防止工具越权修改数据。属于 DANGEROUS 等级，默认禁用，需用户显式授权。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbQueryTool implements AgentTool {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "db_query";
    }

    @Override
    public String description() {
        return "对系统数据库执行只读查询，返回结果行。仅支持单条 SELECT 语句，禁止写入或 DDL。";
    }

    @Override
    public JsonNode parameters() {
        com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode props = objectMapper.createObjectNode();
        props.putObject("sql").put("type", "string").put("description", "只读 SELECT 语句");
        root.put("type", "object").set("properties", props);
        root.putArray("required").add("sql");
        return root;
    }

    @Override
    public boolean enabledByDefault() {
        return false;
    }

    @Override
    public ToolPermission permission() {
        return ToolPermission.DANGEROUS;
    }

    @Override
    public ToolResult execute(JsonNode args, ToolContext ctx) throws ToolException {
        long start = System.currentTimeMillis();
        String sql = args.has("sql") ? args.get("sql").asText() : null;
        if (sql == null || sql.isBlank()) {
            return ToolResult.fail("缺少必填参数 sql", System.currentTimeMillis() - start);
        }
        String normalized = sql.trim().replaceAll("\\s+", " ");
        if (!normalized.toLowerCase().startsWith("select ")) {
            return ToolResult.fail("仅允许 SELECT 查询", System.currentTimeMillis() - start);
        }
        if (normalized.contains(";") && normalized.indexOf(';') != normalized.length() - 1) {
            return ToolResult.fail("仅支持单条语句，不允许多语句", System.currentTimeMillis() - start);
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(normalized);
            StringBuilder sb = new StringBuilder();
            sb.append("共 ").append(rows.size()).append(" 行\n");
            int limit = Math.min(rows.size(), 50);
            for (int i = 0; i < limit; i++) {
                sb.append(objectMapper.writeValueAsString(rows.get(i))).append("\n");
            }
            if (rows.size() > limit) sb.append("...（仅展示前 ").append(limit).append(" 行）\n");
            return ToolResult.ok(sb.toString(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new ToolException("数据库查询失败：" + e.getMessage(), e);
        }
    }
}
