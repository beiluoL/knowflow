package com.knowflow.learning.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.h2.url=jdbc:h2:mem:v7_evidence;DB_CLOSE_DELAY=-1;MODE=MySQL", "knowflow.datasource.config-file=/tmp/knowflow-v7-evidence-no-config.json"})
class LearningEvidenceHttpTest {
 @Autowired com.knowflow.learning.evidence.LearningEvidenceService evidence;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
 @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc; @Autowired JwtUtils jwt; @Autowired ObjectMapper json;
 String auth(long id,String role){assertEquals(role,jdbc.queryForObject("SELECT role FROM sys_user WHERE id=?",String.class,id));return "Bearer "+jwt.generateToken(id,"v7-"+id,role);}
 String auth(){return auth(97101,"USER");}
 String path(long id){return "/api/learning/evidence/QUIZ_ANSWER/"+id;}
 @BeforeEach void seed() throws Exception {
  for(long id:List.of(97101L,97102L,97103L,97104L,97105L)) jdbc.update("MERGE INTO sys_user(id,username,role,deleted) KEY(id) VALUES(?,?,?,0)",id,"v7-"+id,id==97104?"ADMIN":"USER");
  jdbc.update("MERGE INTO quiz_question(id,title,content,question_type,answer,status,review_status,deleted) KEY(id) VALUES(97101,'V7真实作答','问题','TRUE_FALSE','true',1,'APPROVED',0)");
  for(long id:List.of(97101L,97102L,97103L)) {
   String snapshot=json.writeValueAsString(Map.of("contentHash",id==97103?"changed":"v1", "gradingRule","QUIZ_OBJECTIVE_V1","conditions","UNTIMED_ANSWER_VISIBLE_PRACTICE", "knowledgeIds",List.of(), "questionId",97101,"title","问题", "baselineAnswerRecordId",97101));
   jdbc.update("MERGE INTO quiz_answer_record(id,user_id,question_id,user_answer,is_correct,score,time_cost,question_snapshot,deleted) KEY(id) VALUES(?,97101,97101,?,?,?,1,?,0)",id,id==97101?"false":"true",id==97101?0:1,id==97101?0:100,snapshot);
  }
 }
 @Test void requiresAuthenticationAndOwnerEvenForAdmin() throws Exception {
  mvc.perform(get("/api/learning/evidence")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
  mvc.perform(get(path(97101))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
  mvc.perform(post(path(97101)+"/capture")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
  for(long uid:List.of(97102L,97104L))mvc.perform(post(path(97101)+"/capture").header("Authorization",auth(uid,uid==97104?"ADMIN":"USER"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  mvc.perform(post(path(999999)+"/capture").header("Authorization",auth())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
 }
 @Test void freezesZeroFirstAndRetestSeparatelyAndCaptureNeverOverwrites() throws Exception {
  for(long id:List.of(97101L,97102L))mvc.perform(post(path(id)+"/capture").header("Authorization",auth())).andExpect(status().isOk()).andExpect(jsonPath("$.data.firstSourceId").value(97101));
  String original=mvc.perform(get(path(97101)).header("Authorization",auth())).andExpect(jsonPath("$.data.score").value(0)).andReturn().getResponse().getContentAsString();
  jdbc.update("UPDATE quiz_answer_record SET score=55 WHERE id=97101");
  String replay=mvc.perform(post(path(97101)+"/capture").header("Authorization",auth())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertEquals(json.readTree(original).get("data"),json.readTree(replay).get("data"));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_evidence_receipt WHERE user_id=97101 AND source_type='QUIZ_ANSWER' AND source_id=97101",Integer.class));
  mvc.perform(get("/api/learning/evidence/comparisons").header("Authorization",auth()).param("baselineType","QUIZ_ANSWER").param("baselineId","97101").param("retestType","QUIZ_ANSWER").param("retestId","97102")).andExpect(status().isOk()).andExpect(jsonPath("$.data.comparable").value(true)).andExpect(jsonPath("$.data.scoreDifference").value(100));
 }
 @Test void rejectsChangedConditionsAndBoundsPagination() throws Exception {
  for(long id:List.of(97101L,97103L))mvc.perform(post(path(id)+"/capture").header("Authorization",auth())).andExpect(status().isOk());
  mvc.perform(get("/api/learning/evidence/comparisons").header("Authorization",auth()).param("baselineType","QUIZ_ANSWER").param("baselineId","97101").param("retestType","QUIZ_ANSWER").param("retestId","97103")).andExpect(jsonPath("$.data.comparable").value(false)).andExpect(jsonPath("$.data.scoreDifference").doesNotExist());
  for(String size:List.of("0","101"))mvc.perform(get("/api/learning/evidence").header("Authorization",auth()).param("size",size)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
  mvc.perform(get("/api/learning/evidence").header("Authorization",auth()).param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
  mvc.perform(post("/api/learning/evidence/UNKNOWN/1/capture").header("Authorization",auth())).andExpect(status().isBadRequest());
 }
 @Test void legacySnapshotIsHonestAndNoEventOrMasteryIsFabricated() throws Exception {
  jdbc.update("INSERT INTO quiz_answer_record(id,user_id,question_id,user_answer,is_correct,score) VALUES(97109,97101,97101,'true',1,100)");
  long before=jdbc.queryForObject("SELECT COUNT(*) FROM learning_event",Long.class);
  mvc.perform(post(path(97109)+"/capture").header("Authorization",auth())).andExpect(status().isOk()).andExpect(jsonPath("$.data.snapshotState").value("LEGACY_UNAVAILABLE")).andExpect(jsonPath("$.data.knowledgeIds.length()").value(0));
  assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM learning_event",Long.class));
 }
 @Test void explicitRetestRelationIsIdempotentAndCannotBeReassigned() throws Exception {
  String body="{\"baselineType\":\"QUIZ_ANSWER\",\"baselineId\":97101,\"retestType\":\"QUIZ_ANSWER\",\"retestId\":97103}";
  for(int i=0;i<2;i++)mvc.perform(post("/api/learning/evidence/retests").header("Authorization",auth()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.explicitlyLinked").value(true));
  mvc.perform(post("/api/learning/evidence/retests").header("Authorization",auth()).contentType("application/json").content(body.replace("\"baselineId\":97101","\"baselineId\":97102"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
 }
 @Test void capturesExamScaleButRejectsPendingGrade() throws Exception {
  jdbc.update("INSERT INTO exam_paper(id,name,source,status,duration_minutes,total_score) VALUES(97120,'V7原卷','SMART',1,15,10)");
  jdbc.update("INSERT INTO exam_paper_question(id,paper_id,question_id,ordinal,score,question_type,snapshot) VALUES(97120,97120,97101,1,5,'TRUE_FALSE','{\"answer\":\"true\"}')");
  jdbc.update("INSERT INTO exam_attempt(id,paper_id,user_id,mode,status) VALUES(97120,97120,97101,'EXAM','GRADED')");
  jdbc.update("INSERT INTO exam_answer(id,attempt_id,paper_question_id,user_answer,score,max_score,graded,grade_type) VALUES(97120,97120,97120,'true',5,5,1,'RULE')");
  jdbc.update("INSERT INTO exam_paper_question(id,paper_id,question_id,ordinal,score,question_type,snapshot) VALUES(97121,97120,97101,2,5,'TRUE_FALSE','{}')");
  jdbc.update("INSERT INTO exam_answer(id,attempt_id,paper_question_id,user_answer,score,max_score,graded,grade_type) VALUES(97121,97120,97121,'未评分',NULL,5,0,'PENDING')");
  mvc.perform(post("/api/learning/evidence/EXAM_ANSWER/97120/capture").header("Authorization",auth())).andExpect(status().isOk()).andExpect(jsonPath("$.data.score").value(5)).andExpect(jsonPath("$.data.maxScore").value(5));
  mvc.perform(post("/api/learning/evidence/EXAM_ANSWER/97121/capture").header("Authorization",auth())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
 }

 @Test void knowledgeScopeChecksOwnerEditorReaderRevocationAndAdminPersonalBoundary() throws Exception {
  jdbc.update("INSERT INTO doc_category(id,name,owner_id,status) VALUES(97140,'私人知识库',97101,1)");
  jdbc.update("INSERT INTO kb_member(category_id,user_id,role,status) VALUES(97140,97102,'EDITOR',1),(97140,97103,'READER',1)");
  jdbc.update("INSERT INTO quiz_question(id,title,content,question_type,answer,status,review_status,category_id) VALUES(97140,'私人题','正文','TRUE_FALSE','true',1,'APPROVED',97140)");
  for(long uid:List.of(97101L,97102L,97103L,97104L,97105L)) {
   jdbc.update("INSERT INTO quiz_answer_record(id,user_id,question_id,user_answer,score) VALUES(?,?,97140,'true',100)",uid+40,uid);
   mvc.perform(post(path(uid+40)+"/capture").header("Authorization",auth(uid,uid==97104?"ADMIN":"USER"))).andExpect(uid>=97104?status().isForbidden():status().isOk()).andExpect(jsonPath("$.code").value(uid>=97104?403:200));
  }
  String frozen=jdbc.queryForObject("SELECT snapshot_sha FROM learning_evidence_receipt WHERE source_id=97143 AND source_type='QUIZ_ANSWER'",String.class);
  mvc.perform(get("/api/learning/evidence").header("Authorization",auth(97105,"USER")).param("userId","97101").param("ownerId","97101")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.items.length()").value(0));
  mvc.perform(post(path(97141)+"/capture").header("Authorization",auth(97105,"USER")).contentType("application/json").content("{\"userId\":97101,\"ownerId\":97101}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  jdbc.update("UPDATE kb_member SET status=0 WHERE category_id=97140 AND user_id=97103");
  for(int i=0;i<2;i++) mvc.perform(get(path(97143)).header("Authorization",auth(97103,"USER"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  mvc.perform(post(path(97143)+"/capture").header("Authorization",auth(97103,"USER"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  mvc.perform(get("/api/learning/evidence").header("Authorization",auth(97103,"USER"))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.items.length()").value(0)).andExpect(jsonPath("$.data.unavailableCount").value(1));
  assertEquals(frozen,jdbc.queryForObject("SELECT snapshot_sha FROM learning_evidence_receipt WHERE source_id=97143 AND source_type='QUIZ_ANSWER'",String.class));
  jdbc.update("UPDATE kb_member SET status=1 WHERE category_id=97140 AND user_id=97103");
  mvc.perform(get(path(97143)).header("Authorization",auth(97103,"USER"))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
  jdbc.update("INSERT INTO kb_member(category_id,user_id,role,status) VALUES(97140,97104,'READER',1)");
  mvc.perform(post(path(97144)+"/capture").header("Authorization",auth(97104,"ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
  mvc.perform(get(path(97141)).header("Authorization",auth(97104,"ADMIN"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  jdbc.update("UPDATE kb_member SET status=0 WHERE category_id=97140 AND user_id=97104");
  mvc.perform(get(path(97144)).header("Authorization",auth(97104,"ADMIN"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
 }
 @Test void sourceDocumentWithdrawalDeletionAndInvalidIdsFailClosed() throws Exception {
  jdbc.update("INSERT INTO doc_category(id,name,owner_id,status) VALUES(97170,'撤下来源库',97101,1)");
  jdbc.update("INSERT INTO doc_document(id,title,category_id,status) VALUES(97170,'来源正文',97170,1)");
  jdbc.update("INSERT INTO quiz_question(id,title,content,question_type,answer,status,category_id,doc_id) VALUES(97170,'来源题','正文','TRUE_FALSE','true',1,97170,97170)");
  jdbc.update("INSERT INTO quiz_answer_record(id,user_id,question_id,user_answer,score) VALUES(97170,97101,97170,'true',100)");
  mvc.perform(post(path(97170)+"/capture").header("Authorization",auth())).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
  jdbc.update("UPDATE doc_document SET status=0 WHERE id=97170");
  mvc.perform(get(path(97170)).header("Authorization",auth())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  jdbc.update("UPDATE doc_document SET status=1,deleted=1 WHERE id=97170");
  mvc.perform(get(path(97170)).header("Authorization",auth())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  jdbc.update("UPDATE doc_document SET deleted=0 WHERE id=97170");
  jdbc.update("UPDATE doc_category SET status=0 WHERE id=97170");
  mvc.perform(get(path(97170)).header("Authorization",auth())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  jdbc.update("UPDATE doc_category SET status=1 WHERE id=97170");
  jdbc.update("UPDATE doc_category SET deleted=1 WHERE id=97170");
  mvc.perform(get(path(97170)).header("Authorization",auth())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  jdbc.update("UPDATE doc_category SET deleted=0 WHERE id=97170");
  jdbc.update("UPDATE quiz_question SET deleted=1 WHERE id=97170");
  mvc.perform(get(path(97170)).header("Authorization",auth())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  jdbc.update("UPDATE quiz_question SET deleted=0 WHERE id=97170");
  jdbc.update("UPDATE quiz_answer_record SET deleted=1 WHERE id=97170");
  mvc.perform(get(path(97170)).header("Authorization",auth())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
  mvc.perform(get(path(-1)).header("Authorization",auth())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_evidence_receipt WHERE source_id=97170",Integer.class));
 }

 @Autowired com.knowflow.service.KbMemberService members;
 @Autowired com.knowflow.security.DocumentAccessPolicy access;
 @Test void adminContextDoesNotGrantAnonymousOrOtherUsersAdministratorRights() {
  jdbc.update("INSERT INTO doc_category(id,name,owner_id,status) VALUES(97160,'身份隔离库',97101,1)");
  jdbc.update("INSERT INTO kb_member(category_id,user_id,role,status) VALUES(97160,97102,'EDITOR',1),(97160,97103,'READER',1)");
  var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
  context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(97104L,null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
  org.springframework.security.core.context.SecurityContextHolder.setContext(context);
  try {
   assertTrue(members.canViewAll(97104L),"current admin retains moderation scope");
   assertFalse(members.canViewAll(null),"anonymous probe cannot inherit admin actor");
   assertFalse(members.canViewAll(97105L),"another user cannot inherit admin actor");
   assertFalse(access.canReadCategory(97160L,null),"private category must not become public in admin request");
   assertTrue(members.canEditDocs(97160L,97102L));
   assertFalse(members.canEditDocs(97160L,97103L));
   assertFalse(members.isOwner(97160L,97103L));
  } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
  assertTrue(members.canViewAll(97104L),"explicit DB admin works without thread authentication");
  assertFalse(members.canViewAll(97105L));
 }

 @Test void concurrentCaptureHasOneReceiptAndRollbackLeavesNoOrphan() throws Exception {
  jdbc.update("INSERT INTO quiz_answer_record(id,user_id,question_id,user_answer,score) VALUES(97150,97101,97101,'true',100),(97151,97101,97101,'false',0)");
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
  try {
   var a=pool.submit(()->{start.await();return evidence.capture(97101,"QUIZ_ANSWER",97150);});
   var b=pool.submit(()->{start.await();return evidence.capture(97101,"QUIZ_ANSWER",97150);});start.countDown();
   assertEquals(a.get(10,java.util.concurrent.TimeUnit.SECONDS),b.get(10,java.util.concurrent.TimeUnit.SECONDS));
  } finally { pool.shutdownNow(); }
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_evidence_receipt WHERE source_type='QUIZ_ANSWER' AND source_id=97150",Integer.class));
  var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
  assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{evidence.capture(97101,"QUIZ_ANSWER",97151);throw new IllegalStateException("forced rollback");}));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_evidence_receipt WHERE source_type='QUIZ_ANSWER' AND source_id=97151",Integer.class));
 }

}
