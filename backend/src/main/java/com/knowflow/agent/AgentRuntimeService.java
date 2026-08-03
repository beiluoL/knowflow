package com.knowflow.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.ModelAdapter;
import com.knowflow.agent.tool.ToolRegistry;
import com.knowflow.agent.tool.ToolResult;
import com.knowflow.entity.AgentMessage;
import com.knowflow.entity.AgentSession;
import com.knowflow.mapper.AgentMessageMapper;
import com.knowflow.mapper.AgentSessionMapper;
import com.knowflow.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 编程 Agent 运行时编排（ReAct 循环）：
 * 载入会话上下文 → 注入可用工具 → 调用模型 → 解析工具调用 → 执行并回灌 → 直至终态。
 * 循环上限 {@code MAX_ITER} 防止失控。工具调用通过 {@link ToolRegistry} 统一校验与执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuntimeService {

    /** ReAct 最大迭代轮次，避免无限工具循环。 */
    private static final int MAX_ITER = 8;

    private final AiService aiService;
    private final ToolRegistry toolRegistry;
    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    /**
     * 运行一轮 Agent 对话（非流式）：返回最终助手文本。
     * @param sessionId 会话 ID
     * @param userText  用户本轮输入
     * @param userId    当前用户
     * @param configId  模型配置（可空=默认）
     */
    public String run(Long sessionId, String userText, Long userId, Long configId) {
        AgentSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        // 1. 载入历史（按时间正序）
        List<AgentMessage> history = messageMapper.selectList(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, sessionId)
                .eq(AgentMessage::getDeleted, 0)
                .orderByAsc(AgentMessage::getCreateTime));

        // 2. 构造多轮消息
        List<ModelAdapter.ChatMessage> messages = new ArrayList<>();
        for (AgentMessage m : history) {
            if ("tool".equals(m.getRole()) || "tool_result".equals(m.getRole())) {
                ModelAdapter.ChatMessage tm = new ModelAdapter.ChatMessage("tool", m.getContent());
                tm.toolCallId = m.getToolCallId();
                messages.add(tm);
            } else {
                messages.add(new ModelAdapter.ChatMessage(m.getRole(), m.getContent()));
            }
        }
        messages.add(new ModelAdapter.ChatMessage("user", userText));

        // 3. 构造工具声明
        List<ModelAdapter.ToolSpec> toolSpecs = buildToolSpecs(userId);

        // 4. ReAct 循环
        int iter = 0;
        while (iter < MAX_ITER) {
            iter++;
            ModelAdapter.ChatResult result = aiService.chatMulti(messages, toolSpecs, userId, configId);
            String assistantText = result.content != null ? result.content : "";
            if (result.toolCalls == null || result.toolCalls.isEmpty()) {
                persistAssistant(sessionId, userId, assistantText);
                return assistantText;
            }
            // 将助手（含 tool_calls）作为 assistant 消息加入上下文由模型侧处理；
            // 这里直接对每个 tool_call 执行并回灌 tool 消息
            for (ModelAdapter.ToolCall call : result.toolCalls) {
                JsonNode argsNode;
                try {
                    argsNode = objectMapper.readTree(call.arguments != null ? call.arguments : "{}");
                } catch (Exception e) {
                    argsNode = objectMapper.createObjectNode();
                }
                ToolContext ctx = ToolContext.builder()
                        .userId(userId).sessionId(sessionId).workspaceDir(session.getProjectDir()).build();
                ToolResult toolRes = toolRegistry.invoke(call.name, argsNode, ctx);
                String toolText = toolRes.isSuccess() ? toolRes.getOutput() : ("错误：" + toolRes.getError());
                ModelAdapter.ChatMessage toolMsg = new ModelAdapter.ChatMessage("tool", toolText);
                toolMsg.toolCallId = call.id;
                messages.add(toolMsg);
            }
        }
        String fallback = "已达最大工具调用轮次，请简化需求或检查工具配置。";
        persistAssistant(sessionId, userId, fallback);
        return fallback;
    }

    private List<ModelAdapter.ToolSpec> buildToolSpecs(Long userId) {
        List<ModelAdapter.ToolSpec> specs = new ArrayList<>();
        for (ToolRegistry.ToolMeta meta : toolRegistry.listToolsForUser(userId)) {
            if (!meta.enabled()) continue;
            ModelAdapter.ToolSpec spec = new ModelAdapter.ToolSpec();
            spec.name = meta.name();
            spec.description = meta.description();
            spec.parameters = objectMapper.convertValue(meta.parameters(), java.util.Map.class);
            specs.add(spec);
        }
        return specs;
    }

    private void persistAssistant(Long sessionId, Long userId, String content) {
        AgentMessage msg = new AgentMessage();
        msg.setSessionId(sessionId);
        msg.setUserId(userId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setMessageType("normal");
        messageMapper.insert(msg);
    }
}
