package com.knowflow.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.AiService;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local")
@TestPropertySource(properties={"knowflow.datasource.h2.url=jdbc:h2:mem:interview-ai;DB_CLOSE_DELAY=-1;MODE=MySQL","knowflow.datasource.allow-runtime-switch=false","knowflow.exam.timeout-scheduler.enabled=false","knowflow.coding.seed.algorithm.enabled=false","knowflow.ai.capability.enabled=true"})
class InterviewAiIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired SysUserMapper users; @Autowired JwtUtils jwt;
 @MockBean com.knowflow.service.impl.AiServiceImpl ai;
 @Autowired com.knowflow.ai.capability.AiCapabilityProperties capability;
 String owner,other,qid,base; long session;
 @BeforeEach void init() throws Exception {
  reset(ai);when(ai.isConfigured(anyLong())).thenReturn(true);
  when(ai.complete(anyString(),anyString(),org.mockito.ArgumentMatchers.<String>isNull(),anyLong())).thenReturn("{\"commentary\":\"建议补充原理\",\"strengths\":[\"表达明确\"],\"gaps\":[\"补充边界\"],\"followup\":\"为什么选择该方案？\",\"citations\":[\"我的真实回答\"]}");
  owner=actor();other=actor();
  var s=json.readTree(mvc.perform(post("/api/interview/sessions").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content("{\"track\":\"JAVA_BASIC\",\"questionCount\":1}")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
  session=s.path("id").asLong();qid=s.at("/questions/0/id").asText();base="/api/interview/sessions/"+session+"/ai-turns";
  mvc.perform(put("/api/interview/sessions/"+session+"/answers").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("questionId",qid,"answer","我的真实回答","selfScore",3)))).andExpect(status().isOk());
 }
 String actor(){var u=new SysUser();u.setUsername("iv-ai-"+UUID.randomUUID());u.setPassword("test-only");u.setRole("USER");users.insert(u);return "Bearer "+jwt.generateToken(u.getId(),u.getUsername(),"USER");}
 String request(String id,boolean enabled)throws Exception{return json.writeValueAsString(Map.of("questionId",qid,"requestId",id,"useAi",enabled,"followupAnswer","补充真实解释"));}
 String submit(String id)throws Exception{return mvc.perform(post(base).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(request(id,true))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);}
 com.fasterxml.jackson.databind.JsonNode terminal()throws Exception{for(int i=0;i<200;i++){var rows=json.readTree(mvc.perform(get(base).param("questionId",qid).header("Authorization",owner)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");if(rows.size()>0&&!Set.of("QUEUED","RUNNING").contains(rows.get(rows.size()-1).path("status").asText()))return rows.get(rows.size()-1);Thread.sleep(10);}fail("task did not terminate");return null;}
 @Test void ownershipAndExplicitDisabledNeverInvokeAi()throws Exception{
  mvc.perform(get(base)).andExpect(status().isUnauthorized());mvc.perform(get(base).param("questionId",qid).header("Authorization",other)).andExpect(status().isNotFound());
  mvc.perform(post(base).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(request(UUID.randomUUID().toString(),false))).andExpect(jsonPath("$.data.mode").value("BASIC")).andExpect(jsonPath("$.data.referencePoints").isArray());
  verify(ai,never()).complete(anyString(),anyString(),org.mockito.ArgumentMatchers.<String>any(),anyLong());
 }
 @Test void duplicateRequestDoesNotRecallAndThreeRoundsOnly()throws Exception{
  String id=UUID.randomUUID().toString();submit(id);assertEquals("SUCCEEDED",terminal().path("status").asText());submit(id);verify(ai,times(1)).complete(anyString(),anyString(),org.mockito.ArgumentMatchers.<String>isNull(),anyLong());
  submit(UUID.randomUUID().toString());terminal();submit(UUID.randomUUID().toString());terminal();
  mvc.perform(post(base).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(request(UUID.randomUUID().toString(),true))).andExpect(status().isConflict());
  var s=json.readTree(mvc.perform(get("/api/interview/sessions/"+session).header("Authorization",owner)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");assertEquals("我的真实回答",s.at("/answers/0/answer").asText());assertEquals(3,s.at("/answers/0/selfScore").asInt());assertTrue(s.path("avgSelfScore").isNull());
 }
 @Test void forgedCitationAndMalformedOutputDoNotBecomeAdvice()throws Exception{
  when(ai.complete(anyString(),anyString(),org.mockito.ArgumentMatchers.<String>isNull(),anyLong())).thenReturn("{\"commentary\":\"项目经历\",\"strengths\":[],\"gaps\":[],\"followup\":\"追问\",\"citations\":[\"虚构项目\"]}");submit(UUID.randomUUID().toString());assertEquals("FAILED",terminal().path("status").asText());
  when(ai.complete(anyString(),anyString(),org.mockito.ArgumentMatchers.<String>isNull(),anyLong())).thenReturn("not json");submit(UUID.randomUUID().toString());assertEquals("FAILED",terminal().path("status").asText());
 }
 @Test void cancellationWinsLateResult()throws Exception{
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  when(ai.complete(anyString(),anyString(),org.mockito.ArgumentMatchers.<String>isNull(),anyLong())).thenAnswer(call->{entered.countDown();try{release.await(2,TimeUnit.SECONDS);}catch(InterruptedException ignored){release.await(2,TimeUnit.SECONDS);}return "{\"commentary\":\"真实迟到建议\",\"strengths\":[],\"gaps\":[],\"followup\":\"为什么？\",\"citations\":[\"我的真实回答\"]}";});
  var turn=json.readTree(submit(UUID.randomUUID().toString())).at("/data/turn/id");assertTrue(entered.await(2,TimeUnit.SECONDS));
  mvc.perform(post(base+"/"+turn.asLong()+"/cancel").header("Authorization",other)).andExpect(status().isNotFound());
  mvc.perform(post(base+"/"+turn.asLong()+"/cancel").header("Authorization",owner)).andExpect(status().isOk());release.countDown();Thread.sleep(100);assertEquals("CANCELLED",terminal().path("status").asText());
 }
 @Test void systemDisabledAndUnconfiguredAreBasicWithoutReservation()throws Exception{
  capability.setEnabled(false);try{mvc.perform(post(base).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(request(UUID.randomUUID().toString(),true))).andExpect(jsonPath("$.data.mode").value("BASIC"));}finally{capability.setEnabled(true);}
  when(ai.isConfigured(anyLong())).thenReturn(false);mvc.perform(post(base).header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content(request(UUID.randomUUID().toString(),true))).andExpect(jsonPath("$.data.mode").value("BASIC"));
  mvc.perform(get(base).header("Authorization",owner).param("questionId",qid)).andExpect(jsonPath("$.data").isEmpty());verify(ai,never()).complete(anyString(),anyString(),org.mockito.ArgumentMatchers.<String>any(),anyLong());
 }
 @Test void concurrentDuplicateRequestReservesExactlyOneRound()throws Exception{
  String requestId=UUID.randomUUID().toString();var pool=java.util.concurrent.Executors.newFixedThreadPool(2);try{var gate=new CountDownLatch(1);java.util.concurrent.Callable<String> call=()->{gate.await();return submit(requestId);};var one=pool.submit(call);var two=pool.submit(call);gate.countDown();one.get(3,TimeUnit.SECONDS);two.get(3,TimeUnit.SECONDS);assertEquals("SUCCEEDED",terminal().path("status").asText());mvc.perform(get(base).param("questionId",qid).header("Authorization",owner)).andExpect(jsonPath("$.data.length()").value(1));verify(ai,times(1)).complete(anyString(),anyString(),org.mockito.ArgumentMatchers.<String>isNull(),anyLong());}finally{pool.shutdownNow();}
 }

}
