package com.knowflow.controller;
import com.fasterxml.jackson.databind.*;
import com.knowflow.service.impl.AiServiceImpl;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"knowflow.product.interview-learning.enabled=true","knowflow.product.private-quiz.enabled=true","knowflow.product.tutor.enabled=true","knowflow.product.tutor-coaching.enabled=true","server.address=127.0.0.1"}) @ActiveProfiles("local")
class ProductFeedbackHttpTest {
 @Autowired ObjectMapper json;@Autowired JdbcTemplate sql;@Autowired JwtUtils jwt;@MockBean AiServiceImpl ai;
 @org.springframework.boot.test.web.server.LocalServerPort int port;
 final java.net.http.HttpClient client=java.net.http.HttpClient.newHttpClient();long owner;
 JsonNode call(String method,String path,Object body,long user,int expected)throws Exception{var b=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+path)).timeout(java.time.Duration.ofSeconds(20)).header("Content-Type","application/json");if(user>0)b.header("Authorization","Bearer "+jwt.generateToken(user,"product03-"+user,user==owner+2?"ADMIN":"USER"));var r=client.send(b.method(method,body==null?java.net.http.HttpRequest.BodyPublishers.noBody():java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(expected,r.statusCode(),path+" "+r.body());var result=json.readTree(r.body());assertEquals(expected,result.path("code").asInt());return result.path("data");}
 @BeforeEach void setup()throws Exception{String name="p03"+UUID.randomUUID().toString().replace("-","");var user=call("POST","/api/auth/register",Map.of("username",name.substring(0,20),"password","SyntheticOnly!27","email",name+"@example.test"),0,200);owner=jwt.getUserIdFromToken(user.path("token").asText());when(ai.isConfigured(anyLong())).thenReturn(true);when(ai.currentScope(anyLong())).thenReturn("test:contract");}
 record Source(long note,long quiz,long attempt){}
 Source wrong()throws Exception{
  long note=call("POST","/api/notes",Map.of("title","MVCC合成资料","content","Read View用于判断版本可见性。快照读不同于当前读。"),owner,200).path("id").asLong();
  var qs=List.of(Map.of("type","TRUE_FALSE","content","Read View用于判断版本可见性吗？","options",List.of(),"answer","true","explanation","Read View用于判断版本可见性。"),Map.of("type","TRUE_FALSE","content","快照读等同当前读吗？","options",List.of(),"answer","false","explanation","快照读不同于当前读。"),Map.of("type","TRUE_FALSE","content","Read View有关版本吗？","options",List.of(),"answer","true","explanation","Read View用于判断版本可见性。"));
  when(ai.complete(anyString(),anyString(),isNull(String.class),eq(owner))).thenReturn(json.writeValueAsString(qs));var d=call("POST","/api/learning/private-quizzes/preview",Map.of("noteId",note,"expectedRevision",1,"requestId",UUID.randomUUID().toString()),owner,200);long quiz=d.path("id").asLong();call("POST","/api/learning/private-quizzes/"+quiz+"/confirm",Map.of("questions",qs),owner,200);long attempt=call("POST","/api/learning/private-quizzes/"+quiz+"/attempts",Map.of("requestId",UUID.randomUUID().toString()),owner,200).path("id").asLong();call("POST","/api/learning/private-quizzes/attempts/"+attempt+"/submit",Map.of("answers",List.of("false","true","false")),owner,200);return new Source(note,quiz,attempt);
 }
 @Test void diagnosisCorrectionHistoryOwnershipAndRealPracticeReference()throws Exception{
  var source=wrong();when(ai.completeStructured(anyString(),anyString(),isNull(),eq(owner))).thenReturn("{\"labels\":[\"UNKNOWN\"],\"findings\":[{\"label\":\"UNKNOWN\",\"answerQuote\":\"false\",\"basisQuote\":\"Read View用于判断版本可见性。\",\"issue\":\"选择与题版依据冲突\",\"missingKnowledge\":\"Read View可见性\",\"practice\":\"对照概念后单题复练\"}],\"uncertainty\":\"仅选择题不能确定认知原因\",\"confidence\":\"LOW\"}");
  var input=Map.of("sourceType","PRIVATE_ATTEMPT","sourceId",source.attempt(),"questionIndex",0,"requestId",UUID.randomUUID().toString());var d=call("POST","/api/learning/error-annotations",input,owner,200);long id=d.path("id").asLong();assertEquals("UNKNOWN",d.path("original").path("labels").get(0).asText());assertEquals(id,call("POST","/api/learning/error-annotations",input,owner,200).path("id").asLong());verify(ai,times(1)).completeStructured(anyString(),anyString(),isNull(),eq(owner));
  var change=Map.of("expectedRevision",1,"requestId",UUID.randomUUID().toString(),"labels",List.of("CONCEPT","PRINCIPLE"),"note","我实际混淆了版本可见性与加锁读");d=call("POST","/api/learning/error-annotations/"+id+"/corrections",change,owner,200);assertEquals(2,d.path("revision").asInt());assertEquals(2,d.path("history").size());assertEquals("UNKNOWN",d.path("original").path("labels").get(0).asText());assertEquals(2,call("POST","/api/learning/error-annotations/"+id+"/corrections",change,owner,200).path("revision").asInt());
  for(long user:new long[]{owner+1,owner+2})call("GET","/api/learning/error-annotations/"+id,null,user,404);
  var practice=call("GET","/api/learning/error-annotations/"+id+"/practice",null,owner,200);assertEquals(source.note(),practice.path("noteId").asLong());assertEquals(0,practice.path("completedCount").asInt());
  when(ai.completeStructured(anyString(),anyString(),isNull(),eq(owner))).thenReturn("{\"analogy\":\"像读历史账本\",\"concept\":\"Read View用于判断版本可见性\",\"sourceQuote\":\"Read View用于判断版本可见性。\"}");long micro=call("POST","/api/learning/micro-challenges",Map.of("noteId",source.note(),"quizId",source.quiz(),"questionIndex",0,"requestId",UUID.randomUUID().toString()),owner,200).path("id").asLong();
  call("POST","/api/learning/error-annotations/"+id+"/practice",Map.of("microId",micro),owner,409);call("POST","/api/learning/micro-challenges/"+micro+"/begin",Map.of(),owner,200);call("POST","/api/learning/micro-challenges/"+micro+"/submit",Map.of("answer","true"),owner,200);assertEquals(1,call("POST","/api/learning/error-annotations/"+id+"/practice",Map.of("microId",micro),owner,200).path("completedCount").asInt());assertEquals(1,call("POST","/api/learning/error-annotations/"+id+"/practice",Map.of("microId",micro),owner,200).path("completedCount").asInt());
  sql.update("UPDATE learning_note SET deleted=1 WHERE id=?",source.note());call("GET","/api/learning/error-annotations/"+id,null,owner,404);
 }

 @Test void failedAiDoesNotInventDiagnosisOrChangeObjectiveScore()throws Exception{var source=wrong();when(ai.completeStructured(anyString(),anyString(),isNull(),eq(owner))).thenReturn("not-json");var input=Map.of("sourceType","PRIVATE_ATTEMPT","sourceId",source.attempt(),"questionIndex",0,"requestId",UUID.randomUUID().toString());call("POST","/api/learning/error-annotations",input,owner,500);var rows=call("GET","/api/learning/error-annotations?sourceType=PRIVATE_ATTEMPT&sourceId="+source.attempt()+"&questionIndex=0",null,owner,200);assertEquals("FAILED",rows.get(0).path("state").asText());assertEquals(0,rows.get(0).path("history").size());assertEquals(0,call("GET","/api/learning/private-quizzes/attempts/"+source.attempt(),null,owner,200).path("score").asInt());call("GET","/api/learning/error-annotations/"+rows.get(0).path("id").asLong(),null,0,401);}

 @Test void twoScenarioVariantsReusePrivateQuizAndKeepParentAttempt()throws Exception {
  var source=wrong();var variants=List.of(Map.of("knowledge","Read View","scenario","订单查询开启事务后读取历史版本","change","将概念问答改为订单可见性判断","difficulty","MEDIUM","basisQuote","Read View用于判断版本可见性。","ambiguity","SINGLE_VALID_ANSWER","question",Map.of("type","TRUE_FALSE","content","订单事务通过Read View判断哪些历史版本可见吗？","options",List.of(),"answer","true","explanation","Read View用于判断版本可见性。")),Map.of("knowledge","Read View","scenario","库存接口混用快照读与当前加锁读取","change","换成库存读取方式辨析","difficulty","HARD","basisQuote","Read View用于判断版本可见性。","ambiguity","SINGLE_VALID_ANSWER","question",Map.of("type","TRUE_FALSE","content","库存加锁读取时能把Read View可见性判断直接等同于当前读吗？","options",List.of(),"answer","false","explanation","快照读不同于当前读。")));
  when(ai.completeStructured(anyString(),anyString(),isNull(),eq(owner))).thenReturn(json.writeValueAsString(Map.of("variants",variants)));
  var input=Map.of("sourceType","PRIVATE_ATTEMPT","sourceId",source.attempt(),"questionIndex",0,"requestId",UUID.randomUUID().toString());var draft=call("POST","/api/learning/question-variants",input,owner,200);long id=draft.path("id").asLong();assertEquals(2,draft.path("variants").size());assertEquals("PRIVATE_UNREVIEWED",draft.path("reviewStatus").asText());assertEquals(id,call("POST","/api/learning/question-variants",input,owner,200).path("id").asLong());
  call("POST","/api/learning/question-variants/"+id+"/attempts",Map.of("requestId",UUID.randomUUID().toString()),owner,409);
  var confirmation=Map.of("expectedVersion",1,"variants",variants);var saved=call("POST","/api/learning/question-variants/"+id+"/confirm",confirmation,owner,200);assertFalse(saved.path("variants").get(0).path("question").has("answer"));call("POST","/api/learning/question-variants/"+id+"/confirm",confirmation,owner,200);
  String key=UUID.randomUUID().toString();var attempt=call("POST","/api/learning/question-variants/"+id+"/attempts",Map.of("requestId",key),owner,200);assertEquals(2,attempt.path("questions").size());assertFalse(attempt.path("questions").get(0).has("answer"));assertEquals(attempt.path("id"),call("POST","/api/learning/question-variants/"+id+"/attempts",Map.of("requestId",key),owner,200).path("id"));
  var result=call("POST","/api/learning/question-variants/"+id+"/submit",Map.of("attemptId",attempt.path("id").asLong(),"answers",List.of("true","false")),owner,200);assertEquals(100,result.path("score").asInt());assertEquals(0,call("GET","/api/learning/private-quizzes/attempts/"+source.attempt(),null,owner,200).path("score").asInt());assertEquals(1,call("GET","/api/learning/question-variants/"+id,null,owner,200).path("attempts").size());
  for(long u:new long[]{owner+1,owner+2})call("GET","/api/learning/question-variants/"+id,null,u,404);call("GET","/api/learning/question-variants/"+id,null,0,401);
 }

 @Test void optionOnlyAnswerCannotBecomeCertainCauseEvenWhenModelClaimsIt()throws Exception {
  var source=wrong();String response="{\"labels\":[\"CONCEPT\"],\"findings\":[{\"label\":\"CONCEPT\",\"answerQuote\":\"false\",\"basisQuote\":\"Read View用于判断版本可见性。\",\"issue\":\"无法依据选项证明的概念诊断\",\"missingKnowledge\":\"Read View\",\"practice\":\"核对依据\"}],\"uncertainty\":\"模型自称确定\",\"confidence\":\"HIGH\"}";
  when(ai.completeStructured(anyString(),anyString(),isNull(),eq(owner))).thenReturn(response);
  call("POST","/api/learning/error-annotations",Map.of("sourceType","PRIVATE_ATTEMPT","sourceId",source.attempt(),"questionIndex",0,"requestId",UUID.randomUUID().toString()),owner,500);
  assertEquals(0,sql.queryForObject("SELECT COUNT(*) FROM learning_error_revision WHERE owner_id=?",Integer.class,owner));
 }

 @Test void variantStructuredObjectMustContainExactlyOneCompleteJsonDocument()throws Exception {
  var source=wrong();when(ai.completeStructured(anyString(),anyString(),isNull(),eq(owner))).thenReturn("{\"variants\":[]} {\"variants\":[]}");
  call("POST","/api/learning/question-variants",Map.of("sourceType","PRIVATE_ATTEMPT","sourceId",source.attempt(),"questionIndex",0,"requestId",UUID.randomUUID().toString()),owner,500);
  var rows=call("GET","/api/learning/question-variants?sourceType=PRIVATE_ATTEMPT&sourceId="+source.attempt(),null,owner,200);assertEquals("FAILED",rows.get(0).path("state").asText());assertTrue(rows.get(0).path("quizId").isNull());
 }
}
