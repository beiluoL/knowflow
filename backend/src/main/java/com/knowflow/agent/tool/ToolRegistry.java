package com.knowflow.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.agent.ToolContext;
import com.knowflow.entity.AgentToolCall;
import com.knowflow.entity.AgentToolConfig;
import com.knowflow.mapper.AgentToolCallMapper;
import com.knowflow.mapper.AgentToolConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册与编排中心：
 * <ul>
 *   <li>自动收集所有 {@link AgentTool} Spring Bean；</li>
 *   <li>依据用户配置（agent_tool_config）判定启用/授权状态；</li>
 *   <li>执行前做权限校验与参数合法性检查；</li>
 *   <li>记录每次调用的入参/结果/状态到 agent_tool_call（调用链可视化数据源）。</li>
 * </ul>
 * <p>
 * 注：WRITE 类工具需用户显式授权（enabled=1 且 allow_write=1）才执行；
 * DANGEROUS 类默认禁用，需管理员/用户显式开启。二次交互确认（tool-confirm 事件）由前端层处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final List<AgentTool> tools;
    private final AgentToolConfigMapper toolConfigMapper;
    private final AgentToolCallMapper toolCallMapper;
    private final ObjectMapper objectMapper;

    private Map<String, AgentTool> index() {
        Map<String, AgentTool> map = new LinkedHashMap<>();
        for (AgentTool t : tools) {
            map.put(t.name(), t);
        }
        return map;
    }

    /** 列出所有已注册工具（含名称/描述/权限）。 */
    public List<AgentTool> allTools() {
        return new ArrayList<>(tools);
    }

    /** 返回某用户视角的工具清单（含启用状态）。 */
    public List<ToolMeta> listToolsForUser(Long userId) {
        Map<String, AgentTool> all = index();
        List<ToolMeta> result = new ArrayList<>();
        for (AgentTool t : all.values()) {
            boolean enabled = isEnabled(t, userId);
            result.add(new ToolMeta(t.name(), t.description(), t.permission().name(), enabled, t.parameters()));
        }
        return result;
    }

    /** 判断某工具对用户是否可用（启用且授权）。 */
    public boolean isEnabled(AgentTool tool, Long userId) {
        AgentToolConfig cfg = findConfig(tool.name(), userId);
        boolean enabled = cfg != null && cfg.getEnabled() != null && cfg.getEnabled() == 1;
        if (!enabled && cfg == null) {
            // 无配置时回退到默认启用策略
            enabled = tool.enabledByDefault();
        }
        if (!enabled) return false;
        // WRITE 需 allowWrite 授权
        if (tool.permission() == ToolPermission.WRITE) {
            return cfg != null && cfg.getAllowWrite() != null && cfg.getAllowWrite() == 1;
        }
        // DANGEROUS 需显式 allowWrite（视作高危开启）
        if (tool.permission() == ToolPermission.DANGEROUS) {
            return cfg != null && cfg.getAllowWrite() != null && cfg.getAllowWrite() == 1;
        }
        return true;
    }

    /** 执行工具：校验 → 调用 → 落库，返回结构化结果。 */
    public ToolResult invoke(String toolName, JsonNode args, ToolContext ctx) {
        AgentTool tool = index().get(toolName);
        if (tool == null) {
            return ToolResult.fail("未知工具：" + toolName, 0);
        }
        long start = System.currentTimeMillis();
        AgentToolCall record = new AgentToolCall();
        record.setSessionId(ctx.getSessionId());
        record.setMessageId(ctx.getMessageId());
        record.setToolName(toolName);
        record.setPermission(tool.permission().name());
        try {
            record.setArgsJson(args != null ? args.toString() : "{}");
        } catch (Exception ignore) {
            record.setArgsJson("{}");
        }

        try {
            if (!isEnabled(tool, ctx.getUserId())) {
                String msg = switch (tool.permission()) {
                    case WRITE -> "工具 " + toolName + " 需要写授权，请先在工具面板启用并授权写操作";
                    case DANGEROUS -> "工具 " + toolName + " 为高危工具，当前未授权执行";
                    default -> "工具 " + toolName + " 未启用";
                };
                record.setStatus("cancelled");
                record.setResultJson("{\"error\":\"" + msg + "\"}");
                record.setLatencyMs(System.currentTimeMillis() - start);
                toolCallMapper.insert(record);
                return ToolResult.fail(msg, record.getLatencyMs());
            }
            ToolResult result = tool.execute(args, ctx);
            record.setStatus(result.isSuccess() ? "success" : "failed");
            record.setResultJson(result.isSuccess() ? result.getOutput() : result.getError());
            record.setLatencyMs(System.currentTimeMillis() - start);
            toolCallMapper.insert(record);
            return result;
        } catch (ToolException e) {
            record.setStatus("failed");
            record.setResultJson("{\"error\":\"" + e.getMessage() + "\"}");
            record.setLatencyMs(System.currentTimeMillis() - start);
            toolCallMapper.insert(record);
            return ToolResult.fail(e.getMessage(), record.getLatencyMs());
        } catch (Exception e) {
            log.error("工具 {} 执行异常: {}", toolName, e.getMessage(), e);
            record.setStatus("failed");
            record.setResultJson("{\"error\":\"内部错误：" + e.getMessage() + "\"}");
            record.setLatencyMs(System.currentTimeMillis() - start);
            toolCallMapper.insert(record);
            return ToolResult.fail("工具执行异常：" + e.getMessage(), record.getLatencyMs());
        }
    }

    /** 设置工具启用/授权状态（工具管理面板调用）。 */
    public void setConfig(Long userId, String toolName, boolean enabled, boolean allowWrite) {
        AgentToolConfig cfg = findConfig(toolName, userId);
        if (cfg == null) {
            cfg = new AgentToolConfig();
            cfg.setUserId(userId);
            cfg.setToolName(toolName);
        }
        cfg.setEnabled(enabled ? 1 : 0);
        cfg.setAllowWrite(allowWrite ? 1 : 0);
        if (cfg.getId() == null) {
            toolConfigMapper.insert(cfg);
        } else {
            toolConfigMapper.updateById(cfg);
        }
    }

    private AgentToolConfig findConfig(String toolName, Long userId) {
        return toolConfigMapper.selectOne(new LambdaQueryWrapper<AgentToolConfig>()
                .eq(AgentToolConfig::getUserId, userId)
                .eq(AgentToolConfig::getToolName, toolName)
                .eq(AgentToolConfig::getDeleted, 0));
    }

    /** 工具元信息（供前端工具面板展示）。 */
    public record ToolMeta(String name, String description, String permission, boolean enabled, JsonNode parameters) {}
}
