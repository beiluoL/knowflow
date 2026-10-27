package com.knowflow.service;

import com.knowflow.ai.capability.AiCapabilityProperties;
import com.knowflow.dto.ChatRegenerateDTO;
import com.knowflow.dto.ChatSendDTO;
import com.knowflow.entity.ChatConversation;
import com.knowflow.entity.ChatMessage;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.ChatConversationMapper;
import com.knowflow.mapper.ChatMessageMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.service.impl.ChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 消息编辑 / 引用上下文 / 重新生成的服务层行为。 */
class ChatMessageEditRegenerateTest {

    private ChatMessageMapper messages;
    private AiService ai;
    private ChatServiceImpl service;
    private ChatConversation conversation;

    @BeforeEach
    void setUp() {
        messages = mock(ChatMessageMapper.class);
        ai = mock(AiService.class);
        conversation = new ChatConversation();
        conversation.setId(1L);
        conversation.setUserId(7L);
        conversation.setMessageCount(2);
        AiCapabilityProperties capability = new AiCapabilityProperties();
        capability.setEnabled(true);
        service = new ChatServiceImpl(messages, ai, capability, mock(DocDocumentMapper.class),
                mock(DocChunkService.class), mock(LearningEventService.class)) {
            @Override
            public ChatConversation getById(Serializable id) {
                return conversation;
            }
        };
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", mock(ChatConversationMapper.class));
    }

    private ChatMessage message(long id, long userId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setConversationId(1L);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    @Test
    void updateMessageRejectsBlankAssistantAndForeignMessages() {
        ChatMessage own = message(10L, 7L, "user", "old question");
        when(messages.selectById(10L)).thenReturn(own);
        assertThrows(BusinessException.class, () -> service.updateMessage(10L, 7L, "  "));

        ChatMessage assistant = message(11L, 7L, "assistant", "answer");
        when(messages.selectById(11L)).thenReturn(assistant);
        BusinessException notUser = assertThrows(BusinessException.class, () -> service.updateMessage(11L, 7L, "x"));
        assertEquals(400, notUser.getCode());

        ChatMessage foreign = message(12L, 8L, "user", "other");
        when(messages.selectById(12L)).thenReturn(foreign);
        BusinessException notOwner = assertThrows(BusinessException.class, () -> service.updateMessage(12L, 7L, "x"));
        assertEquals(404, notOwner.getCode());
    }

    @Test
    void updateMessageSavesContentAndTruncatesLaterBranch() {
        ChatMessage own = message(10L, 7L, "user", "old question");
        when(messages.selectById(10L)).thenReturn(own);

        var updated = service.updateMessage(10L, 7L, " new question ");

        assertEquals("new question", updated.getContent());
        assertEquals("new question", own.getContent());
        verify(messages).updateById(own);
        verify(messages).delete(any());
    }

    @Test
    void quoteContextIsOnlyAcceptedForOwnedMessagesOfTheSameConversation() {
        ChatMessage quoted = message(20L, 7L, "user", "quoted paragraph");
        when(messages.selectById(20L)).thenReturn(quoted);
        when(ai.chat(anyString(), anyList(), isNull(), eq(7L))).thenReturn("answer");

        ChatSendDTO dto = new ChatSendDTO();
        dto.setConversationId(1L);
        dto.setContent("new question");
        dto.setQuoteMessageId(20L);
        dto.setUseKnowledgeBase(false);
        service.sendMessage(dto, 7L);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).chat(prompt.capture(), anyList(), isNull(), eq(7L));
        assertTrue(prompt.getValue().contains("quoted paragraph"));
        assertTrue(prompt.getValue().contains("new question"));
    }

    @Test
    void quoteContextRejectsForeignMessageBeforeCallingTheModel() {
        ChatMessage foreign = message(21L, 8L, "user", "private paragraph");
        when(messages.selectById(21L)).thenReturn(foreign);

        ChatSendDTO dto = new ChatSendDTO();
        dto.setConversationId(1L);
        dto.setContent("question");
        dto.setQuoteMessageId(21L);
        dto.setUseKnowledgeBase(false);

        BusinessException error = assertThrows(BusinessException.class, () -> service.sendMessage(dto, 7L));
        assertEquals(404, error.getCode());
        verify(ai, never()).chat(anyString(), anyList(), any(), any());
        // 引用无效时不应先落库用户消息，避免留下孤立的用户气泡
        verify(messages, never()).insert(any(ChatMessage.class));
    }

    @Test
    void regenerateRejectsForeignMessagesAndMissingUserPrompt() {
        ChatMessage foreign = message(30L, 8L, "assistant", "answer");
        when(messages.selectById(30L)).thenReturn(foreign);
        BusinessException notOwner = assertThrows(BusinessException.class,
                () -> service.regenerate(30L, new ChatRegenerateDTO(), 7L));
        assertEquals(404, notOwner.getCode());

        ChatMessage noPrompt = message(31L, 7L, "assistant", "answer");
        when(messages.selectById(31L)).thenReturn(noPrompt);
        when(messages.selectList(any())).thenReturn(List.of(noPrompt));
        assertThrows(BusinessException.class, () -> service.regenerate(31L, new ChatRegenerateDTO(), 7L));
        verify(ai, never()).streamChat(anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void regenerateProducesANewAssistantMessageFromTheModelCallback() {
        ChatMessage user = message(40L, 7L, "user", "explain threads");
        ChatMessage assistant = message(41L, 7L, "assistant", "old answer");
        when(messages.selectById(41L)).thenReturn(assistant);
        when(messages.selectList(any())).thenReturn(List.of(user, assistant));

        ChatRegenerateDTO options = new ChatRegenerateDTO();
        options.setUseKnowledgeBase(false);
        service.regenerate(41L, options, 7L);
        // 生成过程离开请求线程执行（保证 SSE 逐条刷出），先等待异步调用落到模型层再断言
        service.awaitSseTasks(3000);

        ArgumentCaptor<AiService.StreamCompletionCallback> callback = ArgumentCaptor.forClass(AiService.StreamCompletionCallback.class);
        verify(ai).streamChat(anyString(), eq("explain threads"), eq(7L), any(), any(), callback.capture(),
                any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), isNull());
        callback.getValue().onComplete("new answer", true);

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages, org.mockito.Mockito.atLeastOnce()).insert(saved.capture());
        ChatMessage inserted = saved.getValue();
        assertEquals("assistant", inserted.getRole());
        assertEquals("new answer", inserted.getContent());
    }
}
