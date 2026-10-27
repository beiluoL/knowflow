package com.knowflow.controller;

import com.fasterxml.jackson.databind.*;
import com.knowflow.ai.capability.AiCapabilityProperties;
import com.knowflow.service.AiService;
import com.knowflow.service.impl.AiServiceImpl;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"knowflow.product.tutor.enabled=true","knowflow.product.tutor-coaching.enabled=true","knowflow.product.private-quiz.enabled=true","server.address=127.0.0.1"}) @ActiveProfiles("local")
class TutorHttpTest {
 @Autowired ObjectMapper json;@Autowired JwtUtils jwt;@Autowired JdbcTemplate sql;@Autowired AiCapabilityProperties capability;
 @MockBean AiServiceImpl ai;
 @org.springframework.boot.test.web.server.LocalServerPort int port;
 final java.net.http.HttpClient client=java.net.http.HttpClient.newHttpClient();
 List<String> prompts=new java.util.concurrent.CopyOnWriteArrayList<>();
 String auth(long uid){return "Bearer "+jwt.generateToken(uid,"tutor-"+uid,uid==98403?"ADMIN":"USER");}
 JsonNode call(String method,String path,Object body,long uid,int expected)throws Exception{
  var builder=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+path)).timeout(java.time.Duration.ofSeconds(15)).header("Authorization",auth(uid)).header("Content-Type","application/json");
  var request=builder.method(method,body==null?java.net.http.HttpRequest.BodyPublishers.noBody():java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
  var response=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));assertEquals(expected,response.statusCode(),path);
  var result=json.readTree(response.body());assertEquals(expected,result.path("code").asInt(),path+" body.code");return result.path("data");
 }

 @BeforeEach void provider(){capability.setEnabled(true);doAnswer(inv->{prompts.add(inv.getArgument(0));SseEmitter emitter=inv.getArgument(4);emitter.send(SseEmitter.event().name("delta").data("{\"content\":\"真实协议替身回答\"}"));AiService.StreamCompletionCallback cb=inv.getArgument(5);cb.onComplete("真实协议替身回答",true);emitter.send(SseEmitter.event().name("done").data("{\"content\":\"真实协议替身回答\"}"));emitter.complete();return null;}).when(ai).streamChat(anyString(),anyString(),anyLong(),isNull(),any(SseEmitter.class),any(AiService.StreamCompletionCallback.class),isNull(),isNull(),isNull(),anyBoolean(),isNull());}
 long session()throws Exception{var note=call("POST","/api/notes",Map.of("title","Tutor来源","content","JVM提供GC，Spring通过IoC管理Bean"),98401,200);return call("POST","/api/learning/tutor/sessions",Map.of("noteId",note.path("id").asLong(),"mode","SOCRATIC"),98401,200).path("conversationId").asLong();}
 String stream(long conv,String text)throws Exception{
  var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api/chat/stream")).timeout(java.time.Duration.ofSeconds(15)).header("Authorization",auth(98401)).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("conversationId",conv,"content",text,"useKnowledgeBase",false)))).build();
  var response=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));assertEquals(200,response.statusCode());assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("text/event-stream"));return response.body();
 }
 void send(long conv,String text)throws Exception{String body=stream(conv,text);assertTrue(body.contains("done"));assertFalse(body.contains("event:error"));}

 @Test void threeModesUseOriginalSseAndHistoryAndFreezeModePerTurn()throws Exception{
  long conv=session();send(conv,"请先提问");assertTrue(prompts.get(0).contains("SOCRATIC"));assertTrue(prompts.get(0).contains("JVM提供GC"));
  var binding=call("GET","/api/learning/tutor/sessions/"+conv,null,98401,200);
  call("POST","/api/learning/tutor/sessions/"+conv+"/mode",Map.of("mode","DIRECT","expectedRevision",binding.path("revision").asLong()),98401,200);send(conv,"请直接讲解");
  assertTrue(prompts.get(1).contains("DIRECT"));assertTrue(prompts.get(1).contains("请先提问"));
  call("POST","/api/learning/tutor/sessions/"+conv+"/mode",Map.of("mode","TEACH_BACK","expectedRevision",2),98401,200);send(conv,"我的复述：IoC管理Bean");assertTrue(prompts.get(2).contains("TEACH_BACK"));verify(ai,never()).complete(anyString(),anyString(),org.mockito.ArgumentMatchers.<String>isNull(),eq(98401L));
  var messages=call("GET","/api/chat/conversations/"+conv+"/messages",null,98401,200);assertEquals(6,messages.size());assertTrue(messages.get(0).path("docReferences").asText().contains("SOCRATIC"));assertTrue(messages.get(2).path("docReferences").asText().contains("DIRECT"));
  call("POST","/api/learning/tutor/sessions/"+conv+"/mode",Map.of("mode","DIRECT","expectedRevision",1),98401,409);
  assertEquals(6,sql.queryForObject("SELECT COUNT(*) FROM chat_message WHERE conversation_id=? AND deleted=0",Integer.class,conv));
 }
 @Test void sourceAndOwnerChecksAlsoProtectOriginalChatEndpointsAndSharing()throws Exception{
  long conv=session();for(long user:new long[]{98402,98403}){
   call("GET","/api/learning/tutor/sessions/"+conv,null,user,404);
   call("GET","/api/chat/conversations/"+conv+"/messages",null,user,404);
  }
  send(conv,"我的来源是什么？");
  call("POST","/api/chat/conversations/"+conv+"/shares",Map.of("confirmed",true,"expiresInHours",24),98401,400);
  long note=call("GET","/api/learning/tutor/sessions/"+conv,null,98401,200).path("noteId").asLong();sql.update("UPDATE learning_note SET deleted=1 WHERE id=?",note);
  call("GET","/api/chat/conversations/"+conv+"/messages",null,98401,404);
  call("POST","/api/chat/stream",Map.of("conversationId",conv,"content","绕过来源"),98401,404);
 }
 @Test void providerFailureRetainsUserInputWithoutInventingAssistant()throws Exception{
  long conv=session();doAnswer(inv->{AiService.StreamCompletionCallback cb=inv.getArgument(5);cb.onComplete("Provider超时",false);return null;}).when(ai).streamChat(anyString(),anyString(),anyLong(),isNull(),any(SseEmitter.class),any(AiService.StreamCompletionCallback.class),isNull(),isNull(),isNull(),anyBoolean(),isNull());
  String result=stream(conv,"保留我的原文");assertTrue(result.contains("error"));assertEquals(1,call("GET","/api/chat/conversations/"+conv+"/messages",null,98401,200).size());
 }

 @Autowired com.knowflow.learning.tutor.TutorContextService tutors;
 @Test void assistantAndConversationUpdateRollbackTogetherAndDeletedSourceCannotCommit()throws Exception{
  long conv=session();var prepared=tutors.prepare(conv,98401);
  var message=new com.knowflow.entity.ChatMessage();message.setConversationId(conv);message.setUserId(98401L);message.setRole("assistant");message.setContent("可回滚导师回复");
  sql.execute("ALTER TABLE chat_conversation ADD CONSTRAINT tutor_test_failure CHECK (id <> "+conv+" OR message_count=0)");
  try{assertThrows(org.springframework.dao.DataAccessException.class,()->tutors.saveAssistant(message,prepared));assertEquals(0,sql.queryForObject("SELECT COUNT(*) FROM chat_message WHERE conversation_id=?",Integer.class,conv));}finally{sql.execute("ALTER TABLE chat_conversation DROP CONSTRAINT tutor_test_failure");}
  sql.update("UPDATE learning_note SET deleted=1 WHERE id=?",prepared.noteId());
  assertThrows(com.knowflow.exception.BusinessException.class,()->tutors.saveAssistant(message,prepared));assertEquals(0,sql.queryForObject("SELECT COUNT(*) FROM chat_message WHERE conversation_id=?",Integer.class,conv));
 }

 @Test void historicalDocumentReferenceCannotBypassRevocationAfterNoteSourceChanges()throws Exception{
  sql.update("INSERT INTO doc_category(id,name,owner_id,status) VALUES(98450,'原始私有来源',98450,1),(98451,'后续本人来源',98401,1)");
  sql.update("INSERT INTO doc_document(id,title,category_id,status) VALUES(98450,'原文A',98450,1),(98451,'原文B',98451,1)");
  sql.update("INSERT INTO kb_member(category_id,user_id,role,status) VALUES(98450,98401,'READER',1)");
  var note=call("POST","/api/notes",Map.of("title","冻结来源","content","原文A内容","sourceDocId",98450),98401,200);long id=note.path("id").asLong();
  long conv=call("POST","/api/learning/tutor/sessions",Map.of("noteId",id,"mode","SOCRATIC"),98401,200).path("conversationId").asLong();send(conv,"请提问");
  sql.update("UPDATE learning_note SET source_doc_id=98451,revision=revision+1 WHERE id=?",id);sql.update("UPDATE kb_member SET status=0 WHERE category_id=98450 AND user_id=98401");
  call("GET","/api/chat/conversations/"+conv+"/messages",null,98401,403);
  call("PATCH","/api/chat/conversations/"+conv,Map.of("title","不得回传撤权内容"),98401,403);
  call("PATCH","/api/chat/conversations/"+conv+"/state",Map.of("pinned",true),98401,403);
 }

 @Test void progressiveHintsPersistOnlyDeliveredAndSameRequestReplaysWithoutDoubleCount()throws Exception{
  long conv=session();send(conv,"我不会，请先提问");
  String key=UUID.randomUUID().toString();
  hint(conv,1,key,false,200);hint(conv,1,key,false,200);
  var state=call("GET","/api/learning/tutor/sessions/"+conv+"/hints",null,98401,200);
  assertEquals(1,state.path("hintCount").asInt());assertEquals(1,state.path("maxHintLevel").asInt());assertFalse(state.path("answerExposed").asBoolean());
  assertEquals(4,call("GET","/api/chat/conversations/"+conv+"/messages",null,98401,200).size());
  hint(conv,2,key,false,409);hint(conv,4,UUID.randomUUID().toString(),false,400);
  hint(conv,2,UUID.randomUUID().toString(),false,200);send(conv,"我自己的回答是IoC管理Bean");
  var history=call("GET","/api/chat/conversations/"+conv+"/messages",null,98401,200);
  assertTrue(history.get(history.size()-2).path("docReferences").asText().contains("hintCount"));
  for(long user:new long[]{98402,98403})call("GET","/api/learning/tutor/sessions/"+conv+"/hints",null,user,404);
 }
 void hint(long conv,int level,String key,boolean reveal,int expected)throws Exception{
  var data=Map.of("conversationId",conv,"content","请求提示L"+level,"tutorHintLevel",level,"tutorRequestId",key,"tutorRevealAnswer",reveal);
  var req=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api/chat/stream")).timeout(java.time.Duration.ofSeconds(15)).header("Authorization",auth(98401)).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build();
  var response=client.send(req,java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(expected,response.statusCode());
 }

 @Test void failedHintCanRetryWithoutDuplicatingOriginalAndExplicitL4MarksExposure()throws Exception{
  long conv=session();String key=UUID.randomUUID().toString();
  doAnswer(inv->{AiService.StreamCompletionCallback cb=inv.getArgument(5);cb.onComplete("超时",false);return null;}).when(ai).streamChat(anyString(),anyString(),anyLong(),isNull(),any(SseEmitter.class),any(AiService.StreamCompletionCallback.class),isNull(),isNull(),isNull(),anyBoolean(),isNull());
  hint(conv,1,key,false,200);assertEquals(0,call("GET","/api/learning/tutor/sessions/"+conv+"/hints",null,98401,200).path("hintCount").asInt());
  provider();hint(conv,1,key,false,200);assertEquals(2,call("GET","/api/chat/conversations/"+conv+"/messages",null,98401,200).size());
  hint(conv,4,UUID.randomUUID().toString(),true,200);assertTrue(call("GET","/api/learning/tutor/sessions/"+conv+"/hints",null,98401,200).path("answerExposed").asBoolean());
 }

 @Test void teachBackRetainsOriginalOnInvalidJsonAndRetryDoesNotOverwriteFirstAssessment()throws Exception{
  long conv=session();call("POST","/api/learning/tutor/sessions/"+conv+"/mode",Map.of("mode","TEACH_BACK","expectedRevision",1),98401,200);
  when(ai.isConfigured(98401L)).thenReturn(true);when(ai.currentScope(98401L)).thenReturn("test:model");
  var input=Map.of("requestId",UUID.randomUUID().toString(),"text","JVM提供GC","topic","JVM","scenario","GENERAL");
  when(ai.completeStructured(anyString(),anyString(),isNull(),eq(98401L))).thenReturn("not JSON");
  call("POST","/api/learning/tutor/sessions/"+conv+"/assessments",input,98401,500);
  var rows=call("GET","/api/learning/tutor/sessions/"+conv+"/assessments",null,98401,200);assertEquals("JVM提供GC",rows.get(0).path("originalText").asText());assertEquals("FAILED",rows.get(0).path("state").asText());
  String result="{\"correct\":[\"提到GC\"],\"technicalErrors\":[],\"missingKnowledge\":[\"补充IoC\"],\"principleGaps\":[],\"engineeringGaps\":[],\"practiceDirections\":[\"补充IoC\"],\"nextRetell\":[\"说明Bean管理\"],\"referenceAnswer\":\"JVM提供GC，Spring通过IoC管理Bean\",\"confidence\":\"LOW\",\"uncertainty\":\"合成来源有限\",\"evidence\":[{\"answerQuote\":\"JVM提供GC\",\"sourceQuote\":\"JVM提供GC\",\"reason\":\"原文支持\"}],\"dimensions\":[{\"key\":\"understanding\",\"score\":2,\"reason\":\"缺少原理\",\"evidenceIndex\":0},{\"key\":\"application\",\"score\":1,\"reason\":\"缺少案例\",\"evidenceIndex\":0},{\"key\":\"clarity\",\"score\":1,\"reason\":\"表达简短\",\"evidenceIndex\":0}]}";
  when(ai.completeStructured(anyString(),anyString(),isNull(),eq(98401L))).thenReturn(result);
  var first=call("POST","/api/learning/tutor/sessions/"+conv+"/assessments",input,98401,200);long firstId=first.path("id").asLong();assertEquals("SUCCEEDED",first.path("state").asText());
  call("POST","/api/learning/tutor/sessions/"+conv+"/assessments",input,98401,200);
  var second=call("POST","/api/learning/tutor/sessions/"+conv+"/assessments",Map.of("requestId",UUID.randomUUID().toString(),"text","JVM提供GC，Spring通过IoC管理Bean","topic","JVM","scenario","GENERAL","previousId",firstId),98401,200);
  assertEquals(firstId,second.path("previousId").asLong());assertTrue(second.path("comparable").asBoolean());var system=org.mockito.ArgumentCaptor.forClass(String.class);verify(ai,atLeastOnce()).completeStructured(system.capture(),anyString(),isNull(),eq(98401L));assertTrue(system.getValue().contains("\"required\""),"评价必须向Provider声明完整必填字段");
  rows=call("GET","/api/learning/tutor/sessions/"+conv+"/assessments",null,98401,200);assertEquals(2,rows.size());assertEquals("JVM提供GC",rows.get(0).path("originalText").asText());
  long note=call("GET","/api/learning/tutor/sessions/"+conv,null,98401,200).path("noteId").asLong();sql.update("UPDATE learning_note SET revision=revision+1 WHERE id=?",note);
  var changed=call("POST","/api/learning/tutor/sessions/"+conv+"/assessments",Map.of("requestId",UUID.randomUUID().toString(),"text","JVM提供GC","topic","JVM","scenario","GENERAL","previousId",firstId),98401,200);assertFalse(changed.path("comparable").asBoolean());
  sql.update("UPDATE learning_note SET deleted=1 WHERE id=?",note);call("GET","/api/learning/tutor/sessions/"+conv+"/assessments",null,98401,404);
  for(long user:new long[]{98402,98403})call("GET","/api/learning/tutor/sessions/"+conv+"/assessments",null,user,404);
 }

 @Test void microChallengeUsesPrivateQuestionAndOriginalAttemptWithoutAnswerLeak()throws Exception{
  long conv=session();long note=call("GET","/api/learning/tutor/sessions/"+conv,null,98401,200).path("noteId").asLong();when(ai.isConfigured(98401L)).thenReturn(true);
  var questions=List.of(Map.of("type","TRUE_FALSE","content","JVM提供GC吗？","options",List.of(),"answer","true","explanation","JVM提供GC"),Map.of("type","TRUE_FALSE","content","IoC管理Bean吗？","options",List.of(),"answer","true","explanation","Spring通过IoC管理Bean"),Map.of("type","TRUE_FALSE","content","GC存在吗？","options",List.of(),"answer","true","explanation","JVM提供GC"));
  when(ai.complete(anyString(),anyString(),org.mockito.ArgumentMatchers.<String>isNull(),eq(98401L))).thenReturn(json.writeValueAsString(questions));
  long quiz=call("POST","/api/learning/private-quizzes/preview",Map.of("noteId",note,"expectedRevision",1,"requestId",UUID.randomUUID().toString()),98401,200).path("id").asLong();call("POST","/api/learning/private-quizzes/"+quiz+"/confirm",Map.of("questions",questions),98401,200);
  when(ai.completeStructured(anyString(),anyString(),isNull(),eq(98401L))).thenReturn("{\"analogy\":\"像清理闲置物品\",\"concept\":\"JVM提供GC\",\"sourceQuote\":\"JVM提供GC\"}");
  var input=Map.of("noteId",note,"quizId",quiz,"questionIndex",0,"requestId",UUID.randomUUID().toString());var c=call("POST","/api/learning/micro-challenges",input,98401,200);long id=c.path("id").asLong();assertEquals("LEARN_READY",c.path("state").asText());
  assertEquals(id,call("POST","/api/learning/micro-challenges",input,98401,200).path("id").asLong());
  var attempt=call("POST","/api/learning/micro-challenges/"+id+"/begin",Map.of(),98401,200);assertEquals(1,attempt.path("attempt").path("questions").size());assertFalse(attempt.path("attempt").path("questions").get(0).has("answer"));assertFalse(attempt.has("concept"));
  call("PUT","/api/learning/micro-challenges/"+id+"/draft",Map.of("answer","false"),98401,200);assertEquals("false",call("GET","/api/learning/micro-challenges/"+id,null,98401,200).path("draftAnswer").asText());
  c=call("POST","/api/learning/micro-challenges/"+id+"/submit",Map.of("answer","false"),98401,200);assertEquals("COMPLETED",c.path("state").asText());assertEquals(0,c.path("attempt").path("score").asInt());assertTrue(c.path("attempt").path("items").get(0).path("question").has("explanation"));
  call("POST","/api/learning/micro-challenges/"+id+"/submit",Map.of("answer","true"),98401,409);
  for(long user:new long[]{98402,98403})call("GET","/api/learning/micro-challenges/"+id,null,user,404);
 }

 @Test void readingSelectionIsServerValidatedAndAppendRequiresConfirmationAndCas()throws Exception{
  long conv=session();long note=call("GET","/api/learning/tutor/sessions/"+conv,null,98401,200).path("noteId").asLong();
  call("POST","/api/chat/stream",Map.of("conversationId",conv,"content","解释选区","tutorSelection",Map.of("noteId",note,"revision",1,"quote","伪造引用","start",0)),98401,409);
  var data=Map.of("conversationId",conv,"content","解释选区","tutorSelection",Map.of("noteId",note,"revision",1,"quote","JVM提供GC","start",0));
  var req=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api/chat/stream")).timeout(java.time.Duration.ofSeconds(15)).header("Authorization",auth(98401)).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build();assertTrue(client.send(req,java.net.http.HttpResponse.BodyHandlers.ofString()).body().contains("done"));
  var history=call("GET","/api/chat/conversations/"+conv+"/messages",null,98401,200);assertEquals(2,history.size());assertTrue(history.get(0).path("docReferences").asText().contains("selection"));long message=history.get(1).path("id").asLong();String path="/api/learning/tutor/sessions/"+conv+"/answers/"+message+"/to-note";
  call("POST",path,Map.of("confirmed",false,"expectedRevision",1),98401,400);
  for(long user:new long[]{98402,98403})call("POST",path,Map.of("confirmed",true,"expectedRevision",1),user,404);
  call("POST",path,Map.of("confirmed",true,"expectedRevision",99),98401,409);
  var saved=call("POST",path,Map.of("confirmed",true,"expectedRevision",1),98401,200);assertEquals(2,saved.path("revision").asInt());assertTrue(saved.path("content").asText().contains("真实协议替身回答"));
  assertEquals(2,call("POST",path,Map.of("confirmed",true,"expectedRevision",1),98401,200).path("revision").asInt());
  call("POST","/api/chat/stream",data,98401,409);
 }

 @Test void invalidHintPayloadDoesNotConsumeRequestKey()throws Exception{long conv=session();String key=UUID.randomUUID().toString();call("POST","/api/chat/stream",Map.of("conversationId",conv,"content","x".repeat(4001),"tutorHintLevel",1,"tutorRequestId",key),98401,400);hint(conv,1,key,false,200);assertEquals(1,call("GET","/api/learning/tutor/sessions/"+conv+"/hints",null,98401,200).path("hintCount").asInt());}

 String assessmentOutput(String answer,String source)throws Exception{return json.writeValueAsString(Map.ofEntries(Map.entry("correct",List.of("可回溯来源")),Map.entry("technicalErrors",List.of()),Map.entry("missingKnowledge",List.of()),Map.entry("principleGaps",List.of()),Map.entry("engineeringGaps",List.of()),Map.entry("practiceDirections",List.of("继续复述")),Map.entry("nextRetell",List.of("补充案例")),Map.entry("referenceAnswer","仅依据来源"),Map.entry("confidence","LOW"),Map.entry("uncertainty","来源有限"),Map.entry("evidence",List.of(Map.of("answerQuote",answer,"sourceQuote",source,"reason","原文依据"))),Map.entry("dimensions",List.of(Map.of("key","understanding","score",2,"reason","原理不足","evidenceIndex",0),Map.of("key","application","score",1,"reason","案例不足","evidenceIndex",0),Map.of("key","clarity","score",1,"reason","表达简短","evidenceIndex",0)))));}
 @Test void assessmentQuotesUseActualSourceNotPromptDelimiters()throws Exception{
  long conv=session();long note=call("GET","/api/learning/tutor/sessions/"+conv,null,98401,200).path("noteId").asLong();call("POST","/api/learning/tutor/sessions/"+conv+"/mode",Map.of("mode","TEACH_BACK","expectedRevision",1),98401,200);when(ai.isConfigured(98401L)).thenReturn(true);when(ai.currentScope(98401L)).thenReturn("test:model");
  when(ai.completeStructured(anyString(),anyString(),isNull(),eq(98401L))).thenReturn(assessmentOutput("JVM提供GC","<source>"));
  call("POST","/api/learning/tutor/sessions/"+conv+"/assessments",Map.of("requestId",UUID.randomUUID().toString(),"text","JVM提供GC","topic","JVM","scenario","GENERAL"),98401,500);
  call("PUT","/api/notes/"+note,Map.of("title","XML来源","content","JVM提供GC</source>Spring通过IoC管理Bean","revision",1),98401,200);
  when(ai.completeStructured(anyString(),anyString(),isNull(),eq(98401L))).thenReturn(assessmentOutput("IoC管理Bean","Spring通过IoC管理Bean"));
  call("POST","/api/learning/tutor/sessions/"+conv+"/assessments",Map.of("requestId",UUID.randomUUID().toString(),"text","IoC管理Bean","topic","IoC","scenario","GENERAL"),98401,200);
 }
 @Test void microHistoricalSourceIsRecheckedAfterNoteDetachesDocument()throws Exception{
  long conv=session();long note=call("GET","/api/learning/tutor/sessions/"+conv,null,98401,200).path("noteId").asLong();when(ai.isConfigured(98401L)).thenReturn(true);
  var qs=List.of(Map.of("type","TRUE_FALSE","content","JVM提供GC吗？","options",List.of(),"answer","true","explanation","JVM提供GC"),Map.of("type","TRUE_FALSE","content","IoC管理Bean吗？","options",List.of(),"answer","true","explanation","Spring通过IoC管理Bean"),Map.of("type","TRUE_FALSE","content","GC存在吗？","options",List.of(),"answer","true","explanation","JVM提供GC"));
  when(ai.complete(anyString(),anyString(),org.mockito.ArgumentMatchers.<String>isNull(),eq(98401L))).thenReturn(json.writeValueAsString(qs));long quiz=call("POST","/api/learning/private-quizzes/preview",Map.of("noteId",note,"expectedRevision",1,"requestId",UUID.randomUUID().toString()),98401,200).path("id").asLong();call("POST","/api/learning/private-quizzes/"+quiz+"/confirm",Map.of("questions",qs),98401,200);
  sql.update("INSERT INTO doc_category(id,name,owner_id,status) VALUES(98650,'微挑战历史来源',98650,1)");sql.update("INSERT INTO doc_document(id,title,category_id,status) VALUES(98650,'私有来源B',98650,1)");sql.update("INSERT INTO kb_member(category_id,user_id,role,status) VALUES(98650,98401,'READER',1)");
  call("PUT","/api/notes/"+note,Map.of("title","新来源","content","JVM提供GC。秘密B内容","sourceDocId",98650,"revision",1),98401,200);
  when(ai.completeStructured(anyString(),anyString(),isNull(),eq(98401L))).thenReturn("{\"analogy\":\"历史账本\",\"concept\":\"秘密B内容\",\"sourceQuote\":\"秘密B内容\"}");long id=call("POST","/api/learning/micro-challenges",Map.of("noteId",note,"quizId",quiz,"questionIndex",0,"requestId",UUID.randomUUID().toString()),98401,200).path("id").asLong();
  var update=new HashMap<String,Object>();update.put("title","已解绑");update.put("content","后续正文");update.put("sourceDocId",null);update.put("revision",2);call("PUT","/api/notes/"+note,update,98401,200);sql.update("UPDATE kb_member SET status=0 WHERE category_id=98650 AND user_id=98401");call("GET","/api/learning/micro-challenges/"+id,null,98401,403);
 }

 @Test void providerBusinessFailureIsRealHttpErrorAndOriginalRetellSurvives()throws Exception{long conv=session();call("POST","/api/learning/tutor/sessions/"+conv+"/mode",Map.of("mode","TEACH_BACK","expectedRevision",1),98401,200);when(ai.isConfigured(98401L)).thenReturn(true);when(ai.currentScope(98401L)).thenReturn("test:model");when(ai.completeStructured(anyString(),anyString(),isNull(),eq(98401L))).thenThrow(new com.knowflow.exception.BusinessException(503,"供应商超时"));call("POST","/api/learning/tutor/sessions/"+conv+"/assessments",Map.of("requestId",UUID.randomUUID().toString(),"text","保留原始复述","topic","JVM","scenario","GENERAL"),98401,500);var rows=call("GET","/api/learning/tutor/sessions/"+conv+"/assessments",null,98401,200);assertEquals("保留原始复述",rows.get(0).path("originalText").asText());assertEquals("FAILED",rows.get(0).path("state").asText());assertTrue(rows.get(0).path("result").isNull());}
}
