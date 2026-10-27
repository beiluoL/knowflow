package com.knowflow.learning.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.h2.url=jdbc:h2:mem:v70_t02;DB_CLOSE_DELAY=-1;MODE=MySQL","knowflow.datasource.config-file=/tmp/v70-t02-no-config.json"})
class LearningEvidenceReliabilityTest {
 @Autowired JdbcTemplate jdbc; @Autowired LearningEvidenceService evidence; @Autowired ObjectMapper json;
 static final long USER=97201;
 @BeforeEach void seed(){jdbc.update("MERGE INTO sys_user(id,username,role,deleted) KEY(id) VALUES(97201,'t02-owner','USER',0)");}
 void question(long q){jdbc.update("INSERT INTO quiz_question(id,title,content,question_type,answer,status,review_status) VALUES(?,'T02题','正文','TRUE_FALSE','true',1,'APPROVED')",q);}
 String snap(String hash,Long baseline)throws Exception {var m=new LinkedHashMap<String,Object>();m.put("contentHash",hash);m.put("gradingRule","QUIZ_OBJECTIVE_V1");m.put("conditions","UNTIMED_ANSWER_VISIBLE_PRACTICE");m.put("knowledgeIds",List.of());if(baseline!=null)m.put("baselineAnswerRecordId",baseline);return json.writeValueAsString(m);}
 void answer(long id,long q,int score,String time,String snapshot,int deleted){jdbc.update("INSERT INTO quiz_answer_record(id,user_id,question_id,user_answer,score,is_correct,create_time,question_snapshot,deleted) VALUES(?,97201,?,'原始回答',?,?,CAST(? AS TIMESTAMP),?,?)",id,q,score,score==100?1:0,time,snapshot,deleted);}
 @Test void firstKnownUsesOccurrenceIncludingDeletedAndDoesNotInventCompleteness()throws Exception{
  question(97210);answer(97300,97210,0,"2026-01-01 00:00:00",null,1);
  for(int n=0;n<55;n++)answer(97210+n,97210,100,"2026-02-01 00:00:00",snap("same",null),0);
  var receipt=evidence.capture(USER,"QUIZ_ANSWER",97264);
  assertEquals(97300L,((Number)receipt.get("firstSourceId")).longValue());
  assertEquals("INCOMPLETE",receipt.get("historyCompleteness"));assertNull(receipt.get("firstAttemptRef"));
  assertEquals("原始回答",jdbc.queryForObject("SELECT user_answer FROM quiz_answer_record WHERE id=97300",String.class));
  assertEquals(0,jdbc.queryForObject("SELECT score FROM quiz_answer_record WHERE id=97300",Integer.class));
 }
 @Test void chronologyAllowsImportedIdsAndRejectsReverseTime()throws Exception{
  question(97400);answer(97402,97400,0,"2026-01-01 00:00:00",snap("same",null),0);answer(97401,97400,100,"2026-01-02 00:00:00",snap("same",null),0);
  var result=evidence.link(USER,new LearningEvidenceService.Retest("QUIZ_ANSWER",97402L,"QUIZ_ANSWER",97401L));
  assertEquals(true,result.get("comparable"));assertEquals(100D,((Number)result.get("scoreDifference")).doubleValue());
 }
 @Test void notComparableHasSpecificReasonsWithoutFabricatedDifference()throws Exception{
  question(97410);answer(97410,97410,0,"2026-01-01 00:00:00",snap("old",null),0);answer(97411,97410,100,"2026-01-02 00:00:00",snap("new",97410L),0);
  evidence.capture(USER,"QUIZ_ANSWER",97410);evidence.capture(USER,"QUIZ_ANSWER",97411);var result=evidence.compare(USER,"QUIZ_ANSWER",97410,"QUIZ_ANSWER",97411);
  assertEquals("NOT_COMPARABLE",result.get("comparability"));assertTrue(((List<?>)result.get("reasons")).contains("QUESTION_VERSION_CHANGED"));assertFalse(result.containsKey("scoreDifference"));
 }
 @Test void legacyNullSnapshotIsUnknownAndScoreIsNotObjective()throws Exception{
  question(97420);answer(97420,97420,100,"2026-01-01 00:00:00",null,0);var r=evidence.capture(USER,"QUIZ_ANSWER",97420);
  assertEquals("UNKNOWN",r.get("historyCompleteness"));assertNull(r.get("firstAttemptRef"));assertEquals("ACTIVITY_ONLY",r.get("assessmentKind"));
 }
 @Test void pendingDoesNotFreezeAndLaterGradeIsCapturedOnce()throws Exception{
  jdbc.update("INSERT INTO exam_paper(id,name,source,status,duration_minutes,total_score) VALUES(97430,'T02卷','SMART',1,15,10)");
  jdbc.update("INSERT INTO exam_paper_question(id,paper_id,ordinal,score,question_type,snapshot) VALUES(97430,97430,1,10,'TRUE_FALSE','{\"answer\":\"true\"}')");
  jdbc.update("INSERT INTO exam_attempt(id,paper_id,user_id,mode,status) VALUES(97430,97430,97201,'EXAM','GRADED')");
  jdbc.update("INSERT INTO exam_answer(id,attempt_id,paper_question_id,user_answer,score,max_score,graded,grade_type) VALUES(97430,97430,97430,'保留原答',NULL,10,0,'PENDING')");
  assertEquals(409,assertThrows(BusinessException.class,()->evidence.capture(USER,"EXAM_ANSWER",97430)).getCode());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_evidence_receipt WHERE source_id=97430",Integer.class));
  jdbc.update("UPDATE exam_answer SET score=8,graded=1,grade_type='RULE' WHERE id=97430");
  var first=evidence.capture(USER,"EXAM_ANSWER",97430);jdbc.update("UPDATE exam_answer SET score=9 WHERE id=97430");
  assertEquals(first,evidence.capture(USER,"EXAM_ANSWER",97430));assertEquals("保留原答",first.get("answer"));
 }

