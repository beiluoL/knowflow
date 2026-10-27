package com.knowflow.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.utils.JwtUtils;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real AiService + CapabilityExecutor + localhost HTTP provider. No real model or paid request. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local")
@TestPropertySource(properties={"knowflow.datasource.h2.url=jdbc:h2:mem:interview-ai-protocol;DB_CLOSE_DELAY=-1;MODE=MySQL","knowflow.datasource.allow-runtime-switch=false","knowflow.exam.timeout-scheduler.enabled=false","knowflow.coding.seed.algorithm.enabled=false","knowflow.ai.capability.enabled=true","knowflow.ai.capability.max-attempts=1","knowflow.ai.capability.circuit-failure-threshold=100","ai.api-key=local-test-placeholder","ai.model=local-protocol","ai.timeout-seconds=1","ai.max-tokens=512"})
class InterviewAiProtocolTest {
 static final MockWebServer provider=new MockWebServer();
 static {try{provider.start(java.net.InetAddress.getLoopbackAddress(),0);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void config(DynamicPropertyRegistry r){r.add("ai.base-url",()->provider.url("/v1").toString().replaceAll("/$",""));}
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired SysUserMapper users; @Autowired JwtUtils jwt;
 String auth,qid,base;
 @BeforeEach void init() throws Exception {var u=new SysUser();u.setUsername("iv-protocol-"+UUID.randomUUID());u.setPassword("test-only");u.setRole("USER");users.insert(u);auth="Bearer "+jwt.generateToken(u.getId(),u.getUsername(),"USER");var s=json.readTree(mvc.perform(post("/api/interview/sessions").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"track\":\"JAVA_BASIC\",\"questionCount\":1}")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");long id=s.path("id").asLong();qid=s.at("/questions/0/id").asText();base="/api/interview/sessions/"+id+"/ai-turns";mvc.perform(put("/api/interview/sessions/"+id+"/answers").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("questionId",qid,"answer","真实回答原文","selfScore",4)))).andExpect(status().isOk());}
 void start()throws Exception{mvc.perform(post(base).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("questionId",qid,"requestId",UUID.randomUUID().toString(),"useAi",true)))).andExpect(status().isOk());}
 com.fasterxml.jackson.databind.JsonNode terminal()throws Exception{for(int i=0;i<300;i++){var rows=json.readTree(mvc.perform(get(base).header("Authorization",auth).param("questionId",qid)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");if(rows.size()>0&&!Set.of("QUEUED","RUNNING").contains(rows.get(0).path("status").asText()))return rows.get(0);Thread.sleep(10);}fail("timeout waiting for task");return null;}
 @Test void realProtocolUsesSavedAnswerAndBoundedOutput()throws Exception{String advice="{\"commentary\":\"补充原理\",\"strengths\":[\"回答清楚\"],\"gaps\":[\"补充边界\"],\"followup\":\"为什么？\",\"citations\":[\"真实回答原文\"]}";provider.enqueue(new MockResponse().setHeader("Content-Type","application/json").setBody(json.writeValueAsString(Map.of("choices",List.of(Map.of("message",Map.of("content",advice),"finish_reason","stop")),"usage",Map.of("total_tokens",45)))));start();assertEquals("SUCCEEDED",terminal().path("status").asText());var request=provider.takeRequest(2,TimeUnit.SECONDS);assertNotNull(request);assertEquals("/v1/chat/completions",request.getPath());var body=json.readTree(request.getBody().readUtf8());assertEquals(512,body.path("max_tokens").asInt());assertTrue(body.at("/messages/1/content").asText().contains("真实回答原文"));assertFalse(body.at("/messages/1/content").asText().contains("selfScore"));}
 @Test void httpFailuresAreClassifiedWithoutForgedAdvice()throws Exception{for(int status:new int[]{401,429,500}){provider.enqueue(new MockResponse().setResponseCode(status).setBody("{}"));start();var turn=terminal();assertEquals("FAILED",turn.path("status").asText());assertTrue(turn.path("result").isNull());assertTrue(turn.path("error").asText().contains(status==401?"INVALID_KEY":status==429?"RATE_LIMITED":"PROVIDER_ERROR"));assertNotNull(provider.takeRequest(2,TimeUnit.SECONDS));if(status!=500)init();}}
 @Test void timeoutFallsBackWithoutChangingSelfScore()throws Exception{provider.enqueue(new MockResponse().setBody("{}").setHeadersDelay(2,TimeUnit.SECONDS));start();var turn=terminal();assertEquals("FAILED",turn.path("status").asText());assertTrue(turn.path("error").asText().contains("TIMEOUT"));assertNotNull(provider.takeRequest(2,TimeUnit.SECONDS));}
 @AfterAll static void close()throws Exception{provider.shutdown();}
}
