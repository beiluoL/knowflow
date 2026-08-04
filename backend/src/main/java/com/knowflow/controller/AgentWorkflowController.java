package com.knowflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.entity.AgentWorkflow;
import com.knowflow.mapper.AgentWorkflowMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 自定义工作流配置接口：用户预设 prompt 模板 + 触发条件，由 Agent 编排层注入对话。
 */
@Tag(name = "编程助手-工作流")
@RestController
@RequestMapping("/api/agent/workflows")
@RequiredArgsConstructor
public class AgentWorkflowController {

    private final AgentWorkflowMapper workflowMapper;

    @Operation(summary = "工作流列表（当前用户）")
    @GetMapping
    public Result<List<AgentWorkflow>> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<AgentWorkflow> list = workflowMapper.selectList(
                new LambdaQueryWrapper<AgentWorkflow>()
                        .eq(AgentWorkflow::getUserId, userId)
                        .orderByAsc(AgentWorkflow::getSortOrder)
                        .orderByDesc(AgentWorkflow::getUpdateTime));
        return Result.success(list);
    }

    @Operation(summary = "创建工作流")
    @PostMapping
    public Result<AgentWorkflow> create(@RequestBody AgentWorkflow workflow) {
        Long userId = SecurityUtils.getCurrentUserId();
        workflow.setId(null);
        workflow.setUserId(userId);
        if (workflow.getEnabled() == null) workflow.setEnabled(1);
        if (workflow.getSortOrder() == null) workflow.setSortOrder(0);
        workflowMapper.insert(workflow);
        return Result.success(workflow);
    }

    @Operation(summary = "更新工作流")
    @PutMapping("/{id}")
    public Result<AgentWorkflow> update(@PathVariable Long id, @RequestBody AgentWorkflow workflow) {
        Long userId = SecurityUtils.getCurrentUserId();
        AgentWorkflow exist = workflowMapper.selectById(id);
        if (exist == null || !exist.getUserId().equals(userId)) {
            return Result.error("工作流不存在或无权访问");
        }
        workflow.setId(id);
        workflow.setUserId(userId);
        workflowMapper.updateById(workflow);
        return Result.success(workflowMapper.selectById(id));
    }

    @Operation(summary = "删除工作流（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        AgentWorkflow exist = workflowMapper.selectById(id);
        if (exist == null || !exist.getUserId().equals(userId)) {
            return Result.error("工作流不存在或无权访问");
        }
        workflowMapper.deleteById(id);
        return Result.success();
    }
}