 @Autowired com.knowflow.service.QuizService quiz;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
 com.knowflow.dto.QuizSubmitDTO submission(long q,String key,String answer){var dto=new com.knowflow.dto.QuizSubmitDTO();dto.setRequestId(key);var item=new com.knowflow.dto.QuizSubmitDTO.Item();item.setQuestionId(q);item.setUserAnswer(answer);dto.setAnswers(List.of(item));return dto;}
 @Test void newSubmissionsPreserveTextTrackedFirstAndIdempotentOriginal()throws Exception{
  question(97450);var input=submission(97450,"t02-replay-01","  false\n");quiz.submit(input,USER);quiz.submit(input,USER);
  long first=jdbc.queryForObject("SELECT id FROM quiz_answer_record WHERE question_id=97450",Long.class);
  assertEquals("  false\n",jdbc.queryForObject("SELECT user_answer FROM quiz_answer_record WHERE id=?",String.class,first));
  assertEquals(409,assertThrows(BusinessException.class,()->quiz.submit(submission(97450,"t02-replay-01","true"),USER)).getCode());
  quiz.submit(submission(97450,"t02-replay-02","true"),USER);long next=jdbc.queryForObject("SELECT MAX(id) FROM quiz_answer_record WHERE question_id=97450",Long.class);
  var r=evidence.get(USER,"QUIZ_ANSWER",next);assertEquals("UNKNOWN",r.get("historyCompleteness"));assertNull(r.get("firstAttemptRef"));assertEquals(first,((Number)((Map<?,?>)r.get("firstTrackedAttemptRef")).get("id")).longValue());
  assertEquals(true,r.get("answerExposed"));assertEquals("GRADED",r.get("assessmentStatus"));
  var live=(Map<?,?>)r.get("historyProjection");assertEquals(next,((Number)((Map<?,?>)live.get("latestQualifiedAttemptRef")).get("id")).longValue());assertEquals(100,((Number)live.get("bestScore")).intValue());
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM quiz_answer_record WHERE question_id=97450",Integer.class));
  assertEquals(0,jdbc.queryForObject("SELECT score FROM quiz_answer_record WHERE id=?",Integer.class,first));
 }
 @Test void receiptFailureRollsBackOriginalAnswerAndEventAndCanRetry()throws Exception{
  question(97460);long before=jdbc.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=97201",Long.class);
  jdbc.execute("ALTER TABLE learning_evidence_receipt ADD CONSTRAINT t02_fail_receipt CHECK(resource_key <> 'QUIZ:97460')");
  try{assertThrows(RuntimeException.class,()->quiz.submit(submission(97460,"t02-rollback-01","true"),USER));}
  finally{jdbc.execute("ALTER TABLE learning_evidence_receipt DROP CONSTRAINT t02_fail_receipt");}
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM quiz_answer_record WHERE question_id=97460",Integer.class));
  assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=97201",Long.class));
  quiz.submit(submission(97460,"t02-rollback-01","true"),USER);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM quiz_answer_record WHERE question_id=97460",Integer.class));
 }

