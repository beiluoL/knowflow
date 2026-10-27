package com.knowflow.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.service.AiService;
import com.knowflow.service.ChatService;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.config-file=/tmp/knowflow-chat-title-v2-test-no-config.json","knowflow.datasource.type=h2","knowflow.datasource.h2.url=jdbc:h2:mem:chat_title_v2;DB_CLOSE_DELAY=-1;MODE=MySQL","knowflow.ai.capability.enabled=true"})
class ChatConversationTitleV2Test {
 @Autowired MockMvc http;@Autowired JwtUtils jwt;@Autowired ObjectMapper json;@Autowired ChatService service;
 /** 流式生成在请求线程之外执行，断言前需等待异步收尾，避免跨用例串行干扰。 */
 @Autowired com.knowflow.service.impl.ChatServiceImpl chat;
 @SpyBean AiService ai;
 final long owner=982001,other=982002;
 String token(long user,String role){return "Bearer "+jwt.generateToken(user,"chat-v2",role);}
 Long conversation(){return service.createConversation(owner,"新对话").getId();}
 void patchTitle(Long id,String title,String auth,int status)throws Exception{http.perform(patch("/api/chat/conversations/"+id).header("Authorization",auth).contentType("application/json").content(json.writeValueAsString(java.util.Map.of("title",title)))).andExpect(status().is(status)).andExpect(jsonPath("$.code").value(status));}
 @Test void ownerRenamePersistsAndAcceptsAnIdenticalRetry()throws Exception{Long id=conversation();patchTitle(id,"  并发学习  ",token(owner,"USER"),200);patchTitle(id,"并发学习",token(owner,"USER"),200);assertEquals("并发学习",service.getById(id).getTitle());}
 @Test void otherAndAdminCannotRenamePrivateConversation()throws Exception{Long id=conversation();for(String role:new String[]{"USER","ADMIN"})patchTitle(id,"intrusion",token(other,role),404);assertEquals("新对话",service.getById(id).getTitle());}
 @Test void anonymousRenameRequiresAuthentication()throws Exception{Long id=conversation();http.perform(patch("/api/chat/conversations/"+id).contentType("application/json").content("{\"title\":\"x\"}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));}
 @Test void invalidOrDeletedRenameDoesNotMutate()throws Exception{Long id=conversation();patchTitle(id,"  ",token(owner,"USER"),400);patchTitle(id,"x".repeat(201),token(owner,"USER"),400);service.deleteConversation(id,owner);patchTitle(id,"deleted",token(owner,"USER"),404);}
 @Test void firstQuestionBecomesServerTitleInStream()throws Exception{Long id=conversation();http.perform(post("/api/chat/stream").header("Authorization",token(owner,"USER")).contentType("application/json").content("{\"conversationId\":"+id+",\"content\":\"解释 Java CAS\",\"useKnowledgeBase\":false}")).andExpect(status().isOk());chat.awaitSseTasks(3000);assertEquals("解释 Java CAS",service.getById(id).getTitle());}
 @Test void firstLearningInstructionBecomesServerTitle()throws Exception{Long id=conversation();http.perform(post("/api/chat/learning-actions").header("Authorization",token(owner,"USER")).contentType("application/json").content("{\"conversationId\":"+id+",\"message\":\"生成今天的学习任务，1小时\",\"preferences\":{\"useAi\":false}}")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));assertEquals("生成今天的学习任务，1小时",service.getById(id).getTitle());}
 @Test void lateStreamCompletionCannotOverwriteManualRename()throws Exception{
  AtomicReference<AiService.StreamCompletionCallback> completion=new AtomicReference<>();
  doAnswer(call->{completion.set(call.getArgument(5));return null;}).when(ai).streamChat(anyString(),anyString(),eq(owner),nullable(Long.class),any(),any(AiService.StreamCompletionCallback.class),nullable(Double.class),nullable(Integer.class),nullable(Double.class));
  Long id=conversation();http.perform(post("/api/chat/stream").header("Authorization",token(owner,"USER")).contentType("application/json").content("{\"conversationId\":"+id+",\"content\":\"question\",\"useKnowledgeBase\":false}")).andExpect(status().isOk());chat.awaitSseTasks(3000);
  patchTitle(id,"我的手工标题",token(owner,"USER"),200);assertNotNull(completion.get());completion.get().onComplete("late reply",true);assertEquals("我的手工标题",service.getById(id).getTitle());assertEquals(2,service.getById(id).getMessageCount());
 }
}
