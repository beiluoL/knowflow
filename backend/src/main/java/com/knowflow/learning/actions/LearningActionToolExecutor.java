package com.knowflow.learning.actions;
import com.fasterxml.jackson.databind.*;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
/** Six explicit business bindings. Actor comes only from authenticated server orchestration. */
@Service @RequiredArgsConstructor
public class LearningActionToolExecutor {
 private final DailyLearningPlanService plans;private final LearningContextService context;private final ObjectMapper json;
 public Object execute(String name,JsonNode args,Long actor){
  if(actor==null)throw new BusinessException(401,"未登录");
  LearningActionTools.requireReadOnlyModelCall(name);
  if(args==null||!args.isObject())throw new BusinessException(400,"工具参数必须为对象");
  Set<String> allowed=name.equals("updateTaskStatus")?Set.of("taskId","source","status","zoneId","date"):Set.of("date","zoneId","availableMinutes","focusTopics","includePractice","includeInterview","useAi","instruction");
  args.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw new BusinessException(400,"工具参数不允许身份、SQL或未知字段");});
  if(name.equals("updateTaskStatus")){if(!args.path("taskId").isIntegralNumber()||!args.path("source").isTextual()||!args.path("status").isIntegralNumber())throw new BusinessException(400,"缺少任务状态预览参数");var r=new PlanModels.Request();if(args.has("zoneId"))r.setZoneId(args.path("zoneId").asText());if(args.has("date")){try{r.setDate(java.time.LocalDate.parse(args.path("date").asText()));}catch(java.time.DateTimeException e){throw new BusinessException(400,"无效日期");}}return plans.previewStatus(actor,args.path("taskId").asLong(),args.path("source").asText(),args.path("status").asInt(),r);}
  PlanModels.Request request;
  try{request=json.treeToValue(args,PlanModels.Request.class);}catch(Exception ex){throw new BusinessException(400,"工具参数不符合请求Schema");}
  request=plans.normalize(request);
  return switch(name){
   case "getLearningContext"->context.read(actor,request);
   case "listTodayTasks"->plans.todayTasks(actor,request).stream().map(t->new PlanModels.TaskRef(t.getId(),"TASK",t.getTitle(),t.getStatus())).toList();
   case "generateDailyPlan"->plans.preview(actor,request);
   case "previewTaskChanges"->plans.previewChanges(actor,request);
   default->throw new BusinessException(400,"未知学习工具");
  };
 }
}