 @Test void firstTrackedSubmissionHasExplicitProvenanceAndQualifiedScale() {
  question(97470);quiz.submit(submission(97470,"t02-tracked-01","true"),USER);
  long id=jdbc.queryForObject("SELECT id FROM quiz_answer_record WHERE question_id=97470",Long.class);
  var r=evidence.get(USER,"QUIZ_ANSWER",id);
  assertAll(()->assertEquals("UNKNOWN",r.get("historyCompleteness")),()->assertNull(r.get("firstAttemptRef")),()->assertNotNull(r.get("firstTrackedAttemptRef")),()->assertEquals(true,r.get("answerExposed")),()->assertEquals("GRADED",r.get("assessmentStatus")),()->assertNotNull(((Map<?,?>)r.get("historyProjection")).get("latestQualifiedAttemptRef")),()->assertEquals(100,((Map<?,?>)r.get("historyProjection")).get("bestScore")));
 }

 @Test void pagingCountsOwnScannedReceiptsAndDoesNotReparseLongRawAnswers()throws Exception{
  long actor=97500;jdbc.update("INSERT INTO sys_user(id,username,role) VALUES(97500,'t02-page-owner','USER'),(97501,'t02-page-other','USER')");
  jdbc.update("INSERT INTO doc_category(id,name,owner_id,status) VALUES(97500,'撤权分页库',97501,1)");
  jdbc.update("INSERT INTO kb_member(category_id,user_id,role,status) VALUES(97500,97500,'READER',1)");question(97500);question(97501);jdbc.update("UPDATE quiz_question SET category_id=97500 WHERE id=97500");
  for(int n=0;n<3;n++){long id=97500+n;jdbc.update("INSERT INTO quiz_answer_record(id,user_id,question_id,user_answer,score,create_time,question_snapshot) VALUES(?,97500,?,?,100,CAST(? AS TIMESTAMP),?)",id,n==0?97501:97500,"x".repeat(50000),"2026-01-0"+(n+1)+" 00:00:00",snap("paging",null));evidence.capture(actor,"QUIZ_ANSWER",id);}
  jdbc.update("UPDATE quiz_answer_record SET question_snapshot='{broken source json' WHERE id=97500");
  var all=evidence.page(actor,1,2,null,null);assertEquals(3L,((Number)all.get("total")).longValue());assertEquals(2,((List<?>)all.get("items")).size());assertEquals(true,all.get("hasMore"));
  jdbc.update("UPDATE kb_member SET status=0 WHERE category_id=97500");
  var hidden=evidence.page(actor,1,2,null,null);assertEquals(0,((List<?>)hidden.get("items")).size());assertEquals(2,hidden.get("unavailableCount"));assertEquals(2,hidden.get("scannedCount"));assertEquals("OWN_RECEIPTS_INCLUDING_UNAVAILABLE",hidden.get("totalSemantics"));assertEquals(true,hidden.get("hasMore"));
  var second=evidence.page(actor,2,2,null,null);assertEquals(1,((List<?>)second.get("items")).size());assertEquals(false,second.get("hasMore"));
  assertEquals(0L,((Number)evidence.page(97501,1,2,null,null).get("total")).longValue());
 }

