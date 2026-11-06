package com.knowflow.agent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.agent.entity.AgentWorkflow;
import com.knowflow.agent.service.AgentWorkflowService;
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

    private final AgentWorkflowService workflowService;

    @Operation(summary = "工作流列表（当前用户）")
    @GetMapping
    public Result<List<AgentWorkflow>> list() {
        return workflowService.list();
    }

    @Operation(summary = "创建工作流")
    @PostMapping
    public Result<AgentWorkflow> create(@RequestBody AgentWorkflow workflow) {
        return workflowService.create(workflow);
    }

    @Operation(summary = "更新工作流")
    @PutMapping("/{id}")
    public Result<AgentWorkflow> update(@PathVariable Long id, @RequestBody AgentWorkflow workflow) {
        return workflowService.update(id, workflow);
    }

    @Operation(summary = "删除工作流（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return workflowService.delete(id);
    }
}
