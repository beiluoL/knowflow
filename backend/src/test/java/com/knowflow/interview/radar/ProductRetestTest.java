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
@TestPropertySource(properties={"knowflow.datasource.h2.url=jdbc:h2:mem:product_retest;DB_CLOSE_DELAY=-1;MODE=MySQL","knowflow.product.interview-learning.enabled=true","knowflow.product.private-quiz.enabled=true","knowflow.product.tutor.enabled=true","knowflow.product.tutor-coaching.enabled=true"})
class ProductRetestTest {
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

 @Autowired com.knowflow.learning.feedback.InterviewRetestService retests;
 @Autowired com.knowflow.learning.feedback.FeedbackSourceService feedbackSources;
 @Autowired com.knowflow.learning.privatequiz.PrivateQuizService quizzes; @Autowired com.knowflow.learning.tutor.TutorMicroService micros;
 @Test void requiresRealPracticeAndPreservesOriginalSnapshotAndUnknownHistory()throws Exception {
  var actor=user();long uid=actor.getId(),q=question(uid);
  jdbc.update("UPDATE quiz_question SET review_status='APPROVED' WHERE id=?",q);
  var original=answer(uid,study.start(uid,start(q,"CLOSED_BOOK")),60);
  long turn=jdbc.queryForObject("SELECT id FROM mock_interview_v2_turn WHERE session_id=?",Long.class,original.get("sessionId"));
  var plan=retests.create(uid,turn);long id=((Number)plan.get("id")).longValue();
  assertEquals(plan.get("id"),retests.create(uid,turn).get("id"));
  assertEquals(0,plan.get("completedCount"));assertThrows(BusinessException.class,()->retests.start(uid,id,"retest-request-01"));
  assertThrows(BusinessException.class,()->retests.detail(user().getId(),id));
  // A manual task checkbox is deliberately insufficient; practice is an actual saved F07 submission.
  long note=((Number)plan.get("noteId")).longValue();
  assertThrows(BusinessException.class,()->retests.linkPractice(uid,id,123456));
  assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM learning_note WHERE id=? AND user_id=?",Integer.class,note,uid)>0);
  var qs=List.of(new com.knowflow.learning.privatequiz.PrivateQuizRequests.Question("TRUE_FALSE","数组容量需要验证吗？",List.of(),"true","数组容量和扩容边界，以及工程验证。"),new com.knowflow.learning.privatequiz.PrivateQuizRequests.Question("TRUE_FALSE","扩容有边界吗？",List.of(),"true","数组容量和扩容边界，以及工程验证。"),new com.knowflow.learning.privatequiz.PrivateQuizRequests.Question("TRUE_FALSE","需要工程验证吗？",List.of(),"true","数组容量和扩容边界，以及工程验证。"));
  FakeAiConfiguration.script(json.writeValueAsString(qs));long quiz=((Number)quizzes.preview(uid,new com.knowflow.learning.privatequiz.PrivateQuizRequests.Preview(note,1L,UUID.randomUUID().toString())).get("id")).longValue();quizzes.confirm(uid,quiz,new com.knowflow.learning.privatequiz.PrivateQuizRequests.Confirm(qs));
  FakeAiConfiguration.script("{\"analogy\":\"容器扩容\",\"concept\":\"验证数组容量\",\"sourceQuote\":\"数组容量和扩容边界，以及工程验证。\"}");long mid=((Number)micros.start(uid,new com.knowflow.learning.tutor.TutorMicroService.Start(note,quiz,0,UUID.randomUUID().toString(),null)).get("id")).longValue();
  assertThrows(BusinessException.class,()->retests.linkPractice(uid,id,mid));micros.begin(mid,uid);micros.submit(mid,uid,"true");assertEquals(1,retests.linkPractice(uid,id,mid).get("completedCount"));assertEquals(1,retests.linkPractice(uid,id,mid).get("completedCount"));
  String frozen=jdbc.queryForObject("SELECT questions_json FROM mock_interview_session WHERE id=?",String.class,original.get("sessionId"));
  jdbc.update("UPDATE quiz_question SET answer='Changed bank reference' WHERE id=?",q);
  var second=answer(uid,study.start(uid,start(q,"CLOSED_BOOK")),65);long alternate=jdbc.queryForObject("SELECT id FROM mock_interview_v2_turn WHERE session_id=?",Long.class,second.get("sessionId"));
  long alternatePlan=((Number)retests.create(uid,alternate).get("id")).longValue();
  var started=retests.start(uid,id,"retest-request-02");assertFalse(retests.detail(uid,alternatePlan).containsKey("baseline"));assertTrue(retests.detail(uid,alternatePlan).containsKey("referenceUnavailable"));assertThrows(BusinessException.class,()->feedbackSources.read(uid,"INTERVIEW_TURN",alternate,0));assertFalse(json.writeValueAsString(started).contains("数组容量和扩容边界，以及工程验证。"));
  assertThrows(BusinessException.class,()->feedbackSources.read(uid,"INTERVIEW_TURN",turn,0));assertFalse(retests.list(uid,turn).get(0).containsKey("baseline"));
  var resession=(Map<String,Object>)started.get("session");assertEquals(frozen,jdbc.queryForObject("SELECT questions_json FROM mock_interview_session WHERE id=?",String.class,resession.get("sessionId")));
  answer(uid,resession,90);var done=retests.finish(uid,id);assertEquals("NOT_COMPARABLE",((Map<?,?>)done.get("comparison")).get("comparability"));assertEquals(done.get("retestTurnId"),retests.finish(uid,id).get("retestTurnId"));
  assertEquals(1,((List<?>)done.get("retestHistory")).size());String repeatKey=UUID.randomUUID().toString();var again=retests.repeat(uid,id,repeatKey);assertFalse(again.containsKey("baseline"));assertEquals(again.get("retestSessionId"),retests.repeat(uid,id,repeatKey).get("retestSessionId"));answer(uid,(Map<String,Object>)again.get("session"),85);var repeated=retests.finish(uid,id);assertEquals(2,((List<?>)repeated.get("retestHistory")).size());assertEquals(done.get("retestTurnId"),((Map<?,?>)((List<?>)repeated.get("retestHistory")).get(0)).get("turnId"));assertNotEquals(done.get("retestTurnId"),repeated.get("retestTurnId"));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_evidence_retest WHERE user_id=? AND retest_id=?",Integer.class,uid,done.get("retestTurnId")));
  assertEquals("我会验证数组容量和扩容边界。",jdbc.queryForObject("SELECT answer FROM mock_interview_v2_turn WHERE id=?",String.class,turn));
  var receipt=(Map<?,?>)plan.get("baseline");assertEquals("UNKNOWN",((Map<?,?>)receipt.get("historyProjection")).get("historyCompleteness"));
  assertEquals("我会验证数组容量和扩容边界。",receipt.get("answer"));
  jdbc.update("UPDATE doc_document SET deleted=1 WHERE id=(SELECT doc_id FROM quiz_question WHERE id=?)",q);assertThrows(BusinessException.class,()->v2.get(uid,((Number)repeated.get("retestSessionId")).longValue()));
 }