 @Test void missingObjectiveGradeIsPendingAndInvalidScaleIsRejected()throws Exception{
  question(97520);answer(97520,97520,0,"2026-01-01 00:00:00",snap("pending",null),0);jdbc.update("UPDATE quiz_answer_record SET score=NULL WHERE id=97520");
  assertEquals(409,assertThrows(BusinessException.class,()->evidence.capture(USER,"QUIZ_ANSWER",97520)).getCode());
  jdbc.update("UPDATE quiz_answer_record SET score=101 WHERE id=97520");assertEquals(409,assertThrows(BusinessException.class,()->evidence.capture(USER,"QUIZ_ANSWER",97520)).getCode());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_evidence_receipt WHERE source_id=97520",Integer.class));
 }
 @Test void recordedHintsOrExposureChangesPreventScoreDifference()throws Exception{
  question(97530);var a=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(snap("hints",null));a.put("hintCount",0);
  var b=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(snap("hints",97530L));b.put("hintCount",1);
  answer(97530,97530,0,"2026-01-01 00:00:00",json.writeValueAsString(a),0);answer(97531,97530,100,"2026-01-02 00:00:00",json.writeValueAsString(b),0);
  evidence.capture(USER,"QUIZ_ANSWER",97530);evidence.capture(USER,"QUIZ_ANSWER",97531);var result=evidence.compare(USER,"QUIZ_ANSWER",97530,"QUIZ_ANSWER",97531);
  assertEquals(false,result.get("comparable"));assertTrue(((List<?>)result.get("reasons")).contains("HINT_USAGE_CHANGED"));assertFalse(result.containsKey("scoreDifference"));
 }

 @Test void historyReadProjectionAdvancesWithoutRewritingFrozenFirstReceipt()throws Exception{
  question(97540);quiz.submit(submission(97540,"t02-live-01","false"),USER);long first=jdbc.queryForObject("SELECT id FROM quiz_answer_record WHERE question_id=97540",Long.class);
  var frozen=evidence.get(USER,"QUIZ_ANSWER",first);quiz.submit(submission(97540,"t02-live-02","true"),USER);long latest=jdbc.queryForObject("SELECT MAX(id) FROM quiz_answer_record WHERE question_id=97540",Long.class);
  var again=evidence.get(USER,"QUIZ_ANSWER",first);assertEquals(frozen.get("receiptSha"),again.get("receiptSha"));assertEquals(0,again.get("score"));
  var projection=(Map<?,?>)again.get("historyProjection");assertNotNull(projection);assertEquals(latest,((Number)((Map<?,?>)projection.get("latestQualifiedAttemptRef")).get("id")).longValue());assertEquals(100,projection.get("bestScore"));
  jdbc.update("UPDATE quiz_answer_record SET deleted=1 WHERE id=?",first);
  var next=evidence.get(USER,"QUIZ_ANSWER",latest);assertEquals("INCOMPLETE",((Map<?,?>)next.get("historyProjection")).get("historyCompleteness"));
  assertEquals(first,((Number)((Map<?,?>)((Map<?,?>)next.get("historyProjection")).get("firstTrackedAttemptRef")).get("id")).longValue());
 }

