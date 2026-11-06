package com.knowflow.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.agent.entity.AgentWorkflow;
import com.knowflow.agent.mapper.AgentWorkflowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 管理工作流默认值、用户隔离及生命周期持久化。 */
@Service
@RequiredArgsConstructor
public class AgentWorkflowService {

    private final AgentWorkflowMapper workflowMapper;
    /** 为工作流操作绑定当前认证所有者。 */
    public Result<List<AgentWorkflow>> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<AgentWorkflow> list = workflowMapper.selectList(
                new LambdaQueryWrapper<AgentWorkflow>()
                        .eq(AgentWorkflow::getUserId, userId)
                        .orderByAsc(AgentWorkflow::getSortOrder)
                        .orderByDesc(AgentWorkflow::getUpdateTime));
        return Result.success(list);
    }

    /** 为工作流操作绑定当前认证所有者。 */
    public Result<AgentWorkflow> create(AgentWorkflow workflow) {
        Long userId = SecurityUtils.getCurrentUserId();
        workflow.setId(null);
        workflow.setUserId(userId);
        if (workflow.getEnabled() == null) workflow.setEnabled(1);
        if (workflow.getSortOrder() == null) workflow.setSortOrder(0);
        workflowMapper.insert(workflow);
        return Result.success(workflow);
    }

    /** 为工作流操作绑定当前认证所有者。 */
    public Result<AgentWorkflow> update(Long id, AgentWorkflow workflow) {
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

    /** 为工作流操作绑定当前认证所有者。 */
    public Result<Void> delete(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        AgentWorkflow exist = workflowMapper.selectById(id);
        if (exist == null || !exist.getUserId().equals(userId)) {
            return Result.error("工作流不存在或无权访问");
        }
        workflowMapper.deleteById(id);
        return Result.success();
    }
    /**
     * 匹配工作流并渲染 prompt 模板。无命中返回 null。
     * 占位符：{input}=用户输入，{file}=当前文件（暂留空，由前端透传预留），{tree}=留空。
     */
    public String resolvePrompt(String userInput, Long userId) {
        if (userInput == null || userInput.isBlank()) return null;
        List<AgentWorkflow> workflows = workflowMapper.selectList(
                new LambdaQueryWrapper<AgentWorkflow>()
                        .eq(AgentWorkflow::getUserId, userId)
                        .eq(AgentWorkflow::getEnabled, 1)
                        .orderByAsc(AgentWorkflow::getSortOrder));
        for (AgentWorkflow wf : workflows) {
            if ("keyword".equals(wf.getTriggerType()) && matches(wf, userInput)) {
                return render(wf.getPromptTemplate(), userInput);
            }
        }
        return null;
    }

    private boolean matches(AgentWorkflow wf, String input) {
        String value = wf.getTriggerValue();
        if (value == null || value.isBlank()) return false;
        String lower = input.toLowerCase();
        for (String token : value.split(",")) {
            String t = token.trim().toLowerCase();
            if (!t.isEmpty() && lower.contains(t)) return true;
        }
        return false;
    }

    private String render(String template, String input) {
        if (template == null) return null;
        return template
                .replace("{input}", input)
                .replace("{file}", "")
                .replace("{tree}", "");
    }
}
