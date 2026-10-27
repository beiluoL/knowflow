package com.knowflow.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.capability.AiCapabilityProperties;
import com.knowflow.dto.ChatSendDTO;
import com.knowflow.entity.ChatConversation;
import com.knowflow.mapper.*;
import com.knowflow.service.impl.ChatServiceImpl;
import java.io.Serializable;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatKnowledgeSwitchTest {
 @Test void disabledKnowledgeNeverRetrievesInEitherTransport() throws Exception {
  var chunks=mock(DocChunkService.class);var ai=mock(AiService.class);var messages=mock(ChatMessageMapper.class);
  var capability=new AiCapabilityProperties();capability.setEnabled(true);
  var conv=new ChatConversation();conv.setId(1L);conv.setUserId(7L);conv.setMessageCount(0);
  var service=new ChatServiceImpl(messages,ai,capability,mock(DocDocumentMapper.class),chunks,mock(LearningEventService.class)) {
   @Override public ChatConversation getById(Serializable id){return conv;}
   @Override public boolean updateById(ChatConversation value){return true;}
  };
  when(ai.chat(anyString(),anyList(),any(),eq(7L))).thenReturn("answer");
  var json=new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"baseMapper",mock(ChatConversationMapper.class));
  var dto=json.readValue("{\"conversationId\":1,\"content\":\"private question\",\"useKnowledgeBase\":false}",ChatSendDTO.class);
  service.streamSend(dto,7L);service.awaitSseTasks(3000);service.sendMessage(dto,7L);
  verifyNoInteractions(chunks);
 }
 @Test void absentKnowledgeFlagKeepsExistingRetrieval() {
  var chunks=mock(DocChunkService.class);when(chunks.searchSimilarTraced(anyString(),eq(5),eq(7L),any())).thenReturn(new DocChunkService.SearchOutcome(java.util.List.of(),null));
  var capability=new AiCapabilityProperties();capability.setEnabled(true);var conv=new ChatConversation();conv.setId(1L);conv.setUserId(7L);
  var service=new ChatServiceImpl(mock(ChatMessageMapper.class),mock(AiService.class),capability,mock(DocDocumentMapper.class),chunks,mock(LearningEventService.class)) {
   @Override public ChatConversation getById(Serializable id){return conv;}
  };
  org.springframework.test.util.ReflectionTestUtils.setField(service,"baseMapper",mock(ChatConversationMapper.class));
  var dto=new ChatSendDTO();dto.setConversationId(1L);dto.setContent("question");service.streamSend(dto,7L);service.awaitSseTasks(3000);
  verify(chunks).searchSimilarTraced(eq("question"),eq(5),eq(7L),any());
 }
}
