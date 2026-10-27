package com.knowflow.learning.actions;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DailyPlanRuleTest {
 @Test void emptyHistoryProducesHonestBudgetedFoundationAndRequestedPractice(){
  var req=new PlanModels.Request();req.setAvailableMinutes(180);req.setFocusTopics(List.of("Java并发","RAG"));
  var result=new DailyPlanRule().generate(req,new PlanModels.Context(List.of(),List.of("mastery"),0,Map.of()));
  assertEquals(180,result.stream().mapToInt(PlanModels.Item::estimatedMinutes).sum());assertTrue(result.stream().anyMatch(i->i.type().equals("INTERVIEW")));assertTrue(result.stream().allMatch(i->i.resourceId()==null));assertTrue(result.get(0).title().contains("Java并发"));
 }
 @Test void actualDueReviewPrecedesUnfinishedChapterWithoutInventingWeakness(){
  var req=new PlanModels.Request();req.setIncludePractice(false);req.setIncludeInterview(false);
  var c=List.of(new PlanModels.Candidate("chapter:2","章节","THEORY","CHAPTER",2L,1L,null,"未完成章节","阅读复述",30),new PlanModels.Candidate("knowledge:3","复习","REVIEW","KNOWLEDGE",3L,null,3L,"真实到期记录","回忆并核对",0));
  var result=new DailyPlanRule().generate(req,new PlanModels.Context(c,List.of(),0,Map.of()));assertEquals("knowledge:3",result.get(0).key());
 }
 @Test void shortBudgetNeverOverrunsAndResultIsReproducible(){
  var req=new PlanModels.Request();req.setAvailableMinutes(7);var c=new PlanModels.Context(List.of(),List.of(),0,Map.of());var rule=new DailyPlanRule();assertEquals(rule.generate(req,c),rule.generate(req,c));assertTrue(rule.generate(req,c).stream().mapToInt(PlanModels.Item::estimatedMinutes).sum()<=7);
 }
}
