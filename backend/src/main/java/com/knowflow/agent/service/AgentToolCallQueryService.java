package com.knowflow.agent.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.agent.tool.ToolRegistry;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.agent.entity.AgentSession;
import com.knowflow.agent.entity.AgentToolCall;
import com.knowflow.exception.BusinessException;
import com.knowflow.agent.mapper.AgentSessionMapper;
import com.knowflow.agent.mapper.AgentToolCallMapper;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
/** 按所有者隔离的工具调用历史与统计。 */
@Service
@RequiredArgsConstructor
public class AgentToolCallQueryService {
 private final AgentToolCallMapper toolCallMapper;
 private final AgentSessionMapper sessionMapper;
    public Result<List<AgentToolCall>> callChain(Long id) {
        requireOwnedSession(id);
        List<AgentToolCall> calls = toolCallMapper.selectList(new LambdaQueryWrapper<AgentToolCall>()
                .eq(AgentToolCall::getSessionId, id)
                .orderByAsc(AgentToolCall::getCreateTime));
        return Result.success(calls);
    }

    /**
     * 工具调用统计（P3）：按工具名聚合调用次数、成功率与平均耗时，供前端面板展示。
     */
    public Result<List<Map<String, Object>>> callStats(Long id) {
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

}
