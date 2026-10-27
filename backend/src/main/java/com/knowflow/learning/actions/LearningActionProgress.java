package com.knowflow.learning.actions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.LearningEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.*;
import java.sql.Timestamp;
import java.util.*;
/** Completion means the planned action was submitted; never a claim of correct understanding. */
@Component @RequiredArgsConstructor @Slf4j
public class LearningActionProgress {
 public record Committed(LearningEvent event){}
 private final JdbcTemplate jdbc;private final ObjectMapper json;private final LearningWriteGuard guard;
 @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void onEvidence(Committed signal){
  var e=signal.event();if(e==null||e.getUserId()==null||e.getResourceId()==null||e.getCreateTime()==null)return;
  if(!Set.of("QUESTION_ANSWERED","FLASHCARD_REVIEWED").contains(e.getEventType()))return;
  try{
   guard.lock(e.getUserId());
   var rows=jdbc.queryForList("SELECT t.id,t.learning_metadata,t.learning_plan_ref FROM task t JOIN learning_action_identity i ON i.task_id=t.id AND i.user_id=t.user_id WHERE t.user_id=? AND i.resource_key=? AND t.status=0 AND t.deleted=0 AND t.learning_plan_ref IS NOT NULL AND t.learning_metadata IS NOT NULL AND t.scheduled_date<=? AND t.create_time<=? ORDER BY t.scheduled_date,t.id LIMIT 100",e.getUserId(),guard.resourceHash(e.getResourceType(),e.getResourceId()),e.getCreateTime().toLocalDate(),Timestamp.valueOf(e.getCreateTime()));
   var meta=e.getMetadata()==null?json.createObjectNode():json.readTree(e.getMetadata());
   if(e.getEventType().equals("FLASHCARD_REVIEWED")&&!meta.path("algorithm").asText().contains("FSRS"))return;
   for(var row:rows){
    var item=json.readTree((String)row.get("learning_metadata"));
    boolean same=Objects.equals(item.path("resourceType").asText(),e.getResourceType())&&item.path("resourceId").asLong(-1)==e.getResourceId();
    if(!same)continue;
    long id=((Number)row.get("id")).longValue();
    var task=new com.knowflow.entity.Task();task.setId(id);task.setUserId(e.getUserId());task.setLearningMetadata((String)row.get("learning_metadata"));task.setLearningPlanRef((String)row.get("learning_plan_ref"));
    // 手动与自动入口消费同一协议；显式MANUAL任务不会因题型后来改变而被自动完成。
    if(guard.completionRequirement(task)==null)continue;
    if(jdbc.update("UPDATE task SET status=1,stage=2,update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND status=0 AND deleted=0",id,e.getUserId())==1)
     jdbc.update("INSERT INTO learning_task_evidence(task_id,event_id) VALUES(?,?) ON DUPLICATE KEY UPDATE event_id=VALUES(event_id)",id,e.getId());
   }
  }catch(Exception ex){
   // 原答题/复习已经提交；这里只回滚任务进度事务，留下可按eventId定位的未对账记录。
   log.error("LEARNING_TASK_UNRECONCILED eventId={} userId={} resourceType={} resourceId={} cause={}",e.getId(),e.getUserId(),e.getResourceType(),e.getResourceId(),ex.getClass().getSimpleName());
   throw new IllegalStateException("Cannot reconcile committed learning evidence eventId="+e.getId(),ex);
  }
 }
}
