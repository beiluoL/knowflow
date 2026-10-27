package com.knowflow.learning.actions;
import com.knowflow.ai.ModelAdapter.ToolSpec;
import com.knowflow.exception.BusinessException;
import java.util.*;
/** The existing provider-neutral schema contract; model calls never grant write authorization. */
public final class LearningActionTools {
 private LearningActionTools(){}
 public static List<ToolSpec> schemas(){return List.of(
  spec("getLearningContext","只读本人有权访问的学习摘要；缺失证据不能视为薄弱。",Map.of()),
  spec("listTodayTasks","只读本人本地日期的实际任务，不创建或更新。",Map.of("date",Map.of("type","string","format","date"),"zoneId",Map.of("type","string"))),
  spec("generateDailyPlan","基于当前用户上下文与预算生成规则草案；绝不创建任务。",Map.of("availableMinutes",Map.of("type","integer","minimum",1,"maximum",720),"date",Map.of("type","string","format","date"),"zoneId",Map.of("type","string"),"focusTopics",Map.of("type","array","items",Map.of("type","string"),"maxItems",8),"includePractice",Map.of("type","boolean"),"includeInterview",Map.of("type","boolean"))),
  spec("previewTaskChanges","仅生成调整草案；不删除、覆盖或完成原任务。",Map.of("availableMinutes",Map.of("type","integer","minimum",1,"maximum",720))),
  spec("createTasksFromPlan","写操作只能在浏览器明确确认后经专用HTTP事务执行；模型调用被拒绝。",Map.of("planId",Map.of("type","string"),"version",Map.of("type","integer"))),
  spec("updateTaskStatus","仅预览本人明确选中的任务状态；实际写入仍需用户确认。",Map.of("taskId",Map.of("type","integer"),"source",Map.of("type","string","enum",List.of("TASK","LEARNING")),"status",Map.of("type","integer","enum",List.of(0,1)))));
 }
 public static ToolSpec planningSchema(){var t=spec("rankDailyPlanCandidates","只排列服务器已提供候选，禁止新资源、ID与写操作。",Map.of("orderedKeys",Map.of("type","array","items",Map.of("type","string"),"maxItems",20)));var params=new HashMap<String,Object>(t.parameters);params.put("required",List.of("orderedKeys"));t.parameters=Map.copyOf(params);return t;}
 private static ToolSpec spec(String name,String description,Map<String,Object> properties){var s=new ToolSpec();s.name=name;s.description=description;s.parameters=Map.of("type","object","properties",properties,"additionalProperties",false);return s;}
 public static void requireReadOnlyModelCall(String name){if(!Set.of("getLearningContext","listTodayTasks","generateDailyPlan","previewTaskChanges","updateTaskStatus").contains(name))throw new BusinessException(403,"模型没有任务写入权限，必须由用户确认");}
}
