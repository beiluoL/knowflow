package com.knowflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.agent.tool.ToolRegistry;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.entity.AgentSession;
import com.knowflow.entity.AgentToolCall;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.AgentSessionMapper;
import com.knowflow.mapper.AgentToolCallMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 编程 Agent 工具管理接口：
 * 提供工具列表查看、详情、启用/禁用与授权配置，以及会话级工具调用链的查询。
 */
@RestController
@RequestMapping("/api/agent/tools")
@RequiredArgsConstructor
public class AgentToolController {

    private final ToolRegistry toolRegistry;
    private final AgentToolCallMapper toolCallMapper;
    private final AgentSessionMapper sessionMapper;

    @Operation(summary = "工具列表（含当前用户启用状态）")
    @GetMapping
    public Result<List<ToolMetaDTO>> listTools() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<ToolRegistry.ToolMeta> metas = toolRegistry.listToolsForUser(userId);
        List<ToolMetaDTO> dtos = metas.stream().map(m -> {
            ToolMetaDTO dto = new ToolMetaDTO();
            dto.setName(m.name());
            dto.setDescription(m.description());
            dto.setPermission(m.permission());
            dto.setEnabled(m.enabled());
            dto.setParameters(m.parameters());
            return dto;
        }).toList();
        return Result.success(dtos);
    }

    @Operation(summary = "工具详情")
    @GetMapping("/{name}")
    public Result<ToolMetaDTO> getTool(@PathVariable String name) {
        Long userId = SecurityUtils.getCurrentUserId();
        return toolRegistry.allTools().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .map(t -> {
                    ToolMetaDTO dto = new ToolMetaDTO();
                    dto.setName(t.name());
                    dto.setDescription(t.description());
                    dto.setPermission(t.permission().name());
                    dto.setEnabled(toolRegistry.isEnabled(t, userId));
                    dto.setParameters(t.parameters());
                    return Result.success(dto);
                })
                .orElseThrow(() -> new BusinessException("工具不存在：" + name));
    }

    @Operation(summary = "启用/禁用工具并配置写授权")
    @PutMapping("/{name}")
    public Result<Void> setTool(@PathVariable String name, @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean exists = toolRegistry.allTools().stream().anyMatch(t -> t.name().equals(name));
        if (!exists) {
            throw new BusinessException("工具不存在：" + name);
        }
        boolean enabled = body.get("enabled") != null && Boolean.parseBoolean(body.get("enabled").toString());
        boolean allowWrite = body.get("allowWrite") != null && Boolean.parseBoolean(body.get("allowWrite").toString());
        toolRegistry.setConfig(userId, name, enabled, allowWrite);
        return Result.success(null);
    }

    @Operation(summary = "会话级工具调用链（可视化用）")
    @GetMapping("/sessions/{id}/call-chain")
    public Result<List<AgentToolCall>> callChain(@PathVariable Long id) {
        requireOwnedSession(id);
        List<AgentToolCall> calls = toolCallMapper.selectList(new LambdaQueryWrapper<AgentToolCall>()
                .eq(AgentToolCall::getSessionId, id)
                .orderByAsc(AgentToolCall::getCreateTime));
        return Result.success(calls);
    }

    /**
     * 工具调用统计（P3）：按工具名聚合调用次数、成功率与平均耗时，供前端面板展示。
     */
    @Operation(summary = "会话工具调用统计")
    @GetMapping("/sessions/{id}/call-stats")
    public Result<List<Map<String, Object>>> callStats(@PathVariable Long id) {
        requireOwnedSession(id);
        List<AgentToolCall> calls = toolCallMapper.selectList(new LambdaQueryWrapper<AgentToolCall>()
                .eq(AgentToolCall::getSessionId, id));

        Map<String, List<AgentToolCall>> grouped = calls.stream()
                .collect(Collectors.groupingBy(AgentToolCall::getToolName, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> stats = new ArrayList<>(grouped.size());
        grouped.forEach((tool, list) -> {
            long okCount = list.stream().filter(c -> "success".equals(c.getStatus())).count();
            double avgLatency = list.stream()
                    .map(AgentToolCall::getLatencyMs)
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0d);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tool", tool);
            item.put("total", list.size());
            item.put("success", okCount);
            item.put("failed", list.size() - okCount);
            item.put("avgLatencyMs", Math.round(avgLatency));
            stats.add(item);
        });
        return Result.success(stats);
    }

    /** 校验会话归属，失败抛出统一业务异常。 */
    private void requireOwnedSession(Long sessionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        AgentSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在或无权访问");
        }
    }

    /** 工具元信息 DTO（含参数 JSON Schema）。 */
    public static class ToolMetaDTO {
        private String name;
        private String description;
        private String permission;
        private boolean enabled;
        private com.fasterxml.jackson.databind.JsonNode parameters;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getPermission() { return permission; }
        public void setPermission(String permission) { this.permission = permission; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public com.fasterxml.jackson.databind.JsonNode getParameters() { return parameters; }
        public void setParameters(com.fasterxml.jackson.databind.JsonNode parameters) { this.parameters = parameters; }
    }
}
