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
        return "查询公开题目分类字典 question_topic，仅允许 id/code/name/parent_code/level/sort_order 列；不支持别名、函数、条件、JOIN或子查询。最多50行。";
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
        String sql = args != null && args.has("sql") ? args.get("sql").asText() : null;
        if (sql == null || sql.isBlank()) {
            return ToolResult.fail("缺少必填参数 sql", System.currentTimeMillis() - start);
        }
        String normalized;
        try {
            normalized = approvedQuery(sql);
        } catch (Exception rejected) {
            log.warn("数据库工具查询被策略拒绝，userId={}", ctx == null ? null : ctx.getUserId());
            return ToolResult.fail("查询不符合公开字典查询策略", System.currentTimeMillis() - start);
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.query(connection -> {
                java.sql.PreparedStatement statement = connection.prepareStatement(normalized);
                statement.setMaxRows(50);
                statement.setQueryTimeout(3);
                return statement;
            }, new org.springframework.jdbc.core.ColumnMapRowMapper());
            StringBuilder sb = new StringBuilder();
            sb.append("共 ").append(rows.size()).append(" 行\n");
            int limit = Math.min(rows.size(), 50);
            for (int i = 0; i < limit; i++) {
                sb.append(objectMapper.writeValueAsString(rows.get(i))).append("\n");
            }
            if (rows.size() > limit) sb.append("...（仅展示前 ").append(limit).append(" 行）\n");
            return ToolResult.ok(sb.toString(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("数据库工具查询执行失败，userId={}", ctx == null ? null : ctx.getUserId());
            return ToolResult.fail("数据库查询失败，请稍后重试", System.currentTimeMillis() - start);
        }
    }
    /** 只开放无用户数据的题目分类字典；解析后重新构建SQL，不执行原始输入。 */
    private String approvedQuery(String sql) throws Exception {
        if (sql.length() > 2000) throw new IllegalArgumentException();
        var parsed = net.sf.jsqlparser.parser.CCJSqlParserUtil.parseStatements(sql);
        if (parsed.getStatements().size() != 1
                || !(parsed.getStatements().get(0) instanceof net.sf.jsqlparser.statement.select.Select select)
                || select.getWithItemsList() != null
                || !(select.getSelectBody() instanceof net.sf.jsqlparser.statement.select.PlainSelect plain)
                || !(plain.getFromItem() instanceof net.sf.jsqlparser.schema.Table table)
                || !"question_topic".equals(table.getName()) || table.getSchemaName() != null
                || table.getAlias() != null) throw new IllegalArgumentException();
        java.util.Set<String> allowed = java.util.Set.of("id", "code", "name", "parent_code", "level", "sort_order");
        java.util.List<String> columns = new java.util.ArrayList<>();
        for (var item : plain.getSelectItems()) {
            if (!(item instanceof net.sf.jsqlparser.statement.select.SelectExpressionItem expression)
                    || expression.getAlias() != null
                    || !(expression.getExpression() instanceof net.sf.jsqlparser.schema.Column column)
                    || column.getTable() != null && column.getTable().getName() != null
                    || !allowed.contains(column.getColumnName())) throw new IllegalArgumentException();
            columns.add(column.getColumnName());
        }
        // 比较解析器规范化输出，拒绝任何未被明确授权的附加语法/表达式。
        String projection = "SELECT " + String.join(", ", columns) + " FROM question_topic";
        if (!plain.toString().equals(projection)) throw new IllegalArgumentException();
        return projection + " WHERE deleted = 0 AND status = 1 LIMIT 50";
    }
}
