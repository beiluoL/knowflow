package com.knowflow.interview.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.service.AiService;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实安全链、HTTP契约与隔离数据库断言；模型使用替身，仅验证协议而非声称真实AI验收。 */
@SpringBootTest(properties={
 // 本类AiServiceImpl为@MockBean，必须允许其受控worker进入；不会调用真实模型。
 "knowflow.ai.capability.enabled=true",
 "knowflow.datasource.h2.url=jdbc:h2:mem:phase4i_http;DB_CLOSE_DELAY=-1;MODE=MySQL",
 "knowflow.datasource.config-file=/tmp/phase4i-http-no-overrides.json",
 "knowflow.seed.development-account.enabled=false", "knowflow.datasource.allow-runtime-switch=false"})
@AutoConfigureMockMvc @ActiveProfiles("local")
class InterviewV2HttpTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired JwtUtils jwt;
 @MockBean com.knowflow.service.impl.AiServiceImpl ai;
 @BeforeEach void configure(){when(ai.isConfigured(anyLong())).thenReturn(false);}
 String token(long uid){return "Bearer "+jwt.generateToken(uid,"phase4i-"+uid,uid==1?"ADMIN":"USER");}
 String key(){return UUID.randomUUID().toString();}
 JsonNode postJson(String path,Object body,long uid,int status)throws Exception{
  var result=mvc.perform(post(path).header("Authorization",token(uid)).contentType("application/json").content(json.writeValueAsString(body)))
   .andExpect(status().is(status)).andExpect(jsonPath("$.code").value(status)).andReturn();
  return json.readTree(result.getResponse().getContentAsString()).path("data");
 }
 JsonNode create(String request)throws Exception{return postJson("/api/interview/v2/sessions",Map.of("requestId",request,"track","JAVA_BASIC","mode","STRICT","role","JAVA_AI","questionCount",1,"maxDepth",1,"durationMinutes",15,"difficulty","SENIOR"),1,200);}
 JsonNode state(long sid,long uid,int status)throws Exception{
  var r=mvc.perform(get("/api/interview/v2/sessions/"+sid).header("Authorization",token(uid)))
   .andExpect(status().is(status)).andExpect(jsonPath("$.code").value(status)).andReturn();
  return json.readTree(r.getResponse().getContentAsString()).path("data");
 }
 JsonNode settled(long sid)throws Exception{for(int i=0;i<150;i++){JsonNode s=state(sid,1,200);if(!s.path("state").asText().equals("EVALUATING"))return s;Thread.sleep(20);}throw new AssertionError("evaluation did not settle");}
 @Test void v2StartRestoresAndDeduplicatesWithoutLeakingStrictReference()throws Exception{
  String id=key();JsonNode s=create(id),again=create(id);assertEquals(s.path("sessionId"),again.path("sessionId"));
  long sid=s.path("sessionId").asLong();assertTrue(sid>0);assertEquals("ASKING",s.path("state").asText());
  mvc.perform(get("/api/interview/sessions/"+sid).header("Authorization",token(1))).andExpect(status().isOk())
   .andExpect(jsonPath("$.data.v2").value(true)).andExpect(jsonPath("$.data.questions[0].referencePoints").isEmpty()).andExpect(jsonPath("$.data.questions[0].rubric").doesNotExist());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mock_interview_v2_state WHERE session_id=?",Integer.class,sid));
 }
 @Test void anonymousAndCrossUserAndAdminDoNotBypassOwnership()throws Exception{
  mvc.perform(post("/api/interview/v2/sessions").contentType("application/json").content("{}"))
   .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
  long sid=create(key()).path("sessionId").asLong();state(sid,2,404);
  // 反向校验：管理员也不能读取普通用户的私有面试。
  var own=postJson("/api/interview/v2/sessions",Map.of("requestId",key(),"track","RAG","mode","STRICT","role","JAVA_AI"),2,200);
  state(own.path("sessionId").asLong(),1,404);
 }
 @Test void confirmedAnswerUsesVersionAndIdempotencyThenFallsBackWithoutFakeScore()throws Exception{
  JsonNode s=create(key());long sid=s.path("sessionId").asLong();String request=key();
  Map<String,Object> answer=new HashMap<>(Map.of("version",s.path("version").asLong(),"requestId",request,"answer","我会先检查实际事务边界，再验证回滚和重复请求。"));
  postJson("/api/interview/v2/sessions/"+sid+"/answers",answer,1,200);
  s=settled(sid);assertEquals("FOLLOW_UP",s.path("state").asText());assertEquals(1,s.path("turns").size());
  assertTrue(s.path("turns").get(0).path("evaluation").isMissingNode()||s.path("turns").get(0).path("evaluation").isNull());
  postJson("/api/interview/v2/sessions/"+sid+"/answers",answer,1,200);
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mock_interview_v2_turn WHERE session_id=?",Integer.class,sid));
  answer.put("answer","不同内容不可复用同一个请求标识");postJson("/api/interview/v2/sessions/"+sid+"/answers",answer,1,409);
  answer.put("requestId",key());postJson("/api/interview/v2/sessions/"+sid+"/answers",answer,1,409);
  postJson("/api/interview/sessions/"+sid+"/finish",Map.of(),1,409);
 }
 @Test void pauseResumeAndFinishAreServerControlledAndIdempotent()throws Exception{
  JsonNode s=create(key());long sid=s.path("sessionId").asLong();String path="/api/interview/v2/sessions/"+sid;
  Map<String,Object> pause=Map.of("version",s.path("version").asLong(),"requestId",key());
  s=postJson(path+"/pause",pause,1,200);assertEquals("PAUSED",s.path("state").asText());
  postJson(path+"/pause",pause,1,200);
  postJson(path+"/answers",Map.of("version",s.path("version").asLong(),"requestId",key(),"answer","暂停时不可提交"),1,409);
  s=postJson(path+"/resume",Map.of("version",s.path("version").asLong(),"requestId",key()),1,200);
  Map<String,Object> finish=Map.of("version",s.path("version").asLong(),"requestId",key());
  s=postJson(path+"/finish",finish,1,200);assertEquals("COMPLETED",s.path("state").asText());
  assertTrue(s.path("report").path("overallScore").isNull());postJson(path+"/finish",finish,1,200);
  assertEquals("FINISHED",jdbc.queryForObject("SELECT status FROM mock_interview_session WHERE id=?",String.class,sid));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_event WHERE event_type='INTERVIEW_COMPLETED' AND resource_id=? AND user_id=1",Integer.class,sid));
 }
 @Test void unusedRecordingCannotCrossFollowupPromptBoundary()throws Exception{
  JsonNode s=create(key());long sid=s.path("sessionId").asLong();String q=s.path("currentQuestion").path("id").asText(),prompt=s.path("currentQuestion").path("title").asText();
  jdbc.update("INSERT INTO mock_interview_speech(user_id,session_id,question_id,request_id,request_hash,status,raw_text,duration_ms,claimed_duration_ms,provider,v2_version,v2_depth,v2_prompt_hash) VALUES(1,?,?,?,?,'TRANSCRIBED','原始录音',1000,1000,'fixture',?,0,?)",sid,q,key(),"a".repeat(64),s.path("version").asLong(),com.knowflow.interview.speech.SpeechV2Admission.promptHash(prompt));
  long speech=jdbc.queryForObject("SELECT id FROM mock_interview_speech WHERE session_id=?",Long.class,sid);
  postJson("/api/interview/v2/sessions/"+sid+"/answers",Map.of("version",s.path("version").asLong(),"requestId",key(),"answer","先用文字回答主问题"),1,200);
  s=settled(sid);assertEquals("FOLLOW_UP",s.path("state").asText());
  postJson("/api/interview/v2/sessions/"+sid+"/answers",Map.of("version",s.path("version").asLong(),"requestId",key(),"answer","旧录音不能确认到新追问","speechId",speech),1,409);
 }
 @Test void basicV2CannotBypassStateMachineThroughLegacyApis()throws Exception{
  JsonNode s=postJson("/api/interview/v2/sessions",Map.of("requestId",key(),"track","JAVA_BASIC","mode","BASIC","role","Java"),1,200);
  long sid=s.path("sessionId").asLong();String q=s.path("currentQuestion").path("id").asText();
  mvc.perform(put("/api/interview/sessions/"+sid+"/answers").header("Authorization",token(1)).contentType("application/json")
   .content(json.writeValueAsString(Map.of("questionId",q,"answer","必须通过受控确认入口","selfScore",3))))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
  postJson("/api/interview/sessions/"+sid+"/finish",Map.of(),1,409);
  postJson("/api/interview/sessions/"+sid+"/ai-turns",Map.of("questionId",q,"requestId",key(),"useAi",false),1,409);
  assertEquals("ASKING",state(sid,1,200).path("state").asText());
 }
 @Test void deadlineAndDepthAreEnforcedWithoutAwardingMastery()throws Exception{
  JsonNode s=create(key());long sid=s.path("sessionId").asLong();
  jdbc.update("UPDATE mock_interview_v2_state SET deadline=? WHERE session_id=?",java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(1)),sid);
  s=state(sid,1,200);assertEquals("COMPLETED",s.path("state").asText());assertEquals(0,s.path("turns").size());
  assertTrue(s.path("report").path("overallScore").isNull());verify(ai,never()).completeStructured(anyString(),anyString(),org.mockito.ArgumentMatchers.<String>isNull(),anyLong());
 }

 @Test void concurrentDifferentRequestsAtSameVersionStoreExactlyOneTurn()throws Exception{
  JsonNode initial=create(key());long sid=initial.path("sessionId").asLong();
  var gate=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try{
   java.util.List<java.util.concurrent.Future<Integer>> results=new ArrayList<>();
   for(int i=0;i<2;i++){String request=key();results.add(pool.submit(()->{gate.await();return mvc.perform(post("/api/interview/v2/sessions/"+sid+"/answers").header("Authorization",token(1)).contentType("application/json").content(json.writeValueAsString(Map.of("version",initial.path("version").asLong(),"requestId",request,"answer","同一版本只能确认一个回答")))).andReturn().getResponse().getStatus();}));}
   gate.countDown();List<Integer> statuses=new ArrayList<>();for(var result:results)statuses.add(result.get(10,java.util.concurrent.TimeUnit.SECONDS));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
   settled(sid);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mock_interview_v2_turn WHERE session_id=?",Integer.class,sid));
  }finally{pool.shutdownNow();}
 }
 @Test void reportUsesPersistedRubricAndMarksMissingOrMixedEvidence()throws Exception{
  JsonNode s=create(key());long sid=s.path("sessionId").asLong();String path="/api/interview/v2/sessions/"+sid;
  for(int i=0;i<2;i++){postJson(path+"/answers",Map.of("version",s.path("version").asLong(),"requestId",key(),"answer","真实确认的测试回答"),1,200);s=settled(sid);}
  assertEquals("COMPLETED",s.path("state").asText());assertTrue(s.path("report").path("overallScore").isNull());
  Map<String,Integer> weights=Map.of("TECHNICAL_CORRECTNESS",35,"KNOWLEDGE_COVERAGE",25,"ENGINEERING_PRACTICE",20,"EXPRESSION_STRUCTURE",10,"FOLLOWUP_RESPONSE",10);
  // 仅测试库模拟已存模型协议快照，不把这些测试分数用于真实验收。
  Map<String,Object> fixture=new LinkedHashMap<>();fixture.put("weights",weights);fixture.put("rubricVersion","historical-rubric-v1");fixture.put("dimensions",weights.keySet().stream().collect(java.util.stream.Collectors.toMap(k->k,k->80)));fixture.put("citations",List.of("真实确认"));
  jdbc.update("UPDATE mock_interview_v2_turn SET evaluation_json=? WHERE session_id=?",json.writeValueAsString(fixture),sid);
  var report=state(sid,1,200).path("report");assertEquals("historical-rubric-v1",report.path("rubricVersion").asText());assertEquals(80,report.path("overallScore").asDouble());assertFalse(report.path("mixedRubric").asBoolean());
  fixture.put("rubricVersion","other-version");jdbc.update("UPDATE mock_interview_v2_turn SET evaluation_json=? WHERE session_id=? AND depth=1",json.writeValueAsString(fixture),sid);
  report=state(sid,1,200).path("report");assertTrue(report.path("mixedRubric").asBoolean());assertTrue(report.path("overallScore").isNull());assertTrue(report.path("dimensionScores").path("TECHNICAL_CORRECTNESS").isNull());
 }

 @Test void realKnowledgePreviewConfirmedTaskAndProjectSnapshotUseExistingServices()throws Exception{
  String name="面试知识-"+key();jdbc.update("INSERT INTO doc_document(title,content,status) VALUES(?,?,1)",name,"该项目只提供文字资料，未验证源码实现。");
  Long doc=jdbc.queryForObject("SELECT id FROM doc_document WHERE title=?",Long.class,name);jdbc.update("INSERT INTO kg_entity(doc_id,name) VALUES(?,?)",doc,name);Long knowledge=jdbc.queryForObject("SELECT id FROM kg_entity WHERE name=?",Long.class,name);
  JsonNode s=postJson("/api/interview/v2/sessions",Map.of("requestId",key(),"track","RAG","mode","TEACHING","questionCount",1,"maxDepth",0,"projectDocumentIds",List.of(doc),"projectContext","仅规划 RAG，尚未完成部署"),1,200);
  long sid=s.path("sessionId").asLong();String path="/api/interview/v2/sessions/"+sid;
  postJson(path+"/answers",Map.of("requestId",key(),"version",s.path("version").asLong(),"answer","这个项目仅有文字资料，没有代码验收数据"),1,200);s=settled(sid);assertEquals("COMPLETED",s.path("state").asText());assertTrue(s.path("projectContext").toString().contains("USER_REPORTED"));assertTrue(s.path("projectContext").toString().contains("DOCUMENT_CONTENT_ONLY"));
  // 模型缺口为测试夹具，知识与Task均为真实隔离库记录，验证应用调用链而非模型质量。
  jdbc.update("UPDATE mock_interview_v2_turn SET evaluation_json=? WHERE session_id=?",json.writeValueAsString(Map.of("gaps",List.of(name))),sid);
  var preview=mvc.perform(get(path+"/remediation").header("Authorization",token(1))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andReturn();
  assertEquals(knowledge.longValue(),json.readTree(preview.getResponse().getContentAsString()).path("data").path("resources").get(0).path("knowledgeId").asLong());
  String request=key();var created=postJson(path+"/remediation/confirm",Map.of("requestId",request,"knowledgeIds",List.of(knowledge)),1,200);long task=created.get(0).path("taskId").asLong();assertTrue(task>0);
  postJson(path+"/remediation/confirm",Map.of("requestId",key(),"knowledgeIds",List.of(knowledge)),1,200);
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM interview_remediation WHERE session_id=? AND knowledge_id=?",Integer.class,sid,knowledge));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM task WHERE id=? AND user_id=1 AND deleted=0 AND notes LIKE ?",Integer.class,task,"%"+knowledge+"%"));
  mvc.perform(get(path+"/remediation").header("Authorization",token(2))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
 }

 @Test void timeoutFallbackAndLateWorkerDoNotPreventCancellingNextEvaluation()throws Exception{
  var firstEntered=new java.util.concurrent.CountDownLatch(1);var secondEntered=new java.util.concurrent.CountDownLatch(1);var releaseFirst=new java.util.concurrent.CountDownLatch(1);var releaseSecond=new java.util.concurrent.CountDownLatch(1);var firstReturned=new java.util.concurrent.CountDownLatch(1);var secondInterrupted=new java.util.concurrent.CountDownLatch(1);var calls=new java.util.concurrent.atomic.AtomicInteger();
  when(ai.isConfigured(1L)).thenReturn(true);when(ai.currentScope(1L)).thenReturn("protocol:timeout");
  doAnswer(invocation->{if(calls.incrementAndGet()==1){firstEntered.countDown();long limit=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);while(releaseFirst.getCount()>0&&System.nanoTime()<limit)try{releaseFirst.await(100,java.util.concurrent.TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){/* 模拟供应商不能取消的晚到响应，不代表真实付费调用。 */}firstReturned.countDown();}else{secondEntered.countDown();try{releaseSecond.await(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException interrupted){secondInterrupted.countDown();}}return "invalid model output";}).when(ai).completeStructured(anyString(),anyString(),org.mockito.ArgumentMatchers.<String>isNull(),eq(1L));
  try{
   JsonNode s=create(key());long sid=s.path("sessionId").asLong();String path="/api/interview/v2/sessions/"+sid;
   postJson(path+"/answers",Map.of("version",s.path("version").asLong(),"requestId",key(),"answer","第一轮已确认回答"),1,200);assertTrue(firstEntered.await(2,java.util.concurrent.TimeUnit.SECONDS));
   jdbc.update("UPDATE mock_interview_v2_state SET updated_at=DATEADD('SECOND',-60,CURRENT_TIMESTAMP) WHERE session_id=?",sid);s=state(sid,1,200);assertEquals("FOLLOW_UP",s.path("state").asText());
   postJson(path+"/answers",Map.of("version",s.path("version").asLong(),"requestId",key(),"answer","下一轮已确认回答"),1,200);assertTrue(secondEntered.await(2,java.util.concurrent.TimeUnit.SECONDS));
   releaseFirst.countDown();assertTrue(firstReturned.await(2,java.util.concurrent.TimeUnit.SECONDS));Thread.sleep(100);
   s=state(sid,1,200);assertEquals("EVALUATING",s.path("state").asText());
   postJson(path+"/finish",Map.of("version",s.path("version").asLong(),"requestId",key()),1,200);assertTrue(secondInterrupted.await(2,java.util.concurrent.TimeUnit.SECONDS),"结束应取消第二轮任务，旧轮清理不能删掉新任务引用");
   assertEquals("COMPLETED",state(sid,1,200).path("state").asText());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM mock_interview_v2_turn WHERE session_id=?",Integer.class,sid));
  }finally{releaseFirst.countDown();releaseSecond.countDown();}
 }

 @Test void selectedScenarioUsesExistingTrackAndCanBeRepeatedWithoutRandomDraws()throws Exception{
  mvc.perform(get("/api/interview/tracks/KAFKA/questions").header("Authorization",token(1))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data[0].id").value("KAFKA-1")).andExpect(jsonPath("$.data[0].referencePoints").doesNotExist());
  for(int repeat=0;repeat<2;repeat++){var s=postJson("/api/interview/v2/sessions",Map.of("requestId",key(),"track","KAFKA","questionId","KAFKA-1","questionCount",3,"mode","STRICT"),1,200);assertEquals("KAFKA-1",s.path("currentQuestion").path("id").asText());assertEquals(1,s.path("questionCount").asInt());assertEquals("QUESTION_BANK",s.path("currentQuestion").path("source").asText());}
  postJson("/api/interview/v2/sessions",Map.of("requestId",key(),"track","RAG","questionId","KAFKA-1","mode","STRICT"),1,400);
 }
 @Test void validatedMissingKnowledgeIdentityMatchesRealResourceWhenGapIsProse()throws Exception{
  String name="知识名称-"+key();jdbc.update("INSERT INTO doc_document(title,content,status) VALUES(?,?,1)",name,"真实学习内容");
  Long doc=jdbc.queryForObject("SELECT id FROM doc_document WHERE title=?",Long.class,name);jdbc.update("INSERT INTO kg_entity(doc_id,name) VALUES(?,?)",doc,name);
  JsonNode s=postJson("/api/interview/v2/sessions",Map.of("requestId",key(),"track","RAG","mode","BASIC","questionCount",1,"maxDepth",0),1,200);
  long sid=s.path("sessionId").asLong();String path="/api/interview/v2/sessions/"+sid;
  postJson(path+"/answers",Map.of("requestId",key(),"version",s.path("version").asLong(),"answer","未展开说明相关知识"),1,200);settled(sid);
  // 协议夹具只验证已校验名称的资源匹配，不代表真实模型调用。
  jdbc.update("UPDATE mock_interview_v2_turn SET evaluation_json=? WHERE session_id=?",json.writeValueAsString(Map.of("gaps",List.of("尚未说明该知识的故障边界"),"missingKnowledge",List.of(name))),sid);
  var result=mvc.perform(get(path+"/remediation").header("Authorization",token(1))).andExpect(status().isOk()).andReturn();
  var resource=json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data").path("resources").get(0);
  assertEquals(name,resource.path("title").asText());assertTrue(resource.path("sourceCitations").get(0).asText().endsWith(":missingKnowledge"));
 }

}
