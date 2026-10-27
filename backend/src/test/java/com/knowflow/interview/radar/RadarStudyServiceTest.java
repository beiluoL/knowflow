package com.knowflow.interview.radar;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.seed.InterviewRadarImportService;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.interview.v2.InterviewV2Service;
import com.knowflow.interview.service.MockInterviewService;
import com.knowflow.ai.capability.AiCapabilityProperties;
import com.knowflow.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc @Import(FakeAiConfiguration.class)
@TestPropertySource(properties="knowflow.datasource.h2.url=jdbc:h2:mem:radar_study;DB_CLOSE_DELAY=-1;MODE=MySQL")
class RadarStudyServiceTest {
 @Autowired RadarStudyService study; @Autowired RadarInterviewBank bank;
 @Autowired InterviewRadarImportService importer; @Autowired SysUserMapper users;
 @Autowired ObjectMapper json; @Autowired InterviewV2Service v2; @Autowired MockInterviewService legacy;
 @Autowired org.springframework.test.web.servlet.MockMvc mvc; @Autowired com.knowflow.utils.JwtUtils jwt;
 @Autowired AiCapabilityProperties capabilities; @Autowired JdbcTemplate jdbc;
 @BeforeEach void reset(){FakeAiConfiguration.reset();capabilities.setEnabled(true);}
 private SysUser user(){var u=new SysUser();u.setUsername("radar-study-"+UUID.randomUUID());u.setPassword("fixture");u.setRole("USER");users.insert(u);return u;}
 private long question(long actor)throws Exception {
  String body="# Q001 HashMap 原理\n## 标准答案\n数组容量和扩容边界，以及工程验证。";
  String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
  importer.apply(actor,json.valueToTree(Map.of("schemaVersion",1,"files",List.of(Map.of("kind","markdown","sourceFile","test/Q001.md","sourceHash",hash,"rawMarkdown",body,"title","Q001 HashMap 原理")),"mergedQuestions",List.of(Map.of("identity","interview-content/test:Q001","sourceNamespace","interview-content/test","sourceQuestionId","Q001","sources",List.of(Map.of("sourceFile","test/Q001.md","sourceHash",hash)),"answerCandidates",List.of("数组容量和扩容边界，以及工程验证。"))))));
  return bank.catalog(actor).get(0).questionId();
 }
 private String evaluation(int score)throws Exception{return json.writeValueAsString(Map.of("decision","NEXT_QUESTION","reason","本题已评价","missingKnowledge",List.of(),"followupQuestion","","targetDimension","TECHNICAL_CORRECTNESS","evaluation",Map.of("dimensions",Map.of("TECHNICAL_CORRECTNESS",score,"KNOWLEDGE_COVERAGE",score,"ENGINEERING_PRACTICE",score,"EXPRESSION_STRUCTURE",score,"FOLLOWUP_RESPONSE",0),"coveredKnowledge",List.of("数组容量"),"errors",List.of(),"strengths",List.of("说明了验证过程"),"gaps",List.of(),"citations",List.of("验证数组容量"),"suggestion","继续验证扩容边界","confidence",0.8)));}
 private Map<String,Object> answer(long actor,Map<String,Object> session,int score)throws Exception {
  FakeAiConfiguration.script(evaluation(score));long id=((Number)session.get("sessionId")).longValue();
  v2.answer(actor,id,new InterviewV2Service.Answer(((Number)session.get("version")).longValue(),UUID.randomUUID().toString(),"我会验证数组容量和扩容边界。",null,null));
  for(int i=0;i<200;i++){var current=v2.get(actor,id);if("COMPLETED".equals(current.get("state")))return current;Thread.sleep(10);}
  fail("evaluation did not complete");return null;
 }
 private RadarStudyService.Start start(long q,String stage){return new RadarStudyService.Start(q,stage,"SPECIALTY",null,15,1,true,UUID.randomUUID().toString(),null,List.of());}
 @Test void closedBookDoesNotLeakAndFirstScoreIsImmutableAcrossRetellSessions()throws Exception {
  var actor=user();var other=user();long q=question(actor.getId());
  assertThrows(BusinessException.class,()->study.start(actor.getId(),start(q,"RETELL")));
  var first=study.start(actor.getId(),start(q,"CLOSED_BOOK"));long sid=((Number)first.get("sessionId")).longValue();
  assertFalse(json.writeValueAsString(first).contains("数组容量和扩容边界，以及工程验证"));
  assertTrue(legacy.getOwn(actor.getId(),sid).getQuestions().get(0).getReferencePoints().isEmpty());
  var completed=answer(actor.getId(),first,70);
  assertEquals(7.0,((Map<?,?>)completed.get("report")).get("radarOverallScore"));
  var retell=study.start(actor.getId(),start(q,"RETELL"));answer(actor.getId(),retell,95);
  var stats=study.questions(actor.getId()).get(0);
  assertEquals(7.0,stats.get("firstScore"));assertEquals(9.5,stats.get("currentScore"));assertEquals(9.5,stats.get("bestScore"));assertEquals(true,stats.get("passed"));
  jdbc.update("UPDATE mock_interview_session SET deleted=1 WHERE id=?",sid);
  assertEquals(7.0,study.questions(actor.getId()).get(0).get("firstScore"));
  assertThrows(BusinessException.class,()->study.start(other.getId(),start(q,"CLOSED_BOOK")));
  assertThrows(BusinessException.class,()->v2.get(other.getId(),sid));
  var review=study.collectReview(actor.getId(),q);assertEquals(review,study.collectReview(actor.getId(),q));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard WHERE user_id=?",Integer.class,actor.getId()));
 }
 @Test void noAnswerOrUnavailableAiCannotProduceScoresAndPlansUseExistingTasks()throws Exception {
  var actor=user();long q=question(actor.getId());var before=study.questions(actor.getId()).get(0);assertNull(before.get("firstScore"));
  assertThrows(BusinessException.class,()->study.collectReview(actor.getId(),q));
  capabilities.setEnabled(false);var session=study.start(actor.getId(),start(q,"CLOSED_BOOK"));answer(actor.getId(),session,100);
  var after=study.questions(actor.getId()).get(0);assertNull(after.get("firstScore"));assertNull(after.get("currentScore"));assertEquals(false,after.get("passed"));
  assertEquals(study.savePlan(actor.getId(),15),study.savePlan(actor.getId(),15));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM task WHERE user_id=?",Integer.class,actor.getId()));
 }
 @Test void httpOwnershipAndEmptyFinishFailClosed()throws Exception {
  var owner=user();long q=question(owner.getId());
  String endpoint="/api/interview/radar/questions";
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(endpoint)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
  String ownerAuth="Bearer "+jwt.generateToken(owner.getId(),owner.getUsername(),"USER");
  var catalog=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(endpoint).header("Authorization",ownerAuth)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString();
  assertFalse(catalog.contains("数组容量和扩容边界，以及工程验证"));
  for(String role:List.of("USER","ADMIN")){
   var other=user();other.setRole(role);users.updateById(other);
   String auth="Bearer "+jwt.generateToken(other.getId(),other.getUsername(),role);
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(endpoint).header("Authorization",auth)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data").isEmpty());
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/interview/radar/sessions").header("Authorization",auth).contentType("application/json").content(json.writeValueAsString(start(q,"CLOSED_BOOK")))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(404));
  }
  var session=study.start(owner.getId(),start(q,"CLOSED_BOOK"));long sid=((Number)session.get("sessionId")).longValue();
  var finished=v2.command(owner.getId(),sid,"finish",new InterviewV2Service.Command(((Number)session.get("version")).longValue(),UUID.randomUUID().toString()));
  assertFalse(json.writeValueAsString(finished).contains("数组容量和扩容边界，以及工程验证"));
  assertNull(study.questions(owner.getId()).get(0).get("firstScore"));
  assertNull(study.questions(owner.getId()).get(0).get("firstTurnId"));
 }

 @Test void incompleteDimensionsCannotCombineIntoAFullRadarReport()throws Exception {
  var owner=user();long q=question(owner.getId());var session=study.start(owner.getId(),start(q,"CLOSED_BOOK"));
  var state=jdbc.queryForMap("SELECT * FROM mock_interview_v2_state WHERE session_id=?",session.get("sessionId"));
  var a=new LinkedHashMap<String,Object>();var b=new LinkedHashMap<String,Object>();
  for(String key:RadarRubric.WEIGHTS.keySet()){a.put(key,100);b.put(key,100);}a.put("TECHNICAL_CORRECTNESS",null);b.put("KNOWLEDGE_COVERAGE",null);
  var turns=List.of(Map.of("evaluation",Map.of("rubricVersion",RadarRubric.VERSION,"weights",RadarRubric.WEIGHTS,"dimensions",a)),Map.of("evaluation",Map.of("rubricVersion",RadarRubric.VERSION,"weights",RadarRubric.WEIGHTS,"dimensions",b)));
  var target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(v2);
  Map<?,?> report=org.springframework.test.util.ReflectionTestUtils.invokeMethod(target,"report",state,turns);
  assertNull(report.get("radarOverallScore"));assertNull(report.get("overallScore"));
 }

}