 @Autowired com.knowflow.learning.feedback.ProductMockInterviewService mocks;
 private List<Long> three(long uid)throws Exception {
  var files=new ArrayList<Map<String,Object>>();var merged=new ArrayList<Map<String,Object>>();
  for(int i=1;i<=3;i++){String number="Q10"+i,body="# "+number+" RAG检索合成测试场景"+i+"\n## 标准答案\n检索需要可核对证据，证据不足时明确未知，不虚构事实。",hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8))),file="test/"+number+".md";
   files.add(Map.of("kind","markdown","sourceFile",file,"sourceHash",hash,"rawMarkdown",body,"title",number+" RAG检索合成测试场景"+i));merged.add(Map.of("identity","interview-content/test:"+number,"sourceNamespace","interview-content/test","sourceQuestionId",number,"sources",List.of(Map.of("sourceFile",file,"sourceHash",hash)),"answerCandidates",List.of("检索需要可核对证据，证据不足时明确未知，不虚构事实。")));}
  importer.apply(uid,json.valueToTree(Map.of("schemaVersion",1,"files",files,"mergedQuestions",merged)));return bank.catalog(uid).stream().map(RadarInterviewBank.Question::questionId).toList();
 }
 private Map<String,Object> respond(long uid,Map<String,Object> session,String text,boolean follow)throws Exception {
  var data=json.readTree(evaluation(60));((com.fasterxml.jackson.databind.node.ObjectNode)data).put("decision",follow?"FOLLOW_UP":"NEXT_QUESTION").put("followupQuestion",follow?"你刚才提到的检索有哪些失败条件？":"");((com.fasterxml.jackson.databind.node.ObjectNode)data).set("missingKnowledge",json.valueToTree(List.of("检索")));((com.fasterxml.jackson.databind.node.ObjectNode)data.path("evaluation")).set("citations",json.valueToTree(List.of(text)));FakeAiConfiguration.script(data.toString());long sid=((Number)session.get("sessionId")).longValue();v2.answer(uid,sid,new InterviewV2Service.Answer(((Number)session.get("version")).longValue(),UUID.randomUUID().toString(),text,null,null));for(int i=0;i<200;i++){var next=v2.get(uid,sid);if(!"EVALUATING".equals(next.get("state")))return next;Thread.sleep(10);}throw new AssertionError("evaluation timeout");
 }
 @Test void threeMainQuestionsAndRelevantFollowupsUseOriginalRubricAndFailClosedOnSourceLoss()throws Exception {
  var actor=user();long uid=actor.getId();var ids=three(uid);var input=new com.knowflow.learning.feedback.ProductMockInterviewService.Start(UUID.randomUUID().toString(),"JAVA_AI","RAG",List.of());assertThrows(BusinessException.class,()->mocks.start(uid,input));for(long id:ids)jdbc.update("UPDATE quiz_question SET review_status='APPROVED' WHERE id=?",id);
  var session=mocks.start(uid,input);assertEquals(3,session.get("questionCount"));assertFalse(json.writeValueAsString(session).contains("检索需要可核对证据"));assertEquals(session.get("sessionId"),mocks.start(uid,input).get("sessionId"));assertTrue(session.get("role").toString().contains("通用岗位模板"));
  for(int i=0;i<3;i++){session=respond(uid,session,"我先做检索。",true);assertEquals("FOLLOW_UP",session.get("state"));assertFalse(json.writeValueAsString(session).contains("radarGrade"));session=respond(uid,session,"检索没有证据时明确说明不知道。",false);}
  assertEquals("COMPLETED",session.get("state"));assertEquals(6,((List<?>)session.get("turns")).size());assertEquals(RadarRubric.VERSION,((Map<?,?>)session.get("report")).get("rubricVersion"));assertNotNull(((Map<?,?>)session.get("report")).get("radarOverallScore"));long sid=((Number)session.get("sessionId")).longValue();assertThrows(BusinessException.class,()->v2.get(user().getId(),sid));
  jdbc.update("UPDATE doc_document SET deleted=1 WHERE id=(SELECT doc_id FROM quiz_question WHERE id=?)",ids.get(0));assertThrows(BusinessException.class,()->v2.get(uid,sid));
 }
}
