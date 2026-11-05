package com.knowflow.agent.controller;

import com.knowflow.agent.AgentRuntimeService;
import com.knowflow.agent.approval.ToolApprovalService;
import java.time.Duration;
import com.knowflow.agent.tool.ToolPermission;
import com.knowflow.agent.tool.ToolResult;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.config.AiProviderRegistry;
import com.knowflow.agent.dto.CodeAgentChatDTO;
import com.knowflow.coding.dto.CodeRunRequest;
import com.knowflow.coding.dto.CodeRunResult;
import com.knowflow.agent.entity.AgentCallLog;
import com.knowflow.agent.entity.AgentMessage;
import com.knowflow.agent.entity.AgentSession;
import com.knowflow.ai.entity.UserAiConfig;
import com.knowflow.agent.mapper.AgentCallLogMapper;
import com.knowflow.agent.mapper.AgentMessageMapper;
import com.knowflow.agent.mapper.AgentSessionMapper;
import com.knowflow.ai.mapper.UserAiConfigMapper;
import com.knowflow.exception.BusinessException;
import com.knowflow.ai.service.AiService;
import com.knowflow.coding.service.CodeExecutionService;
import com.knowflow.agent.vo.AgentMessageVO;
import com.knowflow.agent.vo.AgentSessionVO;
import com.knowflow.agent.vo.AgentStatsVO;
import com.knowflow.ai.vo.PlatformModelVO;
import com.knowflow.ai.vo.UserAiConfigVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 编程 Agent 接口。
 * <p>
 * 整合 AI 流式对话、代码执行、模型管理能力，配合前端 {@code /coding/agent} 页面提供
 * 类 Cursor/Cline 的本地编程 Agent 体验。
 * <ul>
 *   <li>{@code GET  /api/agent/models}：列出用户已配置模型 + 平台预设模型</li>
 *   <li>{@code POST /api/agent/chat/stream}：SSE 流式对话（逐 token 推送）</li>
 *   <li>{@code POST /api/agent/execute}：执行代码（复用 {@link CodeExecutionService}）</li>
 *   <li>{@code POST /api/agent/health-check}：检测指定模型配置可用性</li>
 *   <li>会话管理：{@code GET/POST/PUT/DELETE /api/agent/sessions} 与 {@code GET /api/agent/sessions/{id}/messages}</li>
 *   <li>模型监测：{@code GET /api/agent/stats?rangeHours=24}</li>
 * </ul>
 */
