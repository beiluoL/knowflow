package com.knowflow.learning.actions;
import lombok.Data;
import java.time.*;
import java.util.*;
/** Only authenticated server code supplies owners and resource identities. */
public final class PlanModels {
 private PlanModels(){}
 @Data public static class Request {
  private LocalDate date; private String zoneId="Asia/Shanghai"; private Integer availableMinutes=120;
  private List<String> focusTopics=new ArrayList<>(); private Boolean includePractice=true,includeInterview=true,useAi=false;
  private String instruction;
  private Boolean smartReorder=false;private Long pathId;private Map<Long,Integer> taskMinutes=new LinkedHashMap<>();private List<Long> protectedTaskIds=new ArrayList<>();
 }
 public record Candidate(String key,String title,String type,String resourceType,Long resourceId,Long pathId,Long knowledgeId,String reason,String completionCondition,int rank){}
 public record Context(List<Candidate> candidates,List<String> missingData,int existingTasks,Map<String,Object> facts){}
 // Task metadata可追加服务端完成协议快照；旧计划Item读取时只消费自身定义字段。
 @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
 public record Item(String key,String title,String type,int estimatedMinutes,String priority,String resourceType,Long resourceId,Long pathId,Long knowledgeId,String reason,String completionCondition,Long existingTaskId,String existingSource,Integer previousStatus){}
 public record EditedItem(String key,String title,int estimatedMinutes,String priority){}
 @Data public static class ConfirmRequest {
  private Integer version; private String confirmationToken,idempotencyKey; private List<EditedItem> tasks;
 }
 public record TaskRef(Long id,String source,String title,Integer status){}
 /** 服务端确认的完成要求；只读投影给UI，不接受客户端据此变更任务身份。 */
 public record CompletionRequirement(String resourceType,Long resourceId,Long pathId,Long knowledgeId,boolean available){}
 public record TaskChange(Long taskId,String kind,LocalDate oldDate,LocalDate newDate,int oldMinutes,int newMinutes,String reason){}
 public record Draft(String planId,int version,Instant expiresAt,String state,String source,String action,LocalDate date,String zoneId,int availableMinutes,List<Item> tasks,List<String> warnings,String confirmationToken,List<TaskRef> createdTasks,Integer targetStatus,Request preferences,List<TaskChange> changes){}
 public record ChatRequest(Long conversationId,String message,Request preferences,Long selectedTaskId,String selectedTaskSource){}
 public record ChatResponse(boolean handled,String text,String planId,List<TaskRef> tasks,List<TaskRef> choices,Long conversationId){}
}
