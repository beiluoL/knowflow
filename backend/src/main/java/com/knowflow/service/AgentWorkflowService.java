package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.AgentWorkflow;
import com.knowflow.mapper.AgentWorkflowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自定义工作流匹配服务：在 Agent 编排入口，根据用户消息与已启用工作流，
 * 选出最匹配的工作流并返回渲染后的 prompt 片段。
 * <p>匹配规则：
 * - triggerType=keyword：消息文本命中 triggerValue 中任一关键词（逗号分隔）即匹配；
 * - triggerType=intent：当前编排未做显式意图识别，暂按 keyword 同等方式兜底（关键词含意图词）；
 * - triggerType=manual：仅由前端手动触发，不走自动注入；
 * 命中多个时取 sortOrder 最小者。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentWorkflowService {

    private final AgentWorkflowMapper workflowMapper;

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
            if (!"manual".equals(wf.getTriggerType()) && matches(wf, userInput)) {
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
