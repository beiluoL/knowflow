package com.knowflow.learning.actions;
import java.util.*;
import com.knowflow.exception.BusinessException;
/** A budgeted user-requested plan, not a second NextAction/Evidence engine. */
public class DailyPlanRule {
 private String title(String value){if(value.length()<=200)return value;int end=200;if(Character.isHighSurrogate(value.charAt(end-1)))end--;return value.substring(0,end);}
 public List<PlanModels.Item> generate(PlanModels.Request req,PlanModels.Context context){
  int budget=req.getAvailableMinutes()==null?120:req.getAvailableMinutes();
  if(budget<1||budget>720)throw new BusinessException(400,"学习时长必须为1至720分钟");
  List<String> focus=req.getFocusTopics()==null?List.of():req.getFocusTopics();
  String topic=focus.isEmpty()?"学习基础":String.join(" / ",focus);
  List<PlanModels.Candidate> all=new ArrayList<>(context.candidates());
  all.add(new PlanModels.Candidate("foundation","梳理"+topic,"THEORY","TOOL",null,null,null,"缺少充分证据时先建立基础学习记录，不视为薄弱或已掌握","写出概念、适用条件与一个例子",100));
  if(Boolean.TRUE.equals(req.getIncludePractice()))all.add(new PlanModels.Candidate("practice","练习并核对"+topic,"PRACTICE","TOOL",null,null,null,"通过真实作答检验理解，成绩以实际提交为准","完成练习并记录错误原因",110));
  if(Boolean.TRUE.equals(req.getIncludeInterview()))all.add(new PlanModels.Candidate("interview","模拟面试："+topic,"INTERVIEW","TOOL",null,null,null,"练习解释机制与工程边界，不伪造回答或评分","完成一轮真实回答并复盘",120));
  all.removeIf(c->(!Boolean.TRUE.equals(req.getIncludePractice())&&Set.of("PRACTICE","CODE").contains(c.type()))||(!Boolean.TRUE.equals(req.getIncludeInterview())&&c.type().equals("INTERVIEW")));
  if(!focus.isEmpty())all.removeIf(c->c.type().equals("THEORY")&&c.rank()>=20&&c.resourceId()!=null&&!focus.stream().anyMatch(f->c.title().toLowerCase(Locale.ROOT).contains(f.toLowerCase(Locale.ROOT))));
  all.sort(Comparator.comparingInt((PlanModels.Candidate c)->c.rank()<0?0:1).thenComparingInt(c->focus.stream().anyMatch(f->c.title().toLowerCase(Locale.ROOT).contains(f.toLowerCase(Locale.ROOT)))?0:1).thenComparingInt(PlanModels.Candidate::rank).thenComparing(PlanModels.Candidate::key));
  Set<String> resources=new HashSet<>();all.removeIf(c->c.resourceId()!=null&&!resources.add(c.resourceType()+":"+c.resourceId()));
  int max=Math.max(1,Math.min(6,budget/10));List<PlanModels.Candidate> picked=new ArrayList<>();
  for(var c:all){if(!Set.of("PRACTICE","CODE","INTERVIEW").contains(c.type())&&picked.size()<Math.max(1,max-(Boolean.TRUE.equals(req.getIncludePractice())?1:0)-(Boolean.TRUE.equals(req.getIncludeInterview())?1:0)))picked.add(c);}
  if(Boolean.TRUE.equals(req.getIncludePractice())&&picked.size()<max)all.stream().filter(c->Set.of("PRACTICE","CODE").contains(c.type())).findFirst().ifPresent(picked::add);
  if(Boolean.TRUE.equals(req.getIncludeInterview())&&picked.size()<max)all.stream().filter(c->c.type().equals("INTERVIEW")).findFirst().ifPresent(picked::add);
  int n=picked.size(),base=budget/n,remaining=budget%n;List<PlanModels.Item> result=new ArrayList<>();
  for(int i=0;i<n;i++){var c=picked.get(i);result.add(new PlanModels.Item(c.key(),title(c.title()),c.type(),base+(i<remaining?1:0),c.rank()<20?"HIGH":"MEDIUM",c.resourceType(),c.resourceId(),c.pathId(),c.knowledgeId(),c.reason(),c.completionCondition(),null,null,null));}
  return List.copyOf(result);
 }
}
