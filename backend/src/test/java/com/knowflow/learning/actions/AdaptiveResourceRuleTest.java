package com.knowflow.learning.actions;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class AdaptiveResourceRuleTest {
 @Test void sameQuestionIsNotDuplicatedByMistakeAndPracticeKeys(){
  var r=new PlanModels.Request();r.setAvailableMinutes(60);r.setIncludeInterview(false);
  var c=new PlanModels.Context(List.of(new PlanModels.Candidate("mistake:1","CAS错题","REVIEW","QUIZ",7L,null,1L,"真实错误","真实重答",5),new PlanModels.Candidate("question:7","CAS练习","PRACTICE","QUIZ",7L,null,1L,"真实题","真实作答",50)),List.of(),0,Map.of());
  assertEquals(1,new DailyPlanRule().generate(r,c).stream().filter(x->Objects.equals(x.resourceId(),7L)).count());
 }
}