@Slf4j
@Tag(name = "编程 Agent 接口")
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class CodeAgentController {

    /** 分页单页最大条数，防止前端传入超大 size 拖垮查询。 */
    private static final long MAX_PAGE_SIZE = 100L;

    /** 上下文窗口允许的最小 / 最大值（预估 token）。 */
    private static final int MIN_CONTEXT_WINDOW = 1000;
    private static final int MAX_CONTEXT_WINDOW = 128000;

    /** 高危工具二次确认的等待超时（秒）：超时视为拒绝。 */
    private static final int TOOL_CONFIRM_TIMEOUT_SECONDS = 60;

    private final ToolApprovalService toolApprovals;

    private final com.knowflow.agent.service.CodeAgentPersistenceService persistenceService;
    private final AiService aiService;
    private final CodeExecutionService codeExecutionService;
    private final AiProviderRegistry providerRegistry;
    private final UserAiConfigMapper userAiConfigMapper;
    private final AgentSessionMapper agentSessionMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final AgentCallLogMapper agentCallLogMapper;
    private final AgentRuntimeService agentRuntimeService;
    private final com.knowflow.security.HostCapabilityGuard hostGuard;

    /** 异步执行流式对话，避免阻塞 Servlet 线程。 */
    private final ExecutorService agentExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "agent-stream");
        t.setDaemon(true);
        return t;
    });

    @Operation(summary = "列出可用模型（用户配置 + 平台预设）")
    @GetMapping("/models")
    public Result<Map<String, Object>> models() {
        Long userId = SecurityUtils.getCurrentUserId();
        // 用户已配置的模型列表
        List<UserAiConfig> configs = userAiConfigMapper.selectList(
                new LambdaQueryWrapper<UserAiConfig>()
                        .eq(UserAiConfig::getUserId, userId)
                        .orderByDesc(UserAiConfig::getIsActive)
                        .orderByDesc(UserAiConfig::getCreateTime));
        List<UserAiConfigVO> userModels = configs.stream().map(this::toVO).collect(Collectors.toList());

        // 平台预设（用于"添加新配置"时选择提供商）
        List<PlatformModelVO> platformModels = new ArrayList<>();
        for (AiProviderRegistry.ProviderInfo info : providerRegistry.all()) {
            PlatformModelVO vo = new PlatformModelVO();
            vo.setProvider(info.getProvider());
            vo.setLabel(info.getLabel());
            vo.setBaseUrl(info.getBaseUrl());
            vo.setModel(info.getDefaultModel());
            vo.setDefaultModel(info.getDefaultModel());
            vo.setSubscriptionRequired(info.isSubscriptionRequired());
            vo.setPriceInfo(info.getPriceInfo());
            vo.setProviderType(info.getType().name());
            vo.setCapability(info.getCapability().name());
            platformModels.add(vo);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("userModels", userModels);
        data.put("platformModels", platformModels);
        return Result.success(data);
    }

    /**
     * 流式对话（SSE）。
     * <p>
     * 推送事件：
     * <ul>
     *   <li>{@code delta}：{ content: "token" } — 逐 token 增量</li>
     *   <li>{@code done}：{ content: "完整文本", sessionId: 123 } — 流结束，携带会话ID</li>
     *   <li>{@code error}：{ error: "错误信息" } — 异常</li>
     * </ul>
     * 前端用 EventSource 监听；注意 EventSource 仅支持 GET，因此前端需用 fetch + ReadableStream 消费 POST 流。
     * <p>
     * 持久化策略：
     * <ul>
     *   <li>主线程同步创建/获取会话，并保存 user 消息</li>
     *   <li>异步线程执行流式对话，用包装的 SseEmitter 拦截 done/error 事件，
     *       在结束时保存 assistant 消息与调用日志</li>
     * </ul>
     */
    @Operation(summary = "编程 Agent 流式对话（SSE）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody CodeAgentChatDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);

        // 在主线程提前校验，避免异步线程中 SecurityContext 丢失
        if (dto.getMessages() == null || dto.getMessages().isEmpty()) {
            sendEvent(emitter, "error", Map.of("error", "对话消息不能为空"));
            complete(emitter);
            return emitter;
        }

        // ===== 主线程：同步持久化会话与 user 消息 =====
        // 取最后一条 user 消息作为当前问题
        CodeAgentChatDTO.ChatMessage lastMsg = dto.getMessages().get(dto.getMessages().size() - 1);
        String userContent = lastMsg.getContent();
        Long sessionId = persistenceService.startTurn(dto.getSessionId(), userId, dto.getConfigId(),
                dto.getTitle(), userContent, dto.getFilePath(), false);
        if (sessionId == null) {
            sendEvent(emitter, "error", Map.of("error", "会话不存在或无权访问"));
            complete(emitter);
            return emitter;
        }

        // 推送会话ID给前端（前端据此更新当前 sessionId）
        sendEvent(emitter, "session", Map.of("sessionId", sessionId));

        // ===== 异步线程：执行流式对话 =====
        // 拼接 system 提示与多轮对话上下文
        String systemPrompt = buildSystemPrompt(dto);
        String userPrompt = buildUserPrompt(dto.getMessages());

        log.info("编程 Agent 流式对话: userId={}, sessionId={}, configId={}, msgCount={}",
                userId, sessionId, dto.getConfigId(), dto.getMessages().size());

        // 捕获模型提供商用于调用日志
        String provider = resolveProviderName(userId, dto.getConfigId());
        long startMs = System.currentTimeMillis();
        final Long finalSessionId = sessionId;
        final String finalProvider = provider;

        agentExecutor.submit(hostGuard.wrapAuthenticated(() -> {
            try {
                // 使用带运行时参数的 streamChat：流结束时自动回调，持久化 assistant 消息与调用日志
                aiService.streamChat(systemPrompt, userPrompt, userId, dto.getConfigId(), emitter,
                        (content, success) -> {
                            long latency = System.currentTimeMillis() - startMs;

                            // 保存 assistant 消息
                            AgentMessage assistantMsg = new AgentMessage();
                            assistantMsg.setSessionId(finalSessionId);
                            assistantMsg.setUserId(userId);
                            assistantMsg.setRole("assistant");
                            assistantMsg.setContent(content);
                            assistantMsg.setLatencyMs((int) latency);
                            assistantMsg.setIsError(success ? 0 : 1);
                            assistantMsg.setTokenCount(estimateTokens(content));


                            // 更新会话统计


                            // 记录调用日志
                            AgentCallLog callLog = new AgentCallLog();
                            callLog.setUserId(userId);
                            callLog.setConfigId(dto.getConfigId());
                            callLog.setProvider(finalProvider);
                            callLog.setSessionId(finalSessionId);
                            callLog.setSuccess(success ? 1 : 0);
                            callLog.setLatencyMs((int) latency);
                            callLog.setTokenIn(estimateTokens(userPrompt));
                            callLog.setTokenOut(estimateTokens(content));
                            if (!success) callLog.setErrorMsg(truncate(content, 1000));
                            persistenceService.completeTurn(assistantMsg, callLog, content);
                        },
                        dto.getTemperature(), dto.getMaxTokens(), dto.getTopP());
            } catch (Exception e) {
                log.error("Agent 流式对话异常: userId={}, err={}", userId, e.getMessage(), e);
                sendEvent(emitter, "error", Map.of("error", "对话失败：" + e.getMessage()));
                complete(emitter);
            }
        }));

        emitter.onCompletion(() -> log.debug("Agent SSE 连接完成: userId={}", userId));
        emitter.onTimeout(() -> {
            log.warn("Agent SSE 连接超时: userId={}", userId);
            complete(emitter);
        });
        emitter.onError(throwable -> {
            log.warn("Agent SSE 连接错误: userId={}, err={}", userId, throwable.getMessage());
            complete(emitter);
        });

        return emitter;
    }

    /**
     * 编程 Agent 同步对话（ReAct 工具编排）。
     * 在当前会话上下文基础上，加载启用工具并执行「模型→工具→回灌」循环，直到模型给出终态文本。
     * 适用于需要在后端完整跑通工具链的场景（前端可在后续阶段接入流式 tool-call 事件）。
     */
    @Operation(summary = "编程 Agent 同步对话（含工具调用编排）")
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        String userContent = body.get("content") != null ? body.get("content").toString() : null;
        if (userContent == null || userContent.isBlank()) {
            return Result.error("对话内容不能为空");
        }
        Long sessionId = body.get("sessionId") != null ? Long.valueOf(body.get("sessionId").toString()) : null;
        Long configId = body.get("configId") != null ? Long.valueOf(body.get("configId").toString()) : null;

        sessionId = persistenceService.startTurn(sessionId, userId, configId, null, userContent, null, false);
        if (sessionId == null) return Result.error("会话不存在或无权访问");

        // 运行 ReAct 编排（含工具调用）
        String reply = agentRuntimeService.run(sessionId, userContent, userId, configId);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("content", reply);
        return Result.success(result);
    }

    /**
     * 编程 Agent 流式对话（含工具调用编排，P4）。
     * <p>
     * 在原有 {@code delta/done/error} 之外扩展以下事件：
     * <ul>
     *   <li>{@code session}：{ sessionId }</li>
     *   <li>{@code thinking}：{ iter } — 第 N 轮模型推理开始</li>
     *   <li>{@code tool-confirm}：{ callId, tool, args, permission } — 高危工具待确认，前端需回调确认接口</li>
     *   <li>{@code tool-start}：{ callId, tool, args, permission } — 工具开始执行</li>
     *   <li>{@code tool-end}：{ callId, tool, success, output, latencyMs } — 工具执行完成</li>
     *   <li>{@code done}：{ content, sessionId }</li>
     * </ul>
     */
    @Operation(summary = "编程 Agent 流式对话（含工具调用编排）")
    @PostMapping(value = "/chat/agent-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter agentStream(@RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);

        String userContent = body.get("content") != null ? body.get("content").toString() : null;
        if (userContent == null || userContent.isBlank()) {
            sendEvent(emitter, "error", Map.of("error", "对话内容不能为空"));
            complete(emitter);
            return emitter;
        }
        Long configId = body.get("configId") != null ? Long.valueOf(body.get("configId").toString()) : null;
        Long sessionId = body.get("sessionId") != null ? Long.valueOf(body.get("sessionId").toString()) : null;

        sessionId = persistenceService.startTurn(sessionId, userId, configId, null, userContent, null, true);
        if (sessionId == null) {
            sendEvent(emitter, "error", Map.of("error", "会话不存在或无权访问"));
            complete(emitter);
            return emitter;
        }
        sendEvent(emitter, "session", Map.of("sessionId", sessionId));

        final Long finalSessionId = sessionId;
        final long startMs = System.currentTimeMillis();
        final String provider = resolveProviderName(userId, configId);
        var approvals = toolApprovals.openScope(userId);
        emitter.onCompletion(approvals::close);
        emitter.onTimeout(() -> {
            approvals.close();
            complete(emitter);
        });
        emitter.onError(error -> approvals.close());

        agentExecutor.submit(hostGuard.wrapAuthenticated(() -> {
            try {
                String reply = agentRuntimeService.run(finalSessionId, userContent, userId, configId,
                        new AgentRuntimeService.AgentEventListener() {
                            @Override
                            public void onThinking(int iter) {
                                sendEvent(emitter, "thinking", Map.of("iter", iter));
                            }

                            @Override
                            public void onDelta(String delta) {
                                sendEvent(emitter, "delta", Map.of("content", delta));
                            }

                            @Override
                            public void onInfo(String message) {
                                sendEvent(emitter, "info", Map.of("message", message));
                            }

                            @Override
                            public void onToolStart(String callId, String toolName, String argsJson,
                                                    ToolPermission permission) {
                                Map<String, Object> data = new HashMap<>();
                                data.put("callId", callId);
                                data.put("tool", toolName);
                                data.put("args", argsJson);
                                data.put("permission", permission.name());
                                sendEvent(emitter, "tool-start", data);
                            }

                            @Override
                            public void onToolEnd(String callId, String toolName, ToolResult result) {
                                Map<String, Object> data = new HashMap<>();
                                data.put("callId", callId);
                                data.put("tool", toolName);
                                data.put("success", result.isSuccess());
                                data.put("output", truncate(result.isSuccess()
                                        ? result.getOutput() : result.getError(), 4000));
                                data.put("latencyMs", result.getLatencyMs());
                                sendEvent(emitter, "tool-end", data);
                            }

                            @Override
                            public boolean confirmTool(String callId, String toolName, String argsJson,
                                                       ToolPermission permission) {
                                return awaitToolConfirm(emitter, approvals, toolName, argsJson, permission);
                            }
                        });

                long latency = System.currentTimeMillis() - startMs;


                AgentCallLog callLog = new AgentCallLog();
                callLog.setUserId(userId);
                callLog.setConfigId(configId);
                callLog.setProvider(provider);
                callLog.setSessionId(finalSessionId);
                callLog.setSuccess(1);
                callLog.setLatencyMs((int) latency);
                callLog.setTokenIn(estimateTokens(userContent));
                callLog.setTokenOut(estimateTokens(reply));
                persistenceService.completeTurn(null, callLog, reply);

                Map<String, Object> done = new HashMap<>();
                done.put("content", reply);
                done.put("sessionId", finalSessionId);
                sendEvent(emitter, "done", done);
            } catch (Exception e) {
                log.error("Agent 工具流式对话异常: userId={}, err={}", userId, e.getMessage(), e);
                sendEvent(emitter, "error", Map.of("error", "对话失败：" + e.getMessage()));
            } finally {
                approvals.close();
                complete(emitter);
            }
        }));

        return emitter;
    }

    /**
     * 推送 tool-confirm 事件并阻塞等待前端确认。
     *
     * @return true 表示用户允许执行；超时或异常一律视为拒绝
     */
    private boolean awaitToolConfirm(SseEmitter emitter, ToolApprovalService.Scope approvals,
                                     String toolName, String argsJson, ToolPermission permission) {
        String approvalId = approvals.request(Duration.ofSeconds(TOOL_CONFIRM_TIMEOUT_SECONDS));
        if (approvalId == null) {
            return false;
        }
        Map<String, Object> data = Map.of(
                "callId", approvalId, "tool", toolName, "args", argsJson,
                "permission", permission.name(), "timeoutSeconds", TOOL_CONFIRM_TIMEOUT_SECONDS);
        try {
            String payload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
            emitter.send(SseEmitter.event().name("tool-confirm").data(payload));
        } catch (Exception exception) {
            approvals.close();
            return false;
        }
        return approvals.await(approvalId);
    }

    /**
     * 高危工具二次确认回调（P4）：前端收到 tool-confirm 事件后调用本接口放行或拒绝。
     */
    @Operation(summary = "确认/拒绝高危工具调用")
    @PostMapping("/tool-confirm")
    public Result<Void> toolConfirm(@RequestBody Map<String, Object> body) {
        long userId = SecurityUtils.getCurrentUserId();
        if (!(body.get("callId") instanceof String callId) || callId.isBlank()
                || !(body.get("approved") instanceof Boolean approved)) {
            throw new BusinessException(400, "确认参数不正确");
        }
        if (!toolApprovals.decide(userId, callId, approved)) {
            throw new BusinessException(404, "该工具调用已结束或不存在");
        }
        return Result.success(null);
    }

    // ==================== 会话管理 ====================

    @Operation(summary = "列出当前用户的会话（全量，兼容旧前端）")
    @GetMapping("/sessions")
    public Result<List<AgentSessionVO>> listSessions() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<AgentSession> sessions = agentSessionMapper.selectList(
                new LambdaQueryWrapper<AgentSession>()
                        .eq(AgentSession::getUserId, userId)
                        .orderByDesc(AgentSession::getUpdateTime));
        return Result.success(sessions.stream().map(this::toSessionVO).collect(Collectors.toList()));
    }

    /**
     * 会话分页列表（P3）：支持标题关键字过滤，供前端侧边栏「加载更多」。
     */
    @Operation(summary = "分页列出会话")
    @GetMapping("/sessions/page")
    public Result<IPage<AgentSessionVO>> pageSessions(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        Long userId = SecurityUtils.getCurrentUserId();
        LambdaQueryWrapper<AgentSession> wrapper = new LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getUserId, userId)
                .orderByDesc(AgentSession::getUpdateTime);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(AgentSession::getTitle, keyword.trim());
        }
        Page<AgentSession> page = agentSessionMapper.selectPage(
                new Page<>(current, Math.min(size, MAX_PAGE_SIZE)), wrapper);
        return Result.success(page.convert(this::toSessionVO));
    }

    /** 会话实体 → VO，并回填模型显示名。 */
    private AgentSessionVO toSessionVO(AgentSession s) {
        AgentSessionVO vo = new AgentSessionVO();
        vo.setId(s.getId());
        vo.setUserId(s.getUserId());
        vo.setTitle(s.getTitle());
        vo.setConfigId(s.getConfigId());
        vo.setProjectDir(s.getProjectDir());
        vo.setMessageCount(s.getMessageCount());
        vo.setLastMessage(s.getLastMessage());
        vo.setContextWindow(s.getContextWindow());
        vo.setAgentMode(s.getAgentMode());
        vo.setCreateTime(s.getCreateTime());
        vo.setUpdateTime(s.getUpdateTime());
        if (s.getConfigId() != null) {
            UserAiConfig cfg = userAiConfigMapper.selectById(s.getConfigId());
            if (cfg != null) {
                vo.setConfigLabel(cfg.getDisplayName() != null ? cfg.getDisplayName() : cfg.getProvider());
            }
        }
        return vo;
    }

    @Operation(summary = "创建会话")
    @PostMapping("/sessions")
    public Result<AgentSessionVO> createSession(@RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        AgentSession session = new AgentSession();
        session.setUserId(userId);
        String title = body.get("title") != null ? body.get("title").toString() : "新会话";
        session.setTitle(title);
        if (body.get("configId") != null) {
            session.setConfigId(Long.valueOf(body.get("configId").toString()));
        }
        if (body.get("projectDir") != null) {
            session.setProjectDir(body.get("projectDir").toString());
        }
        session.setMessageCount(0);
        agentSessionMapper.insert(session);

        AgentSessionVO vo = new AgentSessionVO();
        vo.setId(session.getId());
        vo.setUserId(userId);
        vo.setTitle(session.getTitle());
        vo.setConfigId(session.getConfigId());
        vo.setProjectDir(session.getProjectDir());
        vo.setMessageCount(0);
        vo.setCreateTime(session.getCreateTime());
        vo.setUpdateTime(session.getUpdateTime());
        return Result.success(vo);
    }

    /**
     * 更新会话配置（P3）：支持重命名，同时可调整模型、项目目录、上下文窗口与运行模式。
     * 仅更新请求体中出现的字段，未出现的保持原值。
     */
    @Operation(summary = "更新会话（重命名 / 模型 / 上下文窗口 / 模式）")
    @PutMapping("/sessions/{id}")
    public Result<AgentSessionVO> updateSession(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        AgentSession session = agentSessionMapper.selectById(id);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在或无权访问");
        }
        Object title = body.get("title");
        if (title != null && !title.toString().isBlank()) {
            session.setTitle(truncate(title.toString(), 100));
        }
        if (body.containsKey("configId")) {
            Object cfg = body.get("configId");
            session.setConfigId(cfg != null ? Long.valueOf(cfg.toString()) : null);
        }
        if (body.containsKey("projectDir")) {
            Object dir = body.get("projectDir");
            session.setProjectDir(dir != null ? dir.toString() : null);
        }
        if (body.get("contextWindow") != null) {
            int cw = Integer.parseInt(body.get("contextWindow").toString());
            // 约束在合理区间，避免上下文过短导致对话不连贯或过长击穿模型上限
            session.setContextWindow(Math.max(MIN_CONTEXT_WINDOW, Math.min(MAX_CONTEXT_WINDOW, cw)));
        }
        Object mode = body.get("agentMode");
        if (mode != null && ("chat".equals(mode.toString()) || "agent".equals(mode.toString()))) {
            session.setAgentMode(mode.toString());
        }
        agentSessionMapper.updateById(session);
        return Result.success(toSessionVO(agentSessionMapper.selectById(id)));
    }

    @Operation(summary = "删除会话（逻辑删除，连同消息一并标记）")
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!persistenceService.deleteSession(id, userId)) {
            return Result.error("会话不存在或无权访问");
        }
        return Result.success(null);
    }

    @Operation(summary = "获取会话的历史消息（全量，兼容旧前端）")
    @GetMapping("/sessions/{id}/messages")
    public Result<List<AgentMessageVO>> getMessages(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireOwnedSession(id, userId);
        List<AgentMessage> msgs = agentMessageMapper.selectList(
                new LambdaQueryWrapper<AgentMessage>()
                        .eq(AgentMessage::getSessionId, id)
                        .orderByAsc(AgentMessage::getCreateTime));
        return Result.success(msgs.stream().map(this::toMessageVO).collect(Collectors.toList()));
    }

    /**
     * 会话消息分页（P2/P3）：按 ID 倒序分页，返回时再翻转为时间正序，
     * 便于前端「向上滚动加载更早消息」。第 1 页即最新消息。
     *
     * @param beforeId 可选游标，仅返回 ID 小于该值的消息（比 offset 分页更稳定）
     */
    @Operation(summary = "分页获取会话历史消息")
    @GetMapping("/sessions/{id}/messages/page")
    public Result<Map<String, Object>> pageMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "30") long size,
            @RequestParam(required = false) Long beforeId) {
        Long userId = SecurityUtils.getCurrentUserId();
        requireOwnedSession(id, userId);

        long limit = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<AgentMessage> wrapper = new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, id)
                .orderByDesc(AgentMessage::getId)
                // 多取一条用于判断是否还有更早的消息
                .last("LIMIT " + (limit + 1));
        if (beforeId != null) {
            wrapper.lt(AgentMessage::getId, beforeId);
        }
        List<AgentMessage> rows = agentMessageMapper.selectList(wrapper);

        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, (int) limit);
        }
        List<AgentMessageVO> voList = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            voList.add(toMessageVO(rows.get(i)));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("records", voList);
        data.put("hasMore", hasMore);
        // 下一页游标：当前页最早一条消息的 ID
        data.put("nextCursor", voList.isEmpty() ? null : voList.get(0).getId());
        return Result.success(data);
    }

    /** 消息实体 → VO。 */
    private AgentMessageVO toMessageVO(AgentMessage m) {
        AgentMessageVO vo = new AgentMessageVO();
        vo.setId(m.getId());
        vo.setSessionId(m.getSessionId());
        vo.setRole(m.getRole());
        vo.setContent(m.getContent());
        vo.setFilePath(m.getFilePath());
        vo.setMessageType(m.getMessageType());
        vo.setToolCallId(m.getToolCallId());
        vo.setToolName(m.getToolName());
        vo.setTokenCount(m.getTokenCount());
        vo.setLatencyMs(m.getLatencyMs());
        vo.setIsError(m.getIsError());
        vo.setCreateTime(m.getCreateTime());
        return vo;
    }

    /** 校验会话存在且属于当前用户，否则抛出统一业务异常。 */
    private AgentSession requireOwnedSession(Long sessionId, Long userId) {
        AgentSession session = agentSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在或无权访问");
        }
        return session;
    }

    // ==================== 模型监测 ====================

    @Operation(summary = "模型监测统计数据（用于仪表盘）")
    @GetMapping("/stats")
    public Result<AgentStatsVO> stats(@RequestParam(defaultValue = "24") int rangeHours) {
        Long userId = SecurityUtils.getCurrentUserId();
        // 限制范围：1~168（1小时~7天）
        int hours = Math.max(1, Math.min(168, rangeHours));
        LocalDateTime startTime = LocalDateTime.now().minusHours(hours);

        AgentStatsVO vo = new AgentStatsVO();
        vo.setRangeHours(hours);
        vo.setSummary(agentCallLogMapper.summary(userId, startTime));
        vo.setByModel(agentCallLogMapper.statByConfig(userId, startTime));
        vo.setHourly(agentCallLogMapper.statHourly(userId, startTime));
        return Result.success(vo);
    }

    @Operation(summary = "执行代码（可信本地开发宿主机执行，非 OS Sandbox）")
    @PostMapping("/execute")
    public Result<CodeRunResult> execute(@Valid @RequestBody CodeRunRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        CodeRunResult result = codeExecutionService.execute(request, userId);
        return Result.success(result);
    }

    @Operation(summary = "检测模型配置可用性")
    @PostMapping("/health-check")
    public Result<String> healthCheck(@RequestParam(required = false) Long configId) {
        Long userId = SecurityUtils.getCurrentUserId();
        String result = aiService.healthCheck(userId, configId);
        return Result.success(result);
    }

    // ==================== 内部工具 ====================

    /** 构建编程 Agent 系统提示词。 */
    private String buildSystemPrompt(CodeAgentChatDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 KnowFlow 编程 Agent，一个集成在开发环境中的资深编程助手。你的职责是：\n");
        sb.append("1. 回答编程相关问题，提供清晰、准确、可操作的解答；\n");
        sb.append("2. 生成高质量代码，使用 markdown 代码块标注语言；\n");
        sb.append("3. 解释代码原理、调试错误、给出优化建议；\n");
        sb.append("4. 当用户提供了文件上下文时，结合文件内容作答；\n");
        sb.append("5. 用简体中文回答，语气友好专业。\n");
        sb.append("要求：代码需完整可运行，涉及多文件时分别给出；复杂问题先简述思路再给代码。");

        // 注入文件上下文
        if (dto.getFileContext() != null && !dto.getFileContext().isBlank()) {
            sb.append("\n\n【当前打开的文件");
            if (dto.getFilePath() != null && !dto.getFilePath().isBlank()) {
                sb.append("：").append(dto.getFilePath());
            }
            sb.append("】\n```\n").append(dto.getFileContext()).append("\n```");
        }
        return sb.toString();
    }

    /**
     * 将多轮 messages 拼接为 user prompt。
     * <p>保留历史对话，让模型理解上下文；最后一条 user 消息作为当前问题突出显示。
     */
    private String buildUserPrompt(List<CodeAgentChatDTO.ChatMessage> messages) {
        if (messages.size() == 1) {
            return messages.get(0).getContent();
        }
        StringBuilder sb = new StringBuilder();
        // 历史对话（除最后一条）
        sb.append("【对话历史】\n");
        for (int i = 0; i < messages.size() - 1; i++) {
            CodeAgentChatDTO.ChatMessage msg = messages.get(i);
            String role = "assistant".equals(msg.getRole()) ? "助手" : "用户";
            sb.append(role).append("：").append(msg.getContent()).append("\n\n");
        }
        // 当前问题
        CodeAgentChatDTO.ChatMessage last = messages.get(messages.size() - 1);
        sb.append("【当前问题】\n").append(last.getContent());
        return sb.toString();
    }

    private UserAiConfigVO toVO(UserAiConfig config) {
        UserAiConfigVO vo = new UserAiConfigVO();
        vo.setId(config.getId());
        vo.setProvider(config.getProvider());
        vo.setApiKeyMasked(maskKey(config.getApiKey()));
        vo.setBaseUrl(config.getBaseUrl());
        vo.setModel(config.getModel());
        vo.setIsActive(config.getIsActive());
        vo.setProviderType(config.getProviderType());
        vo.setCapability(config.getCapability());
        vo.setDisplayName(config.getDisplayName());
        AiProviderRegistry.ProviderInfo info = providerRegistry.find(config.getProvider());
        if (info != null) {
            vo.setProviderLabel(info.getLabel());
            vo.setIsLocal(info.getType() == AiProviderRegistry.ProviderType.LOCAL);
            if (vo.getProviderType() == null) vo.setProviderType(info.getType().name());
            if (vo.getCapability() == null) vo.setCapability(info.getCapability().name());
        }
        return vo;
    }

    private String maskKey(String key) {
        if (key == null) return null;
        if ("local".equals(key)) return "local";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = om.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (Exception e) {
            log.debug("Agent SSE 发送失败: event={}, err={}", eventName, e.getMessage());
        }
    }

    private void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    /** 粗略估算 token 数：中文按 1.5 字/token，英文按 4 字符/token 折中。 */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / 2.5);
    }

    /** 根据用户ID和configId解析出 provider 名称（用于调用日志聚合）。 */
    private String resolveProviderName(Long userId, Long configId) {
        if (configId == null) return "default";
        try {
            UserAiConfig cfg = userAiConfigMapper.selectById(configId);
            if (cfg != null && cfg.getUserId().equals(userId)) {
                return cfg.getProvider();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
