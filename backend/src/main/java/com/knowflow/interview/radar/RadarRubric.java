package com.knowflow.interview.radar;
import java.math.*;
import java.util.*;

/** Versioned AI training scale; never writes objective Evidence or system mastery. */
public final class RadarRubric {
 private RadarRubric(){}
 public static final String VERSION="radar-four-dimension-10-v1";
 public static final Map<String,Integer> WEIGHTS;
 static {var weights=new LinkedHashMap<String,Integer>();weights.put("TECHNICAL_CORRECTNESS",40);weights.put("KNOWLEDGE_COVERAGE",30);weights.put("ENGINEERING_PRACTICE",20);weights.put("EXPRESSION_STRUCTURE",10);weights.put("FOLLOWUP_RESPONSE",0);WEIGHTS=Collections.unmodifiableMap(weights);}
 public record Grade(Double score,String level,boolean passed,Map<String,Double> points){}
 public static Grade grade(Map<String,?> dimensions){
  var points=new LinkedHashMap<String,Double>();BigDecimal total=BigDecimal.ZERO;boolean complete=true;
  for(var entry:WEIGHTS.entrySet()){
   if(entry.getValue()==0)continue;
   Object raw=dimensions==null?null:dimensions.get(entry.getKey());
   if(raw==null){points.put(entry.getKey(),null);complete=false;continue;}
   if(!(raw instanceof Number number)||!Double.isFinite(number.doubleValue())||number.doubleValue()<0||number.doubleValue()>100||number.doubleValue()!=number.intValue())throw new IllegalArgumentException("Invalid radar dimension: "+entry.getKey());
   BigDecimal value=BigDecimal.valueOf(number.intValue()).multiply(BigDecimal.valueOf(entry.getValue())).divide(BigDecimal.valueOf(1000));
   points.put(entry.getKey(),value.doubleValue());total=total.add(value);
  }
  if(!complete)return new Grade(null,null,false,Collections.unmodifiableMap(points));
  double score=total.setScale(2,RoundingMode.HALF_UP).doubleValue();
  String level=score<2?"L0":score<4?"L1":score<6?"L2":score<8.5?"L3":score<9.5?"L4":"L5";
  return new Grade(score,level,score>=8.5,Collections.unmodifiableMap(points));
 }
}
