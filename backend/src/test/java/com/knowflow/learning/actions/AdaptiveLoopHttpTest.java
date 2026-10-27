package com.knowflow.learning.actions;
import com.fasterxml.jackson.databind.*;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.config-file=/tmp/knowflow-phase4h-test-no-config.json","knowflow.datasource.h2.url=jdbc:h2:mem:adaptive_4h;DB_CLOSE_DELAY=-1;MODE=MySQL"})
class AdaptiveLoopHttpTest {
 @Autowired MockMvc http;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;@Autowired JwtUtils jwt;@Autowired SysUserMapper users;@Autowired QuizQuestionMapper questions;@Autowired DailyLearningPlanService plans;@Autowired DocCategoryMapper categories;@Autowired KbMemberMapper members;
 long actor(){var u=new SysUser();u.setUsername("4h-"+UUID.randomUUID());u.setPassword("unused");u.setRole("USER");users.insert(u);return u.getId();}
 String auth(long u){return "Bearer "+jwt.generateToken(u,"4h","USER");}
 JsonNode send(String path,long u,Object body,int code)throws Exception{return json.readTree(http.perform(post(path).header("Authorization",auth(u)).contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().is(code)).andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");}
 JsonNode preview(long u)throws Exception{return send("/api/learning/action-plans/daily/preview",u,Map.of("availableMinutes",30,"includeInterview",false),200);}
 JsonNode confirm(long u,JsonNode p)throws Exception{var edits=new ArrayList<Map<String,Object>>();for(var i:p.path("tasks"))edits.add(Map.of("key",i.path("key").asText(),"title",i.path("title").asText(),"estimatedMinutes",i.path("estimatedMinutes").asInt(),"priority",i.path("priority").asText()));return send("/api/learning/action-plans/"+p.path("planId").asText()+"/confirm",u,Map.of("version",p.path("version").asInt(),"confirmationToken",p.path("confirmationToken").asText(),"idempotencyKey","confirm_"+UUID.randomUUID(),"tasks",edits),200);}
 @Test void separateDraftsDoNotDuplicateTodayTasks()throws Exception{long u=actor();var a=preview(u);var b=preview(u);var x=confirm(u,a);var y=confirm(u,b);assertEquals(x.path("createdTasks"),y.path("createdTasks"));assertEquals(a.path("tasks").size(),db.queryForObject("SELECT COUNT(*) FROM task WHERE user_id=?",Integer.class,u));}
 @Test void realDueCardIsPrioritizedAndOnlyRealReviewCompletesItsTask()throws Exception{
  long u=actor();var c=send("/api/learning/my/flashcards",u,Map.of("front","volatile","back","可见性"),200);long id=c.path("id").asLong();db.update("UPDATE learning_flashcard SET next_review_time=? WHERE id=?",java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(1)),id);
  var p=preview(u);assertEquals("FLASHCARD",p.path("tasks").get(0).path("resourceType").asText());assertEquals(id,p.path("tasks").get(0).path("resourceId").asLong());var saved=confirm(u,p);long task=saved.path("createdTasks").get(0).path("id").asLong();assertEquals(0,db.queryForObject("SELECT status FROM task WHERE id=?",Integer.class,task));
  var schedule=json.readTree(http.perform(get("/api/learning/flashcards/"+id+"/schedule").header("Authorization",auth(u))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
  send("/api/learning/flashcards/"+id+"/fsrs-review",u,Map.of("rating",3,"revision",schedule.path("revision").asLong(),"settingsRevision",schedule.path("settingsRevision").asLong(),"requestId",UUID.randomUUID().toString(),"sessionId","phase4h-session-valid"),200);
  assertEquals(1,db.queryForObject("SELECT status FROM task WHERE id=?",Integer.class,task));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=?",Integer.class,u));
 }
 @Test void repeatedQuizRequestDoesNotDuplicateObjectiveEvidence()throws Exception{
  long u=actor();var q=new QuizQuestion();q.setTitle("CAS的比较交换");q.setContent("CAS原子比较交换？");q.setQuestionType("TRUE_FALSE");q.setAnswer("true");q.setStatus(1);q.setReviewStatus("APPROVED");questions.insert(q);
  var body=Map.of("requestId",UUID.randomUUID().toString(),"answers",List.of(Map.of("questionId",q.getId(),"userAnswer","false")));
  var a=send("/api/quiz/submit",u,body,200);var b=send("/api/quiz/submit",u,body,200);assertEquals(a,b);var trace=json.readTree(db.queryForObject("SELECT metadata FROM learning_event WHERE user_id=? AND event_type='QUESTION_ANSWERED'",String.class,u));assertEquals(db.queryForObject("SELECT id FROM quiz_answer_record WHERE user_id=?",Long.class,u).longValue(),trace.path("answerRecordId").asLong());assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM quiz_answer_record WHERE user_id=?",Integer.class,u));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='QUESTION_ANSWERED'",Integer.class,u));
 }

 @Test void scoringEligibilityDoesNotDependOnOptionalRequestId()throws Exception{
  long u=actor();for(String type:List.of("SHORT_ANSWER","CODE")){var q=new QuizQuestion();q.setTitle(type);q.setContent("not objective");q.setQuestionType(type);q.setAnswer("answer");q.setStatus(1);questions.insert(q);send("/api/quiz/submit",u,Map.of("answers",List.of(Map.of("questionId",q.getId(),"userAnswer","answer"))),400);}
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM quiz_answer_record WHERE user_id=?",Integer.class,u));
 }
 @Test void reopenedTaskCanCompleteAgainAndUnrelatedBacklogCannotHideIt()throws Exception{
  long u=actor();for(int i=0;i<101;i++)db.update("INSERT INTO task(user_id,title,status,scheduled_date,learning_plan_ref,learning_metadata,deleted) VALUES(?,?,0,CURRENT_DATE,'old',?,0)",u,"unrelated "+i,"{\"resourceType\":\"TOOL\",\"resourceId\":null}");
  var c=send("/api/learning/my/flashcards",u,Map.of("front","CAS backlog","back","原子比较交换"),200);long id=c.path("id").asLong();db.update("UPDATE learning_flashcard SET next_review_time=? WHERE id=?",java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(1)),id);
  var saved=confirm(u,preview(u));long task=saved.path("createdTasks").get(0).path("id").asLong();
  for(int round=0;round<2;round++){
   var schedule=json.readTree(http.perform(get("/api/learning/flashcards/"+id+"/schedule").header("Authorization",auth(u))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
   send("/api/learning/flashcards/"+id+"/fsrs-review",u,Map.of("rating",3,"revision",schedule.path("revision").asLong(),"settingsRevision",schedule.path("settingsRevision").asLong(),"requestId",UUID.randomUUID().toString(),"sessionId","phase4h-reopen-test"),200);
   assertEquals(1,db.queryForObject("SELECT status FROM task WHERE id=?",Integer.class,task));if(round==0)db.update("UPDATE task SET status=0 WHERE id=?",task);
  }
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM learning_task_evidence WHERE task_id=?",Integer.class,task));
 }

 @Test void retiredIdentityCanBeReplacedWithoutResurrectingDeletedTask()throws Exception{long u=actor();var first=confirm(u,preview(u));long old=first.path("createdTasks").get(0).path("id").asLong();db.update("UPDATE task SET deleted=1 WHERE id=?",old);var next=confirm(u,preview(u));assertNotEquals(old,next.path("createdTasks").get(0).path("id").asLong());assertEquals(1,db.queryForObject("SELECT deleted FROM task WHERE id=?",Integer.class,old));}
 @Test void reschedulingSameResourceCollisionReturns409AndPreservesDates()throws Exception{
  long u=actor();var yesterday=send("/api/learning/action-plans/daily/preview",u,Map.of("date",LocalDate.now().minusDays(1).toString(),"availableMinutes",30,"includeInterview",false),200);confirm(u,yesterday);confirm(u,preview(u));
  var before=db.queryForList("SELECT id,scheduled_date FROM task WHERE user_id=? ORDER BY id",u);
  var request=new PlanModels.Request();request.setAvailableMinutes(60);var draft=plans.previewChanges(u,request);var c=new PlanModels.ConfirmRequest();c.setVersion(draft.version());c.setConfirmationToken(draft.confirmationToken());c.setIdempotencyKey(UUID.randomUUID().toString());c.setTasks(draft.tasks().stream().map(x->new PlanModels.EditedItem(x.key(),x.title(),x.estimatedMinutes(),x.priority())).toList());
  assertEquals(409,assertThrows(com.knowflow.exception.BusinessException.class,()->plans.confirm(u,draft.planId(),c)).getCode());assertEquals(before,db.queryForList("SELECT id,scheduled_date FROM task WHERE user_id=? ORDER BY id",u));
 }
 @Test void receiptFailureRollsBackAnswersMistakesAndEvents()throws Exception{
  long u=actor();var q=new QuizQuestion();q.setTitle("rollback CAS");q.setContent("CAS?");q.setQuestionType("TRUE_FALSE");q.setAnswer("true");q.setStatus(1);questions.insert(q);
  db.execute("ALTER TABLE quiz_submit_receipt ADD CONSTRAINT phase4h_fail_receipt CHECK (request_id <> 'failure_receipt_4h')");
  try{send("/api/quiz/submit",u,Map.of("requestId","failure_receipt_4h","answers",List.of(Map.of("questionId",q.getId(),"userAnswer","false"))),500);}finally{db.execute("ALTER TABLE quiz_submit_receipt DROP CONSTRAINT phase4h_fail_receipt");}
  for(String table:List.of("quiz_answer_record","learning_mistake","learning_event"))assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE user_id=?",Integer.class,u));
 }
 @Test void concurrentQuizRequestsCreateOneAnswerAndOneCoreEvent()throws Exception{
  long u=actor();var q=new QuizQuestion();q.setTitle("concurrent CAS");q.setContent("CAS?");q.setQuestionType("TRUE_FALSE");q.setAnswer("true");q.setStatus(1);questions.insert(q);
  var body=Map.of("requestId",UUID.randomUUID().toString(),"answers",List.of(Map.of("questionId",q.getId(),"userAnswer","true")));var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
  try{var results=executor.invokeAll(List.<java.util.concurrent.Callable<JsonNode>>of(()->send("/api/quiz/submit",u,body,200),()->send("/api/quiz/submit",u,body,200)));assertEquals(results.get(0).get(),results.get(1).get());}finally{executor.shutdownNow();}
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM quiz_answer_record WHERE user_id=?",Integer.class,u));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='QUESTION_ANSWERED'",Integer.class,u));
 }
 @Test void exactQuestionAccessChecksReaderEditorAndRevocation()throws Exception{
  long owner=actor(),reader=actor();var category=new DocCategory();category.setName("private-source");category.setStatus(1);category.setOwnerId(owner);categories.insert(category);
  var q=new QuizQuestion();q.setTitle("private CAS");q.setContent("CAS?");q.setQuestionType("TRUE_FALSE");q.setAnswer("true");q.setStatus(1);q.setCategoryId(category.getId());questions.insert(q);
  http.perform(get("/api/quiz/questions").param("questionId",q.getId().toString()).header("Authorization",auth(reader))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  var member=new KbMember();member.setCategoryId(category.getId());member.setUserId(reader);member.setStatus(1);member.setRole("READER");members.insert(member);
  for(String role:List.of("READER","EDITOR")){member.setRole(role);members.updateById(member);http.perform(get("/api/quiz/questions").param("questionId",q.getId().toString()).header("Authorization",auth(reader))).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(q.getId()));}
  member.setStatus(0);members.updateById(member);http.perform(get("/api/quiz/questions").param("questionId",q.getId().toString()).header("Authorization",auth(reader))).andExpect(status().isForbidden());
 }
}
