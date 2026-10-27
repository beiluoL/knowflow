package com.knowflow.controller;

import com.fasterxml.jackson.databind.*;
import com.knowflow.service.AiService;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"knowflow.product.private-quiz.enabled=true"})
@AutoConfigureMockMvc @ActiveProfiles("local")
class PrivateQuizHttpTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JwtUtils jwt; @Autowired JdbcTemplate sql;
 @MockBean com.knowflow.service.impl.AiServiceImpl ai;
 final long owner=98301;
 String auth(long user){return "Bearer "+jwt.generateToken(user,"private-"+user,user==98303?"ADMIN":"USER");}
 JsonNode call(String method,String path,Object body,long user,int expected)throws Exception{
  var req=method.equals("POST")?post(path):get(path);
  return json.readTree(mvc.perform(req.header("Authorization",auth(user)).contentType("application/json").content(body==null?"":json.writeValueAsString(body)))
   .andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(expected)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
 }
 @BeforeEach void provider(){when(ai.isConfigured(anyLong())).thenReturn(true);when(ai.complete(anyString(),anyString(),isNull(String.class),anyLong())).thenReturn("[{\"type\":\"TRUE_FALSE\",\"content\":\"Java有GC吗？\",\"options\":[],\"answer\":\"true\",\"explanation\":\"Java具有垃圾回收\"},{\"type\":\"SINGLE_CHOICE\",\"content\":\"哪个是JVM语言？\",\"options\":[\"Java\",\"C\"],\"answer\":\"0\",\"explanation\":\"Java在JVM上运行\"},{\"type\":\"TRUE_FALSE\",\"content\":\"Spring是数据库吗？\",\"options\":[],\"answer\":\"false\",\"explanation\":\"Spring是框架\"}]");}
 JsonNode draft()throws Exception{
  long id=call("POST","/api/notes",Map.of("title","课程笔记","content","Java有GC，Java运行在JVM上，Spring是框架。"),owner,200).path("id").asLong();
  return call("POST","/api/learning/private-quizzes/preview",Map.of("noteId",id,"expectedRevision",1,"requestId",UUID.randomUUID().toString()),owner,200);
 }
 JsonNode save(JsonNode d)throws Exception{return call("POST","/api/learning/private-quizzes/"+d.path("id").asLong()+"/confirm",Map.of("questions",d.path("questions")),owner,200);}
 @Test void privateClosedBookImmutableHistoryAndReplay()throws Exception{
  mvc.perform(get("/api/learning/private-quizzes")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
  var d=draft();var q=save(d);long id=q.path("id").asLong();
  for(long user:new long[]{98302,98303}){
   call("GET","/api/learning/private-quizzes/"+id,null,user,404);
   call("POST","/api/learning/private-quizzes/"+id+"/attempts",Map.of("requestId",UUID.randomUUID().toString()),user,404);
  }
  assertFalse(q.toString().contains("\"answer\""));assertFalse(q.toString().contains("explanation"));
  String key=UUID.randomUUID().toString();var a=call("POST","/api/learning/private-quizzes/"+id+"/attempts",Map.of("requestId",key),owner,200);
  long attempt=a.path("id").asLong();String base="/api/learning/private-quizzes/attempts/"+attempt;
  assertFalse(a.toString().contains("\"answer\""));
  assertEquals(attempt,call("POST","/api/learning/private-quizzes/"+id+"/attempts",Map.of("requestId",key),owner,200).path("id").asLong());
  var wrong=Map.of("answers",List.of("false","1","true"));
  assertEquals(0,call("POST",base+"/submit",wrong,owner,200).path("score").asInt());
  assertEquals(0,call("POST",base+"/submit",wrong,owner,200).path("score").asInt());
  call("POST",base+"/submit",Map.of("answers",List.of("true","0","false")),owner,409);
  var second=call("POST","/api/learning/private-quizzes/"+id+"/attempts",Map.of("requestId",UUID.randomUUID().toString()),owner,200);
  assertEquals(100,call("POST","/api/learning/private-quizzes/attempts/"+second.path("id").asLong()+"/submit",Map.of("answers",List.of("true","0","false")),owner,200).path("score").asInt());
  assertEquals(0,call("GET",base,null,owner,200).path("score").asInt());
  assertEquals(2,call("GET","/api/learning/private-quizzes/"+id+"/history",null,owner,200).size());
  for(long user:new long[]{98302,98303})call("GET",base,null,user,404);
  assertEquals(2,sql.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND resource_type='PRIVATE_QUIZ' AND resource_id=?",Integer.class,owner,id));
  assertEquals(0,sql.queryForObject("SELECT COUNT(*) FROM quiz_question WHERE title='课程笔记'",Integer.class));
 }
 @Test void changedSourceCannotConfirmAndDeletedSourceRevokesAccess()throws Exception{
  var d=draft();long note=d.path("noteId").asLong();
  sql.update("UPDATE learning_note SET revision=revision+1 WHERE id=?",note);
  call("POST","/api/learning/private-quizzes/"+d.path("id").asLong()+"/confirm",Map.of("questions",d.path("questions")),owner,409);
  var d2=draft();save(d2);sql.update("UPDATE learning_note SET deleted=1 WHERE id=?",d2.path("noteId").asLong());
  call("GET","/api/learning/private-quizzes/"+d2.path("id").asLong(),null,owner,404);
 }
 @Test void malformedProviderNeverBecomesSuccessfulDraft()throws Exception{
  when(ai.complete(anyString(),anyString(),isNull(String.class),anyLong())).thenReturn("这是三道题");
  long note=call("POST","/api/notes",Map.of("title","失败测试","content","失败原文"),owner,200).path("id").asLong();
  call("POST","/api/learning/private-quizzes/preview",Map.of("noteId",note,"expectedRevision",1,"requestId",UUID.randomUUID().toString()),owner,400);
 }

 @Test void concurrentSubmitWritesOneImmutableAttemptAndOneEvent()throws Exception{
  var q=save(draft());long quiz=q.path("id").asLong();long id=call("POST","/api/learning/private-quizzes/"+quiz+"/attempts",Map.of("requestId",UUID.randomUUID().toString()),owner,200).path("id").asLong();
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
  try{
   var tasks=new ArrayList<java.util.concurrent.Future<JsonNode>>();
   for(int i=0;i<2;i++)tasks.add(pool.submit(()->{gate.await();return call("POST","/api/learning/private-quizzes/attempts/"+id+"/submit",Map.of("answers",List.of("true","0","false")),owner,200);}));
   gate.countDown();assertEquals(100,tasks.get(0).get().path("score").asInt());assertEquals(100,tasks.get(1).get().path("score").asInt());
   assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM learning_event WHERE resource_type='PRIVATE_QUIZ' AND resource_id=?",Integer.class,quiz));
  }finally{pool.shutdownNow();}
 }
 @Test void eventFailureRollsBackGradeAndOriginalInputThenAllowsRetry()throws Exception{
  var q=save(draft());long quiz=q.path("id").asLong();long id=call("POST","/api/learning/private-quizzes/"+quiz+"/attempts",Map.of("requestId",UUID.randomUUID().toString()),owner,200).path("id").asLong();
  sql.execute("ALTER TABLE learning_event ADD CONSTRAINT product_test_event_failure CHECK (resource_type <> 'PRIVATE_QUIZ' OR resource_id <> "+quiz+")");
  try{
   call("POST","/api/learning/private-quizzes/attempts/"+id+"/submit",Map.of("answers",List.of("true","0","false")),owner,500);
   assertEquals("IN_PROGRESS",sql.queryForObject("SELECT state FROM private_quiz_attempt WHERE id=?",String.class,id));
   assertNull(sql.queryForObject("SELECT user_answers FROM private_quiz_attempt WHERE id=?",String.class,id));
  }finally{sql.execute("ALTER TABLE learning_event DROP CONSTRAINT product_test_event_failure");}
  assertEquals(100,call("POST","/api/learning/private-quizzes/attempts/"+id+"/submit",Map.of("answers",List.of("true","0","false")),owner,200).path("score").asInt());
 }
 @Test void previewAndBeginRejectSameKeyDifferentInputsAndDoNotRegenerate()throws Exception{
  var d=draft();long id=d.path("id").asLong();save(d);
  String key=UUID.randomUUID().toString();
  call("POST","/api/learning/private-quizzes/preview",Map.of("noteId",d.path("noteId").asLong(),"expectedRevision",1,"requestId",key),owner,200);
  int calls=mockingDetails(ai).getInvocations().size();
  call("POST","/api/learning/private-quizzes/preview",Map.of("noteId",d.path("noteId").asLong(),"expectedRevision",1,"requestId",key),owner,200);
  assertEquals(calls,mockingDetails(ai).getInvocations().size());
  var other=draft();save(other);
  call("POST","/api/learning/private-quizzes/preview",Map.of("noteId",other.path("noteId").asLong(),"expectedRevision",1,"requestId",key),owner,409);
  String attemptKey=UUID.randomUUID().toString();
  call("POST","/api/learning/private-quizzes/"+id+"/attempts",Map.of("requestId",attemptKey),owner,200);
  call("POST","/api/learning/private-quizzes/"+other.path("id").asLong()+"/attempts",Map.of("requestId",attemptKey),owner,409);
 }

 @Test void concurrentSamePreviewKeyForDifferentSourcesNeverReturnsOtherSourceOr500()throws Exception{
  long n1=call("POST","/api/notes",Map.of("title","并发来源一","content","Java有GC"),owner,200).path("id").asLong();
  long n2=call("POST","/api/notes",Map.of("title","并发来源二","content","Spring是框架"),owner,200).path("id").asLong();
  String generated=ai.complete("fixture","fixture",(String)null,owner);
  var barrier=new java.util.concurrent.CyclicBarrier(2);
  when(ai.complete(anyString(),anyString(),isNull(String.class),anyLong())).thenAnswer(inv->{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);return generated;});
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);String key=UUID.randomUUID().toString();
  try{
   var tasks=new ArrayList<java.util.concurrent.Future<org.springframework.mock.web.MockHttpServletResponse>>();
   for(long note:new long[]{n1,n2})tasks.add(pool.submit(()->mvc.perform(post("/api/learning/private-quizzes/preview").header("Authorization",auth(owner)).contentType("application/json").content(json.writeValueAsString(Map.of("noteId",note,"expectedRevision",1,"requestId",key)))).andReturn().getResponse()));
   var responses=List.of(tasks.get(0).get(),tasks.get(1).get());var statuses=responses.stream().map(r->r.getStatus()).sorted().toList();
   assertEquals(List.of(200,409),statuses);
   for(var response:responses)assertEquals(response.getStatus(),json.readTree(response.getContentAsString()).path("code").asInt());
  }finally{pool.shutdownNow();}
 }

 @Test void ownerEditorReaderSourcesAreRecheckedAfterRevocation()throws Exception{
  sql.update("INSERT INTO doc_category(id,name,owner_id,status) VALUES(98310,'测验来源库',98310,1)");
  sql.update("INSERT INTO doc_document(id,title,category_id,status) VALUES(98310,'课程来源',98310,1)");
  sql.update("INSERT INTO kb_member(category_id,user_id,role,status) VALUES(98310,98311,'EDITOR',1),(98310,98312,'READER',1)");
  for(long user:new long[]{98310,98311,98312}){
   var note=call("POST","/api/notes",Map.of("title","受控来源","content","Java有GC","sourceDocId",98310),user,200);
   var d=call("POST","/api/learning/private-quizzes/preview",Map.of("noteId",note.path("id").asLong(),"expectedRevision",1,"requestId",UUID.randomUUID().toString()),user,200);
   long quiz=d.path("id").asLong();call("POST","/api/learning/private-quizzes/"+quiz+"/confirm",Map.of("questions",d.path("questions")),user,200);
   call("GET","/api/learning/private-quizzes/"+quiz,null,user,200);
   if(user!=98310){
    sql.update("UPDATE kb_member SET status=0 WHERE category_id=98310 AND user_id=?",user);
    call("GET","/api/learning/private-quizzes/"+quiz,null,user,403);
    call("POST","/api/learning/private-quizzes/"+quiz+"/attempts",Map.of("requestId",UUID.randomUUID().toString()),user,403);
    call("GET","/api/learning/private-quizzes/"+quiz+"/history",null,user,403);
   }
  }
 }
}
