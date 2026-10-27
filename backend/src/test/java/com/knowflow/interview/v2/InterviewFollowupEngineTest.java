package com.knowflow.interview.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.capability.AiCapabilityProperties;
import com.knowflow.interview.core.InterviewQuestionBank;
import com.knowflow.service.AiService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InterviewFollowupEngineTest {
 private final ObjectMapper json=new ObjectMapper();
 private final AiService ai=mock(AiService.class);
 private final InterviewQuestionBank.SeedQuestion question=InterviewQuestionBank.TRACKS.get(0).questions().get(0);
 private final String answer="我会检查数组容量，再验证扩容和查询结果。";
 private InterviewFollowupEngine engine()throws Exception{return new InterviewFollowupEngine(ai,new AiCapabilityProperties(),json,new InterviewRubricProperties());}
 private Map<String,Object> output(){
  Map<String,Object> evaluation=new LinkedHashMap<>();evaluation.put("dimensions",new LinkedHashMap<>(Map.of("TECHNICAL_CORRECTNESS",80,"KNOWLEDGE_COVERAGE",70,"ENGINEERING_PRACTICE",65,"EXPRESSION_STRUCTURE",75,"FOLLOWUP_RESPONSE",80)));
  evaluation.put("coveredKnowledge",List.of("容量检查"));evaluation.put("errors",List.of());evaluation.put("strengths",List.of("说明了容量检查"));evaluation.put("gaps",List.of("HashMap"));evaluation.put("citations",List.of("检查数组容量"));evaluation.put("suggestion","结合容量边界给出一个验证过程");evaluation.put("confidence",.65);
  return new LinkedHashMap<>(Map.of("decision","FOLLOW_UP","reason","仍需要验证扩容边界","missingKnowledge",List.of("扩容"),"followupQuestion","如果容量不足，你会怎样验证扩容后的查询结果？","targetDimension","ENGINEERING_PRACTICE","evaluation",evaluation));
 }
 @Test void schemaAndEvidencePreserveRealModelAndRubricButNoFirstRoundFollowupScore()throws Exception{
  var proposal=engine().parse(json.writeValueAsString(output()),answer,question,0,2,List.of(question.title()),"actual-provider:model-v1");
  assertEquals(List.of("扩容"),proposal.evaluation().get("missingKnowledge"));assertEquals("AI",proposal.source());assertEquals("actual-provider:model-v1",proposal.evaluation().get("model"));
  assertEquals("interview-5d-v1",proposal.evaluation().get("rubricVersion"));assertNull(((Map<?,?>)proposal.evaluation().get("dimensions")).get("FOLLOWUP_RESPONSE"));
 }
 @Test void radarQuestionUsesIndependentTenPointRubricWithoutChangingOldQuestion()throws Exception{
  var radar=new InterviewQuestionBank.SeedQuestion("RADAR-41",question.title(),question.referencePoints(),question.followups(),question.rubric());
  var result=engine().parse(json.writeValueAsString(output()),answer,radar,0,0,List.of(),"test:provider");
  assertEquals(com.knowflow.interview.radar.RadarRubric.VERSION,result.evaluation().get("rubricVersion"));
  assertEquals(com.knowflow.interview.radar.RadarRubric.WEIGHTS,result.evaluation().get("weights"));
  var grade=(com.knowflow.interview.radar.RadarRubric.Grade)result.evaluation().get("radarGrade");
  assertEquals(7.35,grade.score());assertFalse(grade.passed());
 }
 @Test void radarProviderBudgetStopsUnboundedRetryConfiguration()throws Exception{
  var props=new AiCapabilityProperties();props.setEnabled(true);props.setMaxAttempts(4);when(ai.isConfigured(1L)).thenReturn(true);
  var radar=new InterviewQuestionBank.SeedQuestion("RADAR-41",question.title(),question.referencePoints(),question.followups(),question.rubric());
  var result=new InterviewFollowupEngine(ai,props,json,new InterviewRubricProperties()).evaluate(1L,radar,answer,0,0,List.of(),"Java","STRICT","",List.of());
  assertNull(result.evaluation());verify(ai,never()).completeStructured(anyString(),anyString(),any(),anyLong());
 }
 @Test void fabricatedCitationsAndUnknownFieldsFailClosed()throws Exception{
  Map<String,Object> data=output();data.put("unregistered","override state");assertThrows(IllegalArgumentException.class,()->engine().parse(json.writeValueAsString(data),answer,question,0,2,List.of(),"model"));
  data.remove("unregistered");@SuppressWarnings("unchecked") var eval=(Map<String,Object>)data.get("evaluation");eval.put("citations",List.of("我已实现百万QPS"));
  assertThrows(IllegalArgumentException.class,()->engine().parse(json.writeValueAsString(data),answer,question,0,2,List.of(),"model"));
 }
 @Test void invalidScopeMultiQuestionAndRepetitionAreRejected()throws Exception{
  for(String prompt:List.of("你今天午饭吃了什么？","你知道吗？为什么呢？",question.title(),"正确答案是数组。你记住了吗？")){
   var data=output();data.put("followupQuestion",prompt);assertThrows(IllegalArgumentException.class,()->engine().parse(json.writeValueAsString(data),answer,question,0,2,List.of(question.title()),"model"));
  }
  var data=output();data.put("missingKnowledge",List.of("未经注册的量子算法知识"));assertThrows(IllegalArgumentException.class,()->engine().parse(json.writeValueAsString(data),answer,question,0,2,List.of(),"model"));
 }
 @Test void maxDepthCannotBeChangedByModel()throws Exception{
  var proposal=engine().parse(json.writeValueAsString(output()),answer,question,2,2,List.of(question.title()),"model");assertEquals("NEXT_QUESTION",proposal.decision());assertEquals("",proposal.followupQuestion());
 }
 @Test void disabledAndBrokenModelFallbackNeverProduceFakeScores()throws Exception{
  var e=engine();var result=e.evaluate(1L,question,answer,0,2,List.of(question.title()),"Java + AI","STRICT","",List.of());assertNull(result.evaluation());verify(ai,never()).completeStructured(anyString(),anyString(),any(),anyLong());
  when(ai.isConfigured(1L)).thenReturn(true);when(ai.completeStructured(anyString(),anyString(),isNull(),eq(1L))).thenReturn("not JSON");
  result=e.evaluate(1L,question,answer,0,2,List.of(question.title()),"Java + AI","STRICT","",List.of());assertEquals("PRESET",result.source());assertNull(result.evaluation());
 }

 @Test void evaluationPromptUsesActualFollowupRatherThanMainQuestion()throws Exception{
  when(ai.isConfigured(1L)).thenReturn(true);when(ai.currentScope(1L)).thenReturn("test:model");when(ai.completeStructured(anyString(),anyString(),isNull(),eq(1L))).thenReturn("invalid");
  String followup="如果容量不足，你会怎样验证扩容后的查询结果？";
  engine().evaluate(1L,question,followup,answer,1,2,List.of(question.title()),"Java + AI","STRICT","",List.of());
  var prompt=org.mockito.ArgumentCaptor.forClass(String.class);verify(ai).completeStructured(anyString(),prompt.capture(),isNull(),eq(1L));assertEquals(followup,json.readTree(prompt.getValue()).path("question").asText());
 }

 @Test void rejectedRealModelParaphraseFixtureCannotBypassRegisteredScope()throws Exception{
  var kafka=com.knowflow.interview.core.InterviewQuestionBank.TRACKS.stream().filter(t->t.code().equals("KAFKA")).findFirst().orElseThrow().questions().get(0);
  try(var in=getClass().getResourceAsStream("/interview/rc1-rejected-model.json")){String raw=new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertThrows(IllegalArgumentException.class,()->engine().parse(raw,"使用 Redis 保存已经处理的消息 ID。",kafka,0,2,List.of(kafka.title()),"recorded-real-output"));}
 }
 @Test void typedProviderFailuresOfferSafeActionableFallback()throws Exception{
  when(ai.isConfigured(1L)).thenReturn(true);when(ai.currentScope(1L)).thenReturn("test:model");
  for(var kind:List.of(com.knowflow.ai.capability.AiFailureKind.INVALID_KEY,com.knowflow.ai.capability.AiFailureKind.RATE_LIMITED,com.knowflow.ai.capability.AiFailureKind.PROVIDER_ERROR,com.knowflow.ai.capability.AiFailureKind.TIMEOUT)){
   doThrow(new com.knowflow.ai.capability.AiCapabilityException(kind,"must-not-echo-sensitive-provider-detail")).when(ai).completeStructured(anyString(),anyString(),isNull(),eq(1L));
   var proposal=engine().evaluate(1L,question,answer,0,2,List.of(question.title()),"Java + AI","STRICT","",List.of());assertEquals("PRESET",proposal.source());assertNull(proposal.evaluation());assertTrue(proposal.message().contains(kind.userMessage()));assertFalse(proposal.message().contains("must-not-echo"));
  }
 }

 @Test void strictFollowupCannotIntroduceUnstatedKafkaSolutionFromRealProbe()throws Exception{
  var kafka=com.knowflow.interview.core.InterviewQuestionBank.TRACKS.stream().filter(t->t.code().equals("KAFKA")).findFirst().orElseThrow().questions().get(0);
  String confirmed="使用 Redis 保存已经处理的消息 ID。";var data=output();data.put("missingKnowledge",List.of("数据库唯一约束"));data.put("followupQuestion","你提到用 Redis 保存已处理消息 ID，那如何结合数据库唯一约束，并保证去重记录与业务写入在同一数据库事务中提交？");
  @SuppressWarnings("unchecked") var evaluation=(Map<String,Object>)data.get("evaluation");evaluation.put("citations",List.of(confirmed));
  assertThrows(IllegalArgumentException.class,()->engine().parse(json.writeValueAsString(data),confirmed,kafka,0,2,List.of(kafka.title()),"recorded-unsafe-real-question"));
 }
 @Test void strictFallbackDoesNotRevealUnstatedSolutionAfterSafePresetWasAsked()throws Exception{
  var kafka=InterviewQuestionBank.TRACKS.stream().filter(t->t.code().equals("KAFKA")).findFirst().orElseThrow().questions().get(0);
  var proposal=engine().evaluate(1L,kafka,"使用 Redis 保存已经处理的消息 ID。",1,2,List.of(kafka.title(),kafka.followups().get(0)),"Java + AI","STRICT","",List.of());
  assertEquals("NEXT_QUESTION",proposal.decision());assertNull(proposal.evaluation());
 }

}
