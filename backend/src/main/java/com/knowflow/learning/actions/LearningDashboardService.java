package com.knowflow.learning.actions;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
/** Read projection for a user-requested daily plan, never a second NextAction or mastery engine. */
@Service @RequiredArgsConstructor
public class LearningDashboardService {
 private final LearningContextService context;private final JdbcTemplate jdbc;private final ObjectMapper json;
 public Map<String,Object> read(long uid,int minutes,String zone){
  var r=new PlanModels.Request();r.setAvailableMinutes(minutes);r.setZoneId(zone);r.setDate(LocalDate.now(LearningContextService.zone(zone)));r.setIncludeInterview(false);
  var c=context.read(uid,r);var actions=new DailyPlanRule().generate(r,c).stream().limit(3).toList();
  var rows=jdbc.queryForList("SELECT t.id,t.title,t.status,t.learning_metadata,p.event_id FROM task t LEFT JOIN learning_task_evidence p ON p.task_id=t.id WHERE t.user_id=? AND t.deleted=0 AND t.learning_plan_ref IS NOT NULL AND t.scheduled_date<=? ORDER BY t.scheduled_date DESC,t.id DESC LIMIT 30",uid,r.getDate());
  List<Map<String,Object>> tasks=new ArrayList<>();for(var row:rows){var task=new LinkedHashMap<String,Object>();task.put("id",row.get("id"));task.put("title",row.get("title"));task.put("status",row.get("status"));task.put("eventId",row.get("event_id"));try{task.put("item",row.get("learning_metadata")==null?null:json.readTree((String)row.get("learning_metadata")));}catch(Exception e){throw new IllegalStateException("Invalid plan metadata",e);}tasks.add(task);}
  return Map.of("date",r.getDate(),"zoneId",zone,"facts",c.facts(),"missingData",c.missingData(),"actions",actions,"tasks",tasks,"bounded",rows.size()==30,"weakSignals",c.candidates().stream().filter(x->x.rank()>=0&&x.rank()<=10).limit(5).toList());
 }
}
