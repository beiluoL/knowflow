package com.knowflow.agent.service;
import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.exception.BusinessException;
import com.knowflow.agent.entity.*;
import com.knowflow.agent.mapper.AgentSessionMapper;
import com.knowflow.agent.mapper.AgentMessageMapper;
import com.knowflow.agent.mapper.AgentCallLogMapper;
import com.knowflow.agent.vo.*;
import com.knowflow.ai.entity.UserAiConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
/** 管理认证用户的会话目录、消息游标和用量报告。 */
@Service
@RequiredArgsConstructor
public class AgentSessionCatalogService {
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MIN_CONTEXT_WINDOW = 1000;
    private static final int MAX_CONTEXT_WINDOW = 128000;
    private final AgentSessionMapper agentSessionMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final AgentCallLogMapper agentCallLogMapper;
    private final CodeAgentPersistenceService persistenceService;
    private final com.knowflow.ai.service.UserAiConfigService configService;
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
    public Result<IPage<AgentSessionVO>> pageSessions(
            long current,
            long size,
            String keyword) {
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
            UserAiConfig cfg = configService.findOwned(s.getUserId(), s.getConfigId());
            if (cfg != null) {
                vo.setConfigLabel(cfg.getDisplayName() != null ? cfg.getDisplayName() : cfg.getProvider());
            }
        }
        return vo;
    }

    public Result<AgentSessionVO> createSession(Map<String, Object> body) {
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
    public Result<AgentSessionVO> updateSession(Long id, Map<String, Object> body) {
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

    public Result<Void> deleteSession(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!persistenceService.deleteSession(id, userId)) {
            return Result.error("会话不存在或无权访问");
        }
        return Result.success(null);
    }

    public Result<List<AgentMessageVO>> getMessages(Long id) {
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
    public Result<Map<String, Object>> pageMessages(
            Long id,
            long size,
            Long beforeId) {
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

    public Result<AgentStatsVO> stats(int rangeHours) {
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

    private String truncate(String text, int max) {
        return text == null ? null : text.substring(0, Math.min(max, text.length()));
    }
}