 @Test void originalHistoryLostBeforeTrackingStaysUnknown()throws Exception{
  question(97560);answer(97560,97560,0,"2025-01-01 00:00:00",null,0);jdbc.update("DELETE FROM quiz_answer_record WHERE id=97560");
  quiz.submit(submission(97560,"t02-missing-original","true"),USER);long id=jdbc.queryForObject("SELECT id FROM quiz_answer_record WHERE question_id=97560",Long.class);var r=evidence.get(USER,"QUIZ_ANSWER",id);
  assertNull(r.get("firstAttemptRef"));assertEquals("UNKNOWN",r.get("historyCompleteness"));assertNotNull(r.get("firstTrackedAttemptRef"));
 }
 @Test void qualifiedMetricsExcludeHintChangesJustLikeComparison()throws Exception{
  question(97570);var a=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(snap("metrics-hint",null));a.put("hintCount",0);var b=a.deepCopy();b.put("hintCount",1);
  answer(97570,97570,0,"2026-01-01 00:00:00",json.writeValueAsString(a),0);answer(97571,97570,100,"2026-01-02 00:00:00",json.writeValueAsString(b),0);
  evidence.capture(USER,"QUIZ_ANSWER",97570);evidence.capture(USER,"QUIZ_ANSWER",97571);var projection=(Map<?,?>)evidence.get(USER,"QUIZ_ANSWER",97570).get("historyProjection");
  assertEquals(0,projection.get("bestScore"));assertEquals(97570L,((Number)((Map<?,?>)projection.get("latestQualifiedAttemptRef")).get("id")).longValue());
 }
 @Test void preciseServerTimesOrderAttemptsWhenDatabaseTimestampsHaveSecondPrecision()throws Exception{
  question(97580);var a=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(snap("mysql-second",null));a.put("answeredAt","2026-01-01T00:00:00.000001");var b=a.deepCopy();b.put("answeredAt","2026-01-01T00:00:00.000002");
  answer(97580,97580,0,"2026-01-01 00:00:00",json.writeValueAsString(a),0);answer(97581,97580,100,"2026-01-01 00:00:00",json.writeValueAsString(b),0);
  assertEquals(true,evidence.link(USER,new LearningEvidenceService.Retest("QUIZ_ANSWER",97580L,"QUIZ_ANSWER",97581L)).get("comparable"));
 }

 @Test void unrelatedMalformedLegacyStatisticsCannotRollBackNewAnswer()throws Exception{
  question(97590);quiz.submit(submission(97590,"t02-statistics-first","false"),USER);
  jdbc.update("INSERT INTO quiz_answer_record(user_id,question_id,user_answer,score,question_snapshot) VALUES(97201,97590,'旧输入',0,'{bad historical json')");
  assertDoesNotThrow(()->quiz.submit(submission(97590,"t02-statistics-next","true"),USER));
  assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM quiz_answer_record WHERE question_id=97590",Integer.class));
 }

