package com.knowflow.catalog;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.coding.entity.CodeProblem;
import com.knowflow.coding.mapper.CodeProblemMapper;
import com.knowflow.utils.JwtUtils;
import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.h2.url=jdbc:h2:mem:question-catalog-test;DB_CLOSE_DELAY=-1;MODE=MySQL","knowflow.datasource.allow-runtime-switch=false","knowflow.exam.timeout-scheduler.enabled=false","knowflow.coding.seed.algorithm.enabled=false"})
class QuestionCatalogIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired JwtUtils jwt;
 @Autowired SysUserMapper users; @Autowired QuizQuestionMapper quiz; @Autowired CodeQuestionMapper legacy; @Autowired LearningScenarioMapper scenes; @Autowired CodeProblemMapper code;
 String admin,user,key;
 @BeforeEach void init(){key="catalog-"+UUID.randomUUID();admin=actor("ADMIN");user=actor("USER");}
 String actor(String role){var u=new SysUser();u.setUsername(UUID.randomUUID().toString());u.setPassword("test-only");u.setRole(role);users.insert(u);return "Bearer "+jwt.generateToken(u.getId(),u.getUsername(),role);}
 QuizQuestion q(String type){var q=new QuizQuestion();q.setTitle(key);q.setContent("same-content");q.setQuestionType(type);q.setDifficulty(2);q.setStatus(0);q.setSource("MANUAL");quiz.insert(q);return q;}
 @Test void adminOnlyAndMalformedIdentity()throws Exception{mvc.perform(get("/api/admin/question-catalog")).andExpect(status().isUnauthorized());mvc.perform(get("/api/admin/question-catalog").header("Authorization",user)).andExpect(status().isForbidden());mvc.perform(get("/api/admin/question-catalog").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));mvc.perform(get("/api/admin/question-catalog/1").header("Authorization",admin)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));}
 @Test void fourSourcesLocalIdCollisionAndCodeNotDoubleCounted()throws Exception{var ordinary=q("SINGLE_CHOICE");var scene=new LearningScenario();scene.setId(ordinary.getId());scene.setTitle(key);scene.setContextText("context");scene.setQuestionText("question");scene.setKeyPoints("[]");scene.setDifficulty(2);scene.setStatus(0);scenes.insert(scene);var old=new CodeQuestion();old.setId(ordinary.getId());old.setTitle(key);old.setDescription("legacy executable");old.setDifficulty(1);old.setStatus(0);legacy.insert(old);var algorithm=q("CODE");var extension=new CodeProblem();extension.setQuestionId(algorithm.getId());code.insert(extension);
 var body=mvc.perform(get("/api/admin/question-catalog").param("keyword",key).header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(4)).andReturn().getResponse().getContentAsString();var rows=json.readTree(body).path("data").path("records");Set<String> ids=new HashSet<>();for(var row:rows)ids.add(row.path("catalogId").asText());assertEquals(4,ids.size());assertTrue(ids.contains("QUIZ:"+ordinary.getId()));assertTrue(ids.contains("SCENARIO:"+ordinary.getId()));assertTrue(ids.contains("LEGACY_CODE:"+ordinary.getId()));assertTrue(ids.contains("CODE_PROBLEM:"+algorithm.getId()));mvc.perform(get("/api/admin/question-catalog").param("keyword",key).param("sourceType","LEGACY_CODE").param("difficulty","2").header("Authorization",admin)).andExpect(jsonPath("$.data.total").value(1));}
 @Test void stablePagingLiteralSearchAndBounds()throws Exception{q("TRUE_FALSE");q("SHORT_ANSWER");String first=mvc.perform(get("/api/admin/question-catalog").param("keyword",key).param("pageSize","1").header("Authorization",admin)).andExpect(jsonPath("$.data.total").value(2)).andReturn().getResponse().getContentAsString();String second=mvc.perform(get("/api/admin/question-catalog").param("keyword",key).param("pageSize","1").param("page","2").header("Authorization",admin)).andReturn().getResponse().getContentAsString();assertNotEquals(json.readTree(first).at("/data/records/0/catalogId"),json.readTree(second).at("/data/records/0/catalogId"));mvc.perform(get("/api/admin/question-catalog").param("pageSize","101").header("Authorization",admin)).andExpect(status().isBadRequest());mvc.perform(get("/api/admin/question-catalog").param("keyword",key+"%").header("Authorization",admin)).andExpect(jsonPath("$.data.total").value(0));}
 @Test void aiCandidateCannotAutoPublishAndReviewCannotForgeIdentity()throws Exception{String payload=json.writeValueAsString(Map.of("title",key,"content","candidate","source","AI","status",1,"reviewStatus","APPROVED","creatorId",999,"id",999));var response=mvc.perform(post("/api/admin/quiz-questions").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(jsonPath("$.data.status").value(0)).andExpect(jsonPath("$.data.reviewStatus").value("PENDING_REVIEW")).andReturn().getResponse().getContentAsString();long id=json.readTree(response).at("/data/id").asLong();assertNotEquals(999,id);assertNotEquals(999,quiz.selectById(id).getCreatorId());mvc.perform(put("/api/admin/quiz-questions/"+id+"/publish").header("Authorization",admin)).andExpect(status().isConflict());mvc.perform(post("/api/admin/quiz-questions/"+id+"/review").header("Authorization",user).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}")).andExpect(status().isForbidden());var viewed=json.readTree(mvc.perform(get("/api/admin/question-catalog/QUIZ:"+id).header("Authorization",admin)).andReturn().getResponse().getContentAsString()).at("/data/item");mvc.perform(post("/api/admin/quiz-questions/"+id+"/review").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("decision","APPROVED","expectedSourceVersion",viewed.path("sourceVersion").asInt(),"expectedEditToken",viewed.path("editToken").asText())))).andExpect(status().isOk());assertEquals(0,quiz.selectById(id).getStatus());mvc.perform(put("/api/admin/quiz-questions/"+id+"/publish").header("Authorization",admin)).andExpect(status().isOk());assertEquals(1,quiz.selectById(id).getStatus());assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM question_catalog_audit WHERE source_type='QUIZ' AND source_question_id=?",Long.class,id)>=3);}
 @Test void batchFailureRollsBackEarlierPublication()throws Exception{var first=q("SINGLE_CHOICE");var pending=q("SINGLE_CHOICE");pending.setSource("AI");pending.setReviewStatus("PENDING_REVIEW");quiz.updateById(pending);mvc.perform(post("/api/admin/question-catalog/batch-status").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("catalogIds",List.of("QUIZ:"+first.getId(),"QUIZ:"+pending.getId()),"published",true)))).andExpect(status().isConflict());assertEquals(0,quiz.selectById(first.getId()).getStatus());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM question_catalog_audit WHERE source_type='QUIZ' AND source_question_id=?",Long.class,first.getId()));}
 @Test void genericEditorCannotMutateCodeAndMissingSourceIs404()throws Exception{var algorithm=q("CODE");mvc.perform(put("/api/admin/quiz-questions/"+algorithm.getId()+"/publish").header("Authorization",admin)).andExpect(status().isBadRequest());mvc.perform(get("/api/admin/question-catalog/SCENARIO:99999999").header("Authorization",admin)).andExpect(status().isNotFound());}
 @Test void commonEditOptimisticLockPreservesPaperSnapshotAndSourceIdentity()throws Exception{
  var q=q("SINGLE_CHOICE");q.setVersion(2);q.setCanonicalKey(key);quiz.updateById(q);
  String snapshot=json.writeValueAsString(Map.of("id",q.getId(),"title","original snapshot","answer","original answer"));
  jdbc.update("INSERT INTO exam_paper_question(paper_id,question_id,ordinal,score,question_type,snapshot) VALUES(98765,?,1,5,'SINGLE_CHOICE',?)",q.getId(),snapshot);
  String body=mvc.perform(get("/api/admin/question-catalog/QUIZ:"+q.getId()).header("Authorization",admin)).andReturn().getResponse().getContentAsString();String time=json.readTree(body).at("/data/item/updatedAt").asText();
  var request=Map.of("title",key+" edited","difficulty",3,"tags","tag","expectedSourceVersion",2,"expectedUpdatedAt",time);
  mvc.perform(put("/api/admin/question-catalog/QUIZ:"+q.getId()+"/basic").header("Authorization",user).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isForbidden());
  mvc.perform(put("/api/admin/question-catalog/QUIZ:"+q.getId()+"/basic").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isOk());
  assertEquals(3,quiz.selectById(q.getId()).getVersion());assertEquals(key,quiz.selectById(q.getId()).getCanonicalKey());assertEquals("same-content",quiz.selectById(q.getId()).getContent());
  assertEquals(snapshot,jdbc.queryForObject("SELECT snapshot FROM exam_paper_question WHERE question_id=? AND paper_id=98765",String.class,q.getId()));
  mvc.perform(put("/api/admin/question-catalog/QUIZ:"+q.getId()+"/basic").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isConflict());
 }
 @Test void canonicalDuplicateIsEvidenceOnlyAndJudgeDoesNotEnableExecution()throws Exception{
  var a=q("SHORT_ANSWER");var b=q("SHORT_ANSWER");a.setCanonicalKey(key);b.setCanonicalKey(key);quiz.updateById(a);quiz.updateById(b);
  mvc.perform(get("/api/admin/question-catalog/QUIZ:"+a.getId()).header("Authorization",admin)).andExpect(jsonPath("$.data.item.duplicateCount").value(2)).andExpect(jsonPath("$.data.item.canonicalEvidence").value(true));
  mvc.perform(get("/api/admin/question-catalog/judge-status").header("Authorization",admin)).andExpect(jsonPath("$.data.productionSandboxReady").value(false));
 }

 @Test void unversionedSourceRejectsStaleTokenEvenWhenTimestampUnchanged()throws Exception{
  var old=new CodeQuestion();old.setTitle(key);old.setDescription("legacy");old.setDifficulty(1);old.setStatus(0);legacy.insert(old);
  String body=mvc.perform(get("/api/admin/question-catalog/LEGACY_CODE:"+old.getId()).header("Authorization",admin)).andReturn().getResponse().getContentAsString();var item=json.readTree(body).at("/data/item");
  jdbc.update("UPDATE code_question SET title=? WHERE id=?",key+" other",old.getId());
  var request=Map.of("title",key+" stale","difficulty",2,"tags","QA","expectedUpdatedAt",item.path("updatedAt").asText(),"expectedEditToken",item.path("editToken").asText());
  mvc.perform(put("/api/admin/question-catalog/LEGACY_CODE:"+old.getId()+"/basic").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isConflict());assertEquals(key+" other",legacy.selectById(old.getId()).getTitle());
 }

 @Test void reviewedManualEditCannotPublishRejectedContent()throws Exception{
  var q=q("SINGLE_CHOICE");q.setReviewStatus("REJECTED");quiz.updateById(q);
  mvc.perform(put("/api/admin/quiz-questions/"+q.getId()).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("title",key,"content","edited","questionType","SINGLE_CHOICE","difficulty",2,"status",1)))).andExpect(status().isOk());
  assertEquals(0,quiz.selectById(q.getId()).getStatus());assertEquals("PENDING_REVIEW",quiz.selectById(q.getId()).getReviewStatus());
 }
 @Test void publicationWaitsForConcurrentEditAndChecksLatestReview()throws Exception{
  var q=q("SINGLE_CHOICE");q.setSource("AI");q.setReviewStatus("APPROVED");q.setVersion(1);quiz.updateById(q);
  var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
  try(var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()){
   connection.setAutoCommit(false);
   try(var lock=connection.prepareStatement("SELECT id FROM quiz_question WHERE id=? FOR UPDATE")){lock.setLong(1,q.getId());lock.executeQuery().close();}
   var future=executor.submit(()->mvc.perform(put("/api/admin/quiz-questions/"+q.getId()+"/publish").header("Authorization",admin)).andReturn().getResponse().getStatus());
   Thread.sleep(200);
   try(var edit=connection.prepareStatement("UPDATE quiz_question SET title=?,content='new content',review_status='PENDING_REVIEW',status=0,version=2 WHERE id=?")){edit.setString(1,key+" new");edit.setLong(2,q.getId());edit.executeUpdate();}
   connection.commit();assertEquals(409,future.get(5,java.util.concurrent.TimeUnit.SECONDS));assertEquals(key+" new",quiz.selectById(q.getId()).getTitle());assertEquals(0,quiz.selectById(q.getId()).getStatus());
  }finally{executor.shutdownNow();}
 }

}
