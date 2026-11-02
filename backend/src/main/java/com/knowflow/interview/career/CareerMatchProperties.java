package com.knowflow.interview.career;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.*;
/** 匹配权重是规则配置，未知维度不补分；随匹配结果冻结。 */
@Data @Component @ConfigurationProperties(prefix="knowflow.interview.career.match")
public class CareerMatchProperties {
 private Map<String,Integer> weights=new LinkedHashMap<>(Map.of("requiredSkills",45,"preferredSkills",10,"experience",10,"education",5,"projects",10,"technicalDepth",10,"responsibilities",5,"domain",5));
 public Map<String,Integer> checked(){
  if(weights==null||!weights.keySet().equals(Set.of("requiredSkills","preferredSkills","experience","education","projects","technicalDepth","responsibilities","domain"))||weights.values().stream().anyMatch(n->n==null||n<0)||weights.values().stream().mapToInt(Integer::intValue).sum()!=100)throw new IllegalStateException("匹配权重必须包含八个维度并合计100");
  return Collections.unmodifiableMap(new LinkedHashMap<>(weights));
 }
}
