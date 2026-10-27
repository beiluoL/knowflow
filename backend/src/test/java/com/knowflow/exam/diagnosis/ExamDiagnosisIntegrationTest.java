package com.knowflow.exam.diagnosis;
import com.fasterxml.jackson.databind.*;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"knowflow.datasource.h2.url=jdbc:h2:mem:phase3i-diagnosis;DB_CLOSE_DELAY=-1;MODE=MySQL","knowflow.datasource.config-file=/tmp/phase3i-diagnosis-test.json","knowflow.datasource.allow-runtime-switch=false","knowflow.ai.capability.enabled=false","knowflow.exam.timeout-scheduler.enabled=false","knowflow.coding.seed.algorithm.enabled=false"})
@ActiveProfiles("local") @AutoConfigureMockMvc
class ExamDiagnosisIntegrationTest {
 @Autowired DocDocumentMapper documents; @Autowired KgEntityMapper kg; @Autowired DocCategoryMapper categories; @Autowired ResourceKnowledgeMappingMapper mappings;
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JwtUtils jwt; @Autowired SysUserMapper users; @Autowired QuizQuestionMapper questions; @Autowired JdbcTemplate jdbc;
 Long aliceId,bobId; String alice,bob,topic;
 @BeforeEach void init(){topic="QA_DIAG_"+UUID.randomUUID();aliceId=user();bobId=user();alice=token(aliceId);bob=token(bobId);}
 Long user(){var u=new SysUser();u.setUsername(UUID.randomUUID().toString());u.setPassword("test-only");u.setRole("USER");users.insert(u);return u.getId();}
 String token(Long id){return "Bearer "+jwt.generateToken(id,"test","USER");}
 QuizQuestion question(String type){var q=new QuizQuestion();q.setTitle(topic+"题"+UUID.randomUUID());q.setContent("隔离题干");q.setTags(topic);q.setQuestionType(type);q.setDifficulty(2);q.setStatus(1);q.setSource("MANUAL");q.setCanonicalKey(UUID.randomUUID().toString());q.setVersion(1);q.setOptions("[\"A\",\"B\"]");q.setAnswer("0");q.setExplanation("隔离解析");questions.insert(q);return q;}
 JsonNode call(String actor,String path,String method,Object body)throws Exception{var request=method.equals("POST")?post(path):get(path);var result=mvc.perform(request.header("Authorization",actor).contentType(MediaType.APPLICATION_JSON).content(body==null?"":json.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andReturn();return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");}
 JsonNode compose(String actor,int count,String type,boolean include)throws Exception{return call(actor,"/api/exam/papers/compose","POST",Map.of("topics",List.of(topic),"typeDistribution",Map.of(type,count),"questionCount",count,"strategy","RANDOM","seed",42,"mode","PRACTICE","totalScore",10,"includeAnswered",include));}
 JsonNode start(String actor,long paper)throws Exception{return call(actor,"/api/exam/papers/"+paper+"/attempts","POST",Map.of());}
 JsonNode submit(String actor,JsonNode attempt,Map<Long,String> answers)throws Exception{return call(actor,"/api/exam/attempts/"+attempt.path("attemptId").asLong()+"/submit","POST",Map.of("answers",answers));}
 JsonNode diagnosis(String actor,long id)throws Exception{return call(actor,"/api/exam/attempts/"+id+"/diagnosis","GET",null);}
 @Test void unansweredAndWrongAreSeparatedAndReadDoesNotWriteMastery()throws Exception{
  question("SINGLE_CHOICE");question("SINGLE_CHOICE");question("SINGLE_CHOICE");var paper=compose(alice,3,"SINGLE_CHOICE",true);var attempt=start(alice,paper.path("id").asLong());long id=attempt.path("attemptId").asLong();long pq=attempt.path("questions").get(0).path("paperQuestionId").asLong();submit(alice,attempt,Map.of(pq,"1"));
  long before=jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_mastery WHERE user_id=?",Long.class,aliceId);
  var d=diagnosis(alice,id);assertEquals(1,d.path("objectiveAnsweredCount").asInt());assertEquals(2,d.path("unansweredCount").asInt());assertEquals(1,d.path("topics").get(0).path("wrongCount").asInt());assertEquals("INSUFFICIENT",d.path("topics").get(0).path("evidenceLevel").asText());assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_mastery WHERE user_id=?",Long.class,aliceId));
  mvc.perform(get("/api/exam/attempts/"+id+"/diagnosis").header("Authorization",bob)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  mvc.perform(get("/api/exam/attempts/"+id+"/diagnosis")).andExpect(status().isUnauthorized());
 }
 @Test void pendingAiNeverBecomesWrongOrZeroAccuracy()throws Exception{question("SHORT_ANSWER");var paper=compose(alice,1,"SHORT_ANSWER",true);var attempt=start(alice,paper.path("id").asLong());long pq=attempt.path("questions").get(0).path("paperQuestionId").asLong();submit(alice,attempt,Map.of(pq,"实际回答"));var d=diagnosis(alice,attempt.path("attemptId").asLong());assertEquals(1,d.path("ungradedCount").asInt());assertEquals(0,d.path("topics").get(0).path("wrongCount").asInt());assertTrue(d.path("objectiveAccuracy").isNull());}
 @Test void samePaperRetestComparesActualScores()throws Exception{question("SINGLE_CHOICE");var paper=compose(alice,1,"SINGLE_CHOICE",true);var one=start(alice,paper.path("id").asLong());submit(alice,one,Map.of(one.path("questions").get(0).path("paperQuestionId").asLong(),"1"));var two=start(alice,paper.path("id").asLong());submit(alice,two,Map.of(two.path("questions").get(0).path("paperQuestionId").asLong(),"0"));var d=diagnosis(alice,two.path("attemptId").asLong());assertTrue(d.path("comparison").path("comparable").asBoolean());assertEquals(0,d.path("comparison").path("previousObjectiveAccuracy").asInt());assertEquals(100,d.path("comparison").path("currentObjectiveAccuracy").asInt());}
 @Test void excludingAnsweredUsesOnlyCurrentOwnersExamRecords()throws Exception{question("SINGLE_CHOICE");var paper=compose(alice,1,"SINGLE_CHOICE",true);var one=start(alice,paper.path("id").asLong());submit(alice,one,Map.of(one.path("questions").get(0).path("paperQuestionId").asLong(),"0"));mvc.perform(post("/api/exam/papers/compose").header("Authorization",alice).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("topics",List.of(topic),"typeDistribution",Map.of("SINGLE_CHOICE",1),"includeAnswered",false)))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));assertNotNull(compose(bob,1,"SINGLE_CHOICE",false).get("id"));}
 @Test void knowledgeMaterialsHonorAcceptedMappingAndPrivateCategory()throws Exception {
  var q=question("SINGLE_CHOICE");
  var publicDoc=new DocDocument();publicDoc.setTitle(topic+"publicDoc");publicDoc.setStatus(1);documents.insert(publicDoc);
  var publicKnowledge=new KgEntity(); publicKnowledge.setDocId(publicDoc.getId()); publicKnowledge.setName(topic+"public"); publicKnowledge.setType("CONCEPT"); kg.insert(publicKnowledge);
  var privateCategory=new DocCategory(); privateCategory.setName("private-"+UUID.randomUUID()); privateCategory.setOwnerId(bobId); privateCategory.setStatus(1); categories.insert(privateCategory);
  var privateDoc=new DocDocument();privateDoc.setTitle(topic+"privateDoc");privateDoc.setCategoryId(privateCategory.getId());privateDoc.setStatus(1);documents.insert(privateDoc);
  var privateKnowledge=new KgEntity(); privateKnowledge.setDocId(privateDoc.getId()); privateKnowledge.setName(topic+"secret"); privateKnowledge.setType("CONCEPT"); privateKnowledge.setCategoryId(privateCategory.getId()); kg.insert(privateKnowledge);
  for(var entity:List.of(publicKnowledge,privateKnowledge)) {var mapping=new ResourceKnowledgeMapping();mapping.setResourceType("QUIZ");mapping.setResourceId(q.getId());mapping.setKnowledgeId(entity.getId());mapping.setStatus("ACCEPTED");mapping.setSource("IMPORT");mapping.setConfidence(java.math.BigDecimal.ONE);mappings.insert(mapping);}
  var paper=compose(alice,1,"SINGLE_CHOICE",true);var attempt=start(alice,paper.path("id").asLong());submit(alice,attempt,Map.of(attempt.path("questions").get(0).path("paperQuestionId").asLong(),"1"));
  long before=jdbc.queryForObject("SELECT COUNT(*) FROM resource_knowledge_mapping",Long.class);
  var d=diagnosis(alice,attempt.path("attemptId").asLong());var materials=d.path("topics").get(0).path("materials");assertEquals(1,materials.size());assertEquals(publicKnowledge.getId().longValue(),materials.get(0).path("knowledgeId").asLong());assertFalse(d.toString().contains(privateKnowledge.getName()));assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM resource_knowledge_mapping",Long.class));
 }
 @Test void unpublishedEarlierVersionStillExcludedFromUnansweredPool()throws Exception {
  var old=question("SINGLE_CHOICE");call(alice,"/api/quiz/submit","POST",Map.of("answers",List.of(Map.of("questionId",old.getId(),"userAnswer","0"))));
  old.setStatus(0);questions.updateById(old);questions.deleteById(old.getId());var latest=question("SINGLE_CHOICE");latest.setCanonicalKey(old.getCanonicalKey());latest.setVersion(2);questions.updateById(latest);
  mvc.perform(post("/api/exam/papers/compose").header("Authorization",alice).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("topics",List.of(topic),"typeDistribution",Map.of("SINGLE_CHOICE",1),"includeAnswered",false)))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
 }
 @Test void platformAndRunSubmissionsDoNotExcludeCodeQuestion() {
  var q=question("CODE");var lookup=new com.knowflow.exam.engine.QuestionHistoryLookup(jdbc);
  for(var values:List.of(new String[]{"SUBMIT","JUDGE_UNAVAILABLE"},new String[]{"RUN","ACCEPTED"})) jdbc.update("INSERT INTO code_submission(user_id,question_id,language,source_code,submission_type,judge_status) VALUES(?,?,'java','test',?,?)",aliceId,q.getId(),values[0],values[1]);
  assertFalse(lookup.answered(aliceId).contains(q.getId()));
  jdbc.update("INSERT INTO code_submission(user_id,question_id,language,source_code,submission_type,judge_status,score) VALUES(?,?,'java','test','SUBMIT','ACCEPTED',100)",aliceId,q.getId());
  assertTrue(lookup.answered(aliceId).contains(q.getId()));assertFalse(lookup.answered(bobId).contains(q.getId()));
 }

