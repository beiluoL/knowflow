package com.knowflow.controller;

import com.knowflow.agent.controller.CodeAgentController;


import com.knowflow.agent.entity.AgentSession;
import com.knowflow.agent.mapper.AgentSessionMapper;
import com.knowflow.agent.mapper.AgentMessageMapper;
import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证编程智能体消息写入和会话删除的事务回滚。
 */
@SpringBootTest
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties="knowflow.datasource.h2.url=jdbc:h2:mem:code_agent_batch1;DB_CLOSE_DELAY=-1;MODE=MySQL")
class CodeAgentTransactionTest {
    @Autowired CodeAgentController controller;
    @Autowired AgentSessionMapper sessions;
    @SpyBean AgentMessageMapper messages;

    @Test
    void userMessageFailureRollsBackNewSession() {
        long owner = 991882L;
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, "", List.of()));
        doThrow(new IllegalStateException("injected message insert failure")).when(messages).insert(any(com.knowflow.agent.entity.AgentMessage.class));
        try {
            assertThrows(RuntimeException.class, () -> controller.chat(java.util.Map.of("content", "rollback new session")));
            assertEquals(0L, sessions.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentSession>()
                    .eq(AgentSession::getUserId, owner)));
        } finally {
            SecurityContextHolder.clearContext();
            reset(messages);
        }
    }

    @Test
    void messageDeleteFailureRollsBackSessionDeletion() {
        long owner = 991881L;
        AgentSession session = new AgentSession();
        session.setUserId(owner);
        session.setTitle("rollback");
        session.setMessageCount(0);
        sessions.insert(session);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, "", List.of()));
        doThrow(new IllegalStateException("injected message delete failure")).when(messages).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        try {
            assertThrows(RuntimeException.class, () -> controller.deleteSession(session.getId()));
            assertNotNull(sessions.selectById(session.getId()), "session deletion must roll back with messages");
        } finally {
            SecurityContextHolder.clearContext();
            reset(messages);
        }
    }
}
