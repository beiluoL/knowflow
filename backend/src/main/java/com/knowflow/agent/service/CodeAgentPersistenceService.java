package com.knowflow.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.agent.entity.AgentSession;
import com.knowflow.agent.entity.AgentMessage;
import com.knowflow.agent.entity.AgentCallLog;
import com.knowflow.agent.mapper.AgentSessionMapper;
import com.knowflow.agent.mapper.AgentMessageMapper;
import com.knowflow.agent.mapper.AgentCallLogMapper;
import com.knowflow.exception.BusinessException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 执行短持久化事务；模型和工具始终在这些事务之外运行。 */
@Service
@RequiredArgsConstructor
public class CodeAgentPersistenceService {
    private final AgentSessionMapper sessions;
    private final AgentMessageMapper messages;
    private final AgentCallLogMapper logs;

    /** 创建或检查所属会话，原子保存输入轮次与统计。
     * @param userId 异步执行前捕获的认证请求身份
     * @return 会话标识；指定会话不可访问时返回 null */
    @Transactional(rollbackFor = Exception.class)
    public Long startTurn(Long sessionId, Long userId, Long configId, String title,
                          String content, String filePath, boolean agentMode) {
        AgentSession session;
        if (sessionId == null) {
            session = new AgentSession();
            session.setUserId(userId);
            session.setConfigId(configId);
            session.setTitle(title != null && !title.isBlank() ? title : truncate(content, 30));
            session.setMessageCount(0);
            session.setLastMessage(truncate(content, 200));
            if (agentMode) session.setAgentMode("agent");
            requireSingleWrite(sessions.insert(session));
        } else {
            session = ownedSession(sessionId, userId);
            if (session == null) return null;
        }
        AgentMessage message = new AgentMessage();
        message.setSessionId(session.getId());
        message.setUserId(userId);
        message.setRole("user");
        message.setContent(content);
        message.setFilePath(filePath);
        message.setMessageType("normal");
        message.setIsError(0);
        message.setTokenCount(tokens(content));
        requireSingleWrite(messages.insert(message));
        updateStats(session, content);
        return session.getId();
    }

    /** 外部模型执行结束后，原子保存流完成状态与计量；运行时工具消息已由 AgentRuntimeService 持久化，此处不重复保存。 */
    @Transactional(rollbackFor = Exception.class)
    public void completeTurn(AgentMessage assistant, AgentCallLog callLog, String content) {
        AgentSession session = ownedSession(callLog.getSessionId(), callLog.getUserId());
        if (session == null) throw new BusinessException(404, "会话不存在或无权访问");
        if (assistant != null) {
            if (!Objects.equals(assistant.getSessionId(), callLog.getSessionId())
                    || !Objects.equals(assistant.getUserId(), callLog.getUserId())) {
                throw new BusinessException(409, "消息与调用日志的会话归属不一致");
            }
            requireSingleWrite(messages.insert(assistant));
        }
        updateStats(session, content);
        requireSingleWrite(logs.insert(callLog));
    }

    /** 将所属会话及其消息的逻辑删除作为一个可回滚单元执行。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSession(Long sessionId, Long userId) {
        if (ownedSession(sessionId, userId) == null) return false;
        requireSingleWrite(sessions.delete(new LambdaQueryWrapper<AgentSession>().eq(AgentSession::getId, sessionId)
                .eq(AgentSession::getUserId, userId)));
        messages.delete(new LambdaQueryWrapper<AgentMessage>().eq(AgentMessage::getSessionId, sessionId)
                .eq(AgentMessage::getUserId, userId));
        return true;
    }

    private AgentSession ownedSession(Long sessionId, Long userId) {
        return sessions.selectOne(new LambdaQueryWrapper<AgentSession>().eq(AgentSession::getId, sessionId)
                .eq(AgentSession::getUserId, userId).last("FOR UPDATE"));
    }

    private void updateStats(AgentSession session, String content) {
        Long count = messages.selectCount(new LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, session.getId()).eq(AgentMessage::getUserId, session.getUserId()));
        session.setMessageCount(count.intValue());
        session.setLastMessage(truncate(content, 200));
        requireSingleWrite(sessions.update(session, new LambdaQueryWrapper<AgentSession>().eq(AgentSession::getId, session.getId())
                .eq(AgentSession::getUserId, session.getUserId())));
    }

    private void requireSingleWrite(int affectedRows) {
        if (affectedRows != 1) throw new BusinessException(409, "会话状态已变化，请重试");
    }

    private int tokens(String content) {
        return content == null ? 0 : (int) Math.ceil(content.length() / 2.5);
    }

    private String truncate(String content, int limit) {
        return content == null || content.length() <= limit ? content : content.substring(0, limit);
    }
}