 @Autowired com.knowflow.service.LearningEventService events;
 @Test void requiredEventCannotPointToADifferentOriginalQuestion()throws Exception{
  question(97610);question(97611);answer(97610,97610,0,"2026-01-01 00:00:00",snap("event-link",null),0);
  long before=jdbc.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=97201",Long.class);
  assertEquals(409,assertThrows(BusinessException.class,()->events.recordRequired(USER,"QUESTION_ANSWERED","QUIZ",97611L,Map.of("answerRecordId",97610))).getCode());
  assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=97201",Long.class));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_evidence_receipt WHERE source_id=97610",Integer.class));
 }
 @Test void latestProjectionUsesPreservedPreciseTimeRatherThanImportIdOrder()throws Exception{
  question(97620);var a=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(snap("latest-import",null));a.put("answeredAt","2026-01-01T00:00:00.000001");var b=a.deepCopy();b.put("answeredAt","2026-01-01T00:00:00.000002");
  answer(97622,97620,0,"2026-01-01 00:00:00",json.writeValueAsString(a),0);answer(97621,97620,100,"2026-01-01 00:00:00",json.writeValueAsString(b),0);
  evidence.capture(USER,"QUIZ_ANSWER",97622);evidence.capture(USER,"QUIZ_ANSWER",97621);var projection=(Map<?,?>)evidence.get(USER,"QUIZ_ANSWER",97622).get("historyProjection");
  assertEquals(97621L,((Number)((Map<?,?>)projection.get("latestQualifiedAttemptRef")).get("id")).longValue());
  assertEquals(97622L,((Number)((Map<?,?>)projection.get("firstKnownAttemptRef")).get("id")).longValue());
 }

 @Test void republishingQuestionCannotExposeItsOldRevokedSnapshot()throws Exception{
  jdbc.update("INSERT INTO sys_user(id,username,role) VALUES(97640,'t02-private-source-owner','USER')");
  jdbc.update("INSERT INTO doc_category(id,name,owner_id,status) VALUES(97640,'原私人来源',97640,1)");
  jdbc.update("INSERT INTO doc_document(id,title,content,category_id,status) VALUES(97640,'原文','私人正文',97640,1)");
  jdbc.update("INSERT INTO kb_member(category_id,user_id,role,status) VALUES(97640,97201,'READER',1)");question(97640);
  jdbc.update("UPDATE quiz_question SET category_id=97640,doc_id=97640 WHERE id=97640");
  var snap=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(snap("private-original",null));snap.put("categoryId",97640);snap.put("docId",97640);snap.put("content","不可泄漏的原题正文");
  answer(97640,97640,0,"2026-01-01 00:00:00",json.writeValueAsString(snap),0);evidence.capture(USER,"QUIZ_ANSWER",97640);
  jdbc.update("UPDATE kb_member SET status=0 WHERE category_id=97640");jdbc.update("UPDATE quiz_question SET category_id=NULL,doc_id=NULL WHERE id=97640");
  assertAll(()->assertEquals(403,assertThrows(BusinessException.class,()->evidence.get(USER,"QUIZ_ANSWER",97640)).getCode()),()->assertTrue(((List<?>)evidence.page(USER,1,100,null,null).get("items")).stream().noneMatch(v->((Number)((Map<?,?>)v).get("sourceId")).longValue()==97640)));
 }

 @Test void realQuestionRevisionDoesNotRewriteOriginalOrProduceComparableDifference()throws Exception{
  question(97700);quiz.submit(submission(97700,"t02-version-first","false"),USER);long first=jdbc.queryForObject("SELECT id FROM quiz_answer_record WHERE question_id=97700",Long.class);String original=jdbc.queryForObject("SELECT question_snapshot FROM quiz_answer_record WHERE id=?",String.class,first);
  jdbc.update("UPDATE quiz_question SET content='新版正文',version=version+1 WHERE id=97700");
  var input=submission(97700,"t02-version-next","true");input.getAnswers().get(0).setExpectedContentHash(quiz.practiceQuestion(97700L,USER).getContentHash());input.getAnswers().get(0).setBaselineAnswerRecordId(first);quiz.submit(input,USER);
  long next=jdbc.queryForObject("SELECT MAX(id) FROM quiz_answer_record WHERE question_id=97700",Long.class);var comparison=evidence.compare(USER,"QUIZ_ANSWER",first,"QUIZ_ANSWER",next);
  assertEquals("NOT_COMPARABLE",comparison.get("comparability"));assertTrue(((List<?>)comparison.get("reasons")).contains("QUESTION_VERSION_CHANGED"));assertFalse(comparison.containsKey("scoreDifference"));assertEquals(original,jdbc.queryForObject("SELECT question_snapshot FROM quiz_answer_record WHERE id=?",String.class,first));assertEquals(0,jdbc.queryForObject("SELECT score FROM quiz_answer_record WHERE id=?",Integer.class,first));
 }
 @Test void concurrentRevocationKeepsCountsConsistentAndFollowingRequestsHidden()throws Exception{
  jdbc.update("INSERT INTO sys_user(id,username,role) VALUES(97710,'t02-concurrent-reader','USER'),(97711,'t02-concurrent-owner','USER')");jdbc.update("INSERT INTO doc_category(id,name,owner_id,status) VALUES(97710,'并发撤权',97711,1)");jdbc.update("INSERT INTO kb_member(category_id,user_id,role,status) VALUES(97710,97710,'READER',1)");question(97710);jdbc.update("UPDATE quiz_question SET category_id=97710 WHERE id=97710");
  for(int n=0;n<5;n++){jdbc.update("INSERT INTO quiz_answer_record(id,user_id,question_id,user_answer,score,question_snapshot) VALUES(?,97710,97710,'true',100,?)",97710+n,snap("concurrent-revoke",null));evidence.capture(97710,"QUIZ_ANSWER",97710+n);}
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
  try{
   var reads=pool.submit(()->{start.await();for(int n=0;n<20;n++){var page=evidence.page(97710,1,3,null,null);assertEquals(5L,((Number)page.get("total")).longValue());assertEquals(3,((List<?>)page.get("items")).size()+((Number)page.get("unavailableCount")).intValue());assertEquals(true,page.get("hasMore"));}return true;});
   var revoke=pool.submit(()->{start.await();jdbc.update("UPDATE kb_member SET status=0 WHERE category_id=97710 AND user_id=97710");return true;});start.countDown();assertTrue(revoke.get(10,java.util.concurrent.TimeUnit.SECONDS));assertTrue(reads.get(20,java.util.concurrent.TimeUnit.SECONDS));
  }finally{pool.shutdownNow();}
  var after=evidence.page(97710,1,3,null,null);assertEquals(0,((List<?>)after.get("items")).size());assertEquals(3,after.get("unavailableCount"));assertEquals(0L,((Number)evidence.page(97711,1,3,null,null).get("total")).longValue());
 }

 @Test void legacyFrozenMissingGradeCannotBeCalledComparable()throws Exception{
  question(97800);answer(97800,97800,0,"2026-01-01 00:00:00",snap("legacy-grade",null),0);answer(97801,97800,100,"2026-01-02 00:00:00",snap("legacy-grade",97800L),0);
  var first=evidence.capture(USER,"QUIZ_ANSWER",97800);evidence.capture(USER,"QUIZ_ANSWER",97801);
  var stored=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(jdbc.queryForObject("SELECT snapshot_json FROM learning_evidence_receipt WHERE user_id=97201 AND source_type='QUIZ_ANSWER' AND source_id=97800",String.class));stored.putNull("score");jdbc.update("UPDATE learning_evidence_receipt SET snapshot_json=?,snapshot_sha=? WHERE user_id=97201 AND source_type='QUIZ_ANSWER' AND source_id=97800",json.writeValueAsString(stored),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(stored))));
  var comparison=evidence.compare(USER,"QUIZ_ANSWER",97800,"QUIZ_ANSWER",97801);assertEquals(false,comparison.get("comparable"));assertTrue(((List<?>)comparison.get("reasons")).contains("FROZEN_GRADE_UNAVAILABLE"));
 }
 @Test void unknownLegacyExamRuleVersionCannotCertifyComparableScores()throws Exception{
  jdbc.update("INSERT INTO exam_paper(id,name,source,status,duration_minutes,total_score) VALUES(97810,'未知评分规则','SMART',1,15,5)");
  jdbc.update("INSERT INTO exam_paper_question(id,paper_id,ordinal,score,question_type,scoring_rule,snapshot) VALUES(97810,97810,1,5,'TRUE_FALSE',NULL,'{\"answer\":\"true\"}')");
  for(int n=0;n<2;n++){long id=97810+n;jdbc.update("INSERT INTO exam_attempt(id,paper_id,user_id,mode,status,submit_time) VALUES(?,97810,97201,'EXAM','GRADED',CAST(? AS TIMESTAMP))",id,"2026-01-0"+(n+1)+" 00:00:00");jdbc.update("INSERT INTO exam_answer(id,attempt_id,paper_question_id,user_answer,score,max_score,graded,grade_type) VALUES(?,?,97810,'true',?,5,1,'RULE')",id,id,n*5);}
  var comparison=evidence.link(USER,new LearningEvidenceService.Retest("EXAM_ANSWER",97810L,"EXAM_ANSWER",97811L));assertEquals(false,comparison.get("comparable"));assertTrue(((List<?>)comparison.get("reasons")).contains("GRADING_RULE_VERSION_UNKNOWN"));
 }
}
