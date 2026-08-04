package com.knowflow.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.ModelAdapter;
import com.knowflow.agent.tool.ToolPermission;
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
 * <p>
 * P2 起上下文由 {@link AgentContextManager} 统一组装（滚动窗口 + 摘要压缩）；
 * P4 起支持 {@link AgentEventListener} 事件回调，用于向前端 SSE 推送工具执行过程。
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
    private final AgentContextManager contextManager;
    private final ObjectMapper objectMapper;
    private final com.knowflow.service.AgentWorkflowService workflowService;

    /**
     * Agent 运行过程事件监听：由调用方（SSE 控制器）实现，用于实时推送。
     * 默认空实现，便于同步调用方忽略。
     */
    public interface AgentEventListener {
        /** 模型开始第 iter 轮推理 */
        default void onThinking(int iter) {}

        /** 模型产出文本增量 */
        default void onDelta(String delta) {}

        /** 模型请求调用工具（执行前） */
        default void onToolStart(String callId, String toolName, String argsJson, ToolPermission permission) {}

        /** 工具执行完成 */
        default void onToolEnd(String callId, String toolName, ToolResult result) {}

        /** 运行期提示信息（如模型不支持工具时退化为普通对话的通知） */
        default void onInfo(String message) {}

        /**
         * 高危工具二次确认：返回 true 表示允许执行。
         * 默认拒绝，保证未接入确认通道的调用方不会误触发高危操作。
         */
        default boolean confirmTool(String callId, String toolName, String argsJson, ToolPermission permission) {
            return false;
        }
    }

    /** 默认监听器：不推送事件，高危工具一律拒绝。 */
    private static final AgentEventListener NOOP_LISTENER = new AgentEventListener() {};

    /**
     * 运行一轮 Agent 对话（非流式）：返回最终助手文本。
     *
     * @param sessionId 会话 ID
     * @param userText  用户本轮输入
     * @param userId    当前用户
     * @param configId  模型配置（可空=默认）
     */
    public String run(Long sessionId, String userText, Long userId, Long configId) {
        return run(sessionId, userText, userId, configId, NOOP_LISTENER);
    }

    /**
     * 运行一轮 Agent 对话，并通过监听器推送过程事件。
     *
     * @param listener 过程事件监听器，可为 null（等价于不推送）
     * @return 最终助手文本
     */
    public String run(Long sessionId, String userText, Long userId, Long configId, AgentEventListener listener) {
        AgentEventListener ln = listener != null ? listener : NOOP_LISTENER;
        AgentSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("会话不存在");
        }

        // 1. 由上下文管理器组装受控长度的历史（含摘要压缩）
        List<ModelAdapter.ChatMessage> messages =
                new ArrayList<>(contextManager.buildContext(session, userId, configId));

        // 1.1 自定义工作流：命中关键词的工作流模板以 system 消息注入（仅注入首个最匹配项）
        String workflowPrompt = workflowService.resolvePrompt(userText, userId);
        if (workflowPrompt != null && !workflowPrompt.isBlank()) {
            messages.add(new ModelAdapter.ChatMessage("system",
                    "[自定义工作流提示]\n" + workflowPrompt));
        }

        messages.add(new ModelAdapter.ChatMessage("user", userText));

        // 2. 构造工具声明（仅注入该用户已启用的工具）
        List<ModelAdapter.ToolSpec> toolSpecs = buildToolSpecs(userId);

        // 3. 能力检测：若当前模型不支持 tools（如 Ollama 上的 deepseek-coder:6.7b），
        //    直接退化为「普通对话」，不再走 ReAct 工具循环，避免向模型下发 tools 触发 400。
        //    同时告知用户当前模型不具备工具调用能力。
        if (!aiService.supportsTools(userId, configId)) {
            ln.onInfo("当前模型不支持工具调用（Function Calling），已自动退化为普通对话模式，"
                    + "文件读写/命令执行等工具均不可用。如需使用 Agent 工具能力，请切换到支持 tools 的模型（如 qwen-plus、gpt-4o 等）。");
            ln.onThinking(1);
            ModelAdapter.ChatResult result = aiService.chatMulti(messages, null, userId, configId);
            String text = result != null && result.content != null ? result.content : "";
            if (!text.isEmpty()) {
                ln.onDelta(text);
            }
            persistMessage(sessionId, userId, "assistant", text, "normal", null, null);
            return text;
        }

        // 4. ReAct 循环
        int iter = 0;
        while (iter < MAX_ITER) {
            iter++;
            ln.onThinking(iter);
            ModelAdapter.ChatResult result = aiService.chatMulti(messages, toolSpecs, userId, configId);
            String assistantText = result != null && result.content != null ? result.content : "";

            if (result == null || result.toolCalls == null || result.toolCalls.isEmpty()) {
                if (!assistantText.isEmpty()) {
                    ln.onDelta(assistantText);
                }
                persistMessage(sessionId, userId, "assistant", assistantText, "normal", null, null);
                return assistantText;
            }

            // 助手的工具调用意图先入上下文，模型才能正确关联后续 tool 结果
            ModelAdapter.ChatMessage assistantMsg = new ModelAdapter.ChatMessage("assistant", assistantText);
            assistantMsg.toolCalls = result.toolCalls;
            messages.add(assistantMsg);

            for (ModelAdapter.ToolCall call : result.toolCalls) {
                executeOneToolCall(call, session, userId, messages, ln);
            }
        }

        String fallback = "已达最大工具调用轮次（" + MAX_ITER + "），请简化需求或检查工具配置。";
        persistMessage(sessionId, userId, "assistant", fallback, "normal", null, null);
        return fallback;
    }

    /** 执行单个工具调用：权限确认 → 执行 → 落库 → 回灌上下文。 */
    private void executeOneToolCall(ModelAdapter.ToolCall call, AgentSession session, Long userId,
                                    List<ModelAdapter.ChatMessage> messages, AgentEventListener ln) {
        Long sessionId = session.getId();
        String argsJson = call.arguments != null ? call.arguments : "{}";
        JsonNode argsNode;
        try {
            argsNode = objectMapper.readTree(argsJson);
        } catch (Exception e) {
            argsNode = objectMapper.createObjectNode();
        }

        ToolPermission permission = toolRegistry.permissionOf(call.name);
        // 工具调用意图落库，供调用链可视化
        Long callMsgId = persistMessage(sessionId, userId, "assistant", argsJson,
                "tool_call", call.id, call.name);
        ln.onToolStart(call.id, call.name, argsJson, permission);

        ToolResult toolRes;
        // 高危工具需要调用方二次确认（前端 tool-confirm）
        if (permission == ToolPermission.DANGEROUS && !ln.confirmTool(call.id, call.name, argsJson, permission)) {
            toolRes = ToolResult.fail("用户拒绝执行高危工具 " + call.name, 0);
        } else {
            ToolContext ctx = ToolContext.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .workspaceDir(session.getProjectDir())
                    .messageId(callMsgId)
                    .build();
            toolRes = toolRegistry.invoke(call.name, argsNode, ctx);
        }

        String toolText = toolRes.isSuccess() ? toolRes.getOutput() : ("错误：" + toolRes.getError());
        persistMessage(sessionId, userId, "tool", toolText, "tool_result", call.id, call.name);
        ln.onToolEnd(call.id, call.name, toolRes);

        ModelAdapter.ChatMessage toolMsg = new ModelAdapter.ChatMessage("tool", toolText);
        toolMsg.toolCallId = call.id;
        messages.add(toolMsg);
    }

    /** 组装模型可见的工具声明列表。 */
    private List<ModelAdapter.ToolSpec> buildToolSpecs(Long userId) {
        List<ModelAdapter.ToolSpec> specs = new ArrayList<>();
        for (ToolRegistry.ToolMeta meta : toolRegistry.listToolsForUser(userId)) {
            if (!meta.enabled()) {
                continue;
            }
            ModelAdapter.ToolSpec spec = new ModelAdapter.ToolSpec();
            spec.name = meta.name();
            spec.description = meta.description();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> params = objectMapper.convertValue(meta.parameters(), java.util.Map.class);
            spec.parameters = params;
            specs.add(spec);
        }
        return specs;
    }

    /**
     * 统一的消息落库。
     *
     * @return 新增消息 ID，便于串联调用链
     */
    private Long persistMessage(Long sessionId, Long userId, String role, String content,
                                String messageType, String toolCallId, String toolName) {
        AgentMessage msg = new AgentMessage();
        msg.setSessionId(sessionId);
        msg.setUserId(userId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setMessageType(messageType);
        msg.setToolCallId(toolCallId);
        msg.setToolName(toolName);
        msg.setTokenCount(AgentContextManager.estimateTokens(content));
        messageMapper.insert(msg);
        return msg.getId();
    }
}
