package com.knowflow.interview.v2;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.*;

/** 权重与版本属于服务端，可按部署配置调整；前端不可决定成绩。 */
@Data @Component @ConfigurationProperties(prefix="knowflow.interview.rubric")
public class InterviewRubricProperties {
 private String version="interview-5d-v1";
 private int evaluationTimeoutSeconds=45;
 private Map<String,Integer> weights=new LinkedHashMap<>(Map.of(
  "TECHNICAL_CORRECTNESS",35,"KNOWLEDGE_COVERAGE",25,"ENGINEERING_PRACTICE",20,
  "EXPRESSION_STRUCTURE",10,"FOLLOWUP_RESPONSE",10));
 public Map<String,Integer> checkedWeights(){
  if(weights==null||weights.size()!=5||weights.values().stream().anyMatch(v->v==null||v<0)||weights.values().stream().mapToInt(i->i).sum()!=100)
   throw new IllegalStateException("面试Rubric权重必须为五个维度且总和100");
  return Collections.unmodifiableMap(new LinkedHashMap<>(weights));
 }
}
