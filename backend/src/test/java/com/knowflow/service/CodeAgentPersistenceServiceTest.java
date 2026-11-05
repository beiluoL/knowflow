package com.knowflow.service;

import com.knowflow.agent.service.CodeAgentPersistenceService;


import com.knowflow.agent.entity.AgentCallLog;
import com.knowflow.agent.entity.AgentMessage;
import com.knowflow.agent.entity.AgentSession;
import com.knowflow.exception.BusinessException;
import com.knowflow.agent.mapper.AgentSessionMapper;
import com.knowflow.agent.mapper.AgentMessageMapper;
import com.knowflow.agent.mapper.AgentCallLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CodeAgentPersistenceServiceTest {
    private AgentSessionMapper sessions;
    private AgentMessageMapper messages;
    private AgentCallLogMapper logs;
    private CodeAgentPersistenceService service;

    @BeforeEach
    void setup() {
        sessions = mock(AgentSessionMapper.class);
        messages = mock(AgentMessageMapper.class);
        logs = mock(AgentCallLogMapper.class);
        service = new CodeAgentPersistenceService(sessions, messages, logs);
    }

    @Test
    void zeroSessionInsertStopsBeforeMessageWrite() {
        assertThrows(BusinessException.class, () -> service.startTurn(null, 1L, null, null, "hello", null, false));
        verifyNoInteractions(messages, logs);
    }

    @Test
    void inaccessibleSessionHasNoSideEffects() {
        assertNull(service.startTurn(2L, 1L, null, null, "hello", null, false));
        assertFalse(service.deleteSession(2L, 1L));
        verifyNoInteractions(messages, logs);
        verify(sessions, never()).insert(any(AgentSession.class));
    }

    @Test
    void completionRejectsAssistantIdentityMismatchBeforeWrites() {
        stubOwnedSession();
        AgentMessage assistant = assistant();
        assistant.setUserId(9L);
        assertThrows(BusinessException.class, () -> service.completeTurn(assistant, callLog(), "reply"));
        verifyNoInteractions(messages, logs);
    }

    @Test
    void completionSavesMessageCountAndLog() {
        stubOwnedSession();
        when(messages.insert(any(AgentMessage.class))).thenReturn(1);
        when(messages.selectCount(any())).thenReturn(2L);
        when(sessions.update(any(AgentSession.class), any())).thenReturn(1);
        when(logs.insert(any(AgentCallLog.class))).thenReturn(1);
        service.completeTurn(assistant(), callLog(), "reply");
        verify(messages).insert(any(AgentMessage.class));
        verify(sessions).update(argThat(session -> session.getMessageCount() == 2
                && "reply".equals(session.getLastMessage())), any());
        verify(logs).insert(any(AgentCallLog.class));
    }

    private void stubOwnedSession() {
        AgentSession session = new AgentSession();
        session.setId(2L);
        session.setUserId(1L);
        when(sessions.selectOne(any())).thenReturn(session);
    }

    private AgentMessage assistant() {
        AgentMessage assistant = new AgentMessage();
        assistant.setSessionId(2L);
        assistant.setUserId(1L);
        return assistant;
    }

    private AgentCallLog callLog() {
        AgentCallLog callLog = new AgentCallLog();
        callLog.setSessionId(2L);
        callLog.setUserId(1L);
        return callLog;
    }
}