 @Test void overdueExamMistakesUseRealReviewTimestamp()throws Exception {
  var q=question("SINGLE_CHOICE");var paper=compose(alice,1,"SINGLE_CHOICE",true);var attempt=start(alice,paper.path("id").asLong());submit(alice,attempt,Map.of(attempt.path("questions").get(0).path("paperQuestionId").asLong(),"1"));
  jdbc.update("UPDATE learning_mistake SET last_review_time=? WHERE user_id=? AND source='EXAM' AND question_id=?",java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(8)),aliceId,q.getId());
  var d=diagnosis(alice,attempt.path("attemptId").asLong());assertTrue(d.path("topics").get(0).path("observations").toString().contains("至少7天没有复习"));
  jdbc.update("UPDATE learning_mistake SET last_review_time=? WHERE user_id=? AND source='EXAM' AND question_id=?",java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()),aliceId,q.getId());
  assertFalse(diagnosis(alice,attempt.path("attemptId").asLong()).path("topics").get(0).path("observations").toString().contains("至少7天没有复习"));
 }

 @Test void privateDocumentAssociationCannotBeMadePublicByNullQuestionCategory()throws Exception {
  var c=new DocCategory();c.setName("private-"+UUID.randomUUID());c.setOwnerId(bobId);c.setStatus(1);categories.insert(c);
  var doc=new DocDocument();doc.setTitle("private QA");doc.setCategoryId(c.getId());doc.setStatus(1);documents.insert(doc);
  var q=question("SINGLE_CHOICE");q.setDocId(doc.getId());q.setCategoryId(null);questions.updateById(q);
  mvc.perform(post("/api/exam/papers/compose").header("Authorization",alice).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("topics",List.of(topic),"typeDistribution",Map.of("SINGLE_CHOICE",1))))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
  assertNotNull(compose(bob,1,"SINGLE_CHOICE",true).get("id"));
 }
 @Test void earlierSubmissionWithLargerAttemptIdRemainsHistory()throws Exception {
  question("SINGLE_CHOICE");var paper=compose(alice,1,"SINGLE_CHOICE",true);var current=start(alice,paper.path("id").asLong());var secondPaper=compose(alice,1,"SINGLE_CHOICE",true);var prior=start(alice,secondPaper.path("id").asLong());assertNotEquals(current.path("attemptId").asLong(),prior.path("attemptId").asLong());
  submit(alice,prior,Map.of(prior.path("questions").get(0).path("paperQuestionId").asLong(),"1"));submit(alice,current,Map.of(current.path("questions").get(0).path("paperQuestionId").asLong(),"0"));
  jdbc.update("UPDATE exam_attempt SET submit_time=? WHERE id=?",java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(2)),prior.path("attemptId").asLong());
  var d=diagnosis(alice,current.path("attemptId").asLong());assertEquals(prior.path("attemptId").asLong(),d.path("comparison").path("previousAttemptId").asLong());assertTrue(d.path("comparison").path("comparable").asBoolean());
 }

 @Test void originalExamSnapshotIdentitySurvivesSourceIdentityEdit()throws Exception {
  var q=question("SINGLE_CHOICE");String original=q.getCanonicalKey();var paper=compose(alice,1,"SINGLE_CHOICE",true);var attempt=start(alice,paper.path("id").asLong());submit(alice,attempt,Map.of(attempt.path("questions").get(0).path("paperQuestionId").asLong(),"0"));
  q.setCanonicalKey(UUID.randomUUID().toString());questions.updateById(q);
  var keys=new com.knowflow.exam.engine.QuestionHistoryLookup(jdbc).answeredCanonicalKeys(aliceId);assertTrue(keys.contains(original));assertFalse(new com.knowflow.exam.engine.QuestionHistoryLookup(jdbc).answeredCanonicalKeys(bobId).contains(original));
 }

 @Test void unsubmittedAttemptCannotProduceDiagnosis()throws Exception {
  question("SINGLE_CHOICE");var paper=compose(alice,1,"SINGLE_CHOICE",true);var attempt=start(alice,paper.path("id").asLong());
  mvc.perform(get("/api/exam/attempts/"+attempt.path("attemptId").asLong()+"/diagnosis").header("Authorization",alice)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
 }

 @Test void aiDisabledFallbackPreservesAnsweredExclusion()throws Exception {
  var q=question("SINGLE_CHOICE");var paper=compose(alice,1,"SINGLE_CHOICE",true);var attempt=start(alice,paper.path("id").asLong());submit(alice,attempt,Map.of(attempt.path("questions").get(0).path("paperQuestionId").asLong(),"0"));
  mvc.perform(post("/api/exam/papers/ai").header("Authorization",alice).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("topic",topic,"count",1,"typeDistribution",Map.of("SINGLE_CHOICE",1),"includeAnswered",false,"fallbackToBank",1)))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
 }

}
