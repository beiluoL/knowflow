package com.knowflow.learning.feedback;

import com.knowflow.dto.*;
import com.knowflow.exception.BusinessException;
import com.knowflow.interview.radar.*;
import com.knowflow.interview.v2.InterviewV2Service;
import com.knowflow.learning.evidence.LearningEvidenceService;
import com.knowflow.learning.actions.LearningWriteGuard;
import com.knowflow.learning.tutor.TutorMicroService;
import com.knowflow.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Links existing notes, tasks, actual F07 submissions and immutable T02 receipts. */
@Service @RequiredArgsConstructor
@ConditionalOnProperty(name={"knowflow.product.interview-learning.enabled","knowflow.product.private-quiz.enabled","knowflow.product.tutor.enabled","knowflow.product.tutor-coaching.enabled"},havingValue="true")
public class InterviewRetestService {
 private final JdbcTemplate sql;private final FeedbackSourceService sources;private final LearningEvidenceService evidence;
 private final RadarInterviewBank bank;private final InterviewV2Service interviews;private final NoteService notes;
 private final org.springframework.core.env.Environment environment;
 private final TaskService tasks;private final TutorMicroService micros;private final LearningWriteGuard guard;
 private boolean synthetic(RadarInterviewBank.Material m){return environment.acceptsProfiles(org.springframework.core.env.Profiles.of("product03"))&&environment.getProperty("knowflow.product.interview-learning.allow-synthetic-fixtures",Boolean.class,false)&&"interview-content/SYNTHETIC_PRODUCT03".equals(m.metadata().sourceNamespace())&&m.metadata().title().contains("合成测试");}
 private long n(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
 private Map<String,Object> own(long user,long id){var rows=sql.queryForList("SELECT * FROM learning_interview_retest WHERE id=? AND owner_id=?",id,user);if(rows.isEmpty())throw new BusinessException(404,"原题补练计划不存在");var r=rows.get(0);sources.readForRetest(user,"INTERVIEW_TURN",n(r,"baseline_turn"),0);notes.detail(n(r,"note_id"),user,false);return r;}
 @Transactional public Map<String,Object> create(long user,long turn){
  guard.lock(user);var source=sources.read(user,"INTERVIEW_TURN",turn,0);
  var original=sql.queryForMap("SELECT t.question_id,t.depth,s.mode FROM mock_interview_v2_turn t JOIN mock_interview_v2_state s ON t.session_id=s.session_id WHERE t.id=? AND t.user_id=?",turn,user);
  String key=original.get("question_id").toString();if(!key.matches("RADAR-[0-9]+")||n(original,"depth")!=0||!"STRICT".equals(original.get("mode")))throw new BusinessException(409,"请选择已完成的闭卷原题主回答");
  var material=bank.material(user,Long.parseLong(key.substring(6)));if(!material.metadata().eligible()||(!"APPROVED".equals(material.metadata().reviewStatus())&&!synthetic(material)))throw new BusinessException(409,"正式补练仅支持已审核可用题源；待审资料保持受限");
  var old=sql.queryForList("SELECT id FROM learning_interview_retest WHERE owner_id=? AND baseline_turn=?",user,turn);if(!old.isEmpty())return detail(user,n(old.get(0),"id"));
  evidence.captureRequired(user,"INTERVIEW_TURN",turn);
  var dto=new LearningNoteDTO();dto.setTitle("原题补练 · "+source.question().substring(0,Math.min(100,source.question().length())));dto.setContent("# 原题冻结参考\n"+source.question()+"\n\n"+source.basis()+"\n\nAI建议评价，不代表客观掌握度。");dto.setSourceDocId(material.document().getId());dto.setStatus("SAVED");long note=notes.create(user,dto).getId();
  var task=new TaskDTO();task.setTitle("原题弱点补练");task.setNotes("原作答 #"+turn+"；笔记 #"+note+"。请在现有F07实际交卷，勾选任务不计作补练凭证。");long tid=tasks.createTask(user,task);
  sql.update("INSERT INTO learning_interview_retest(owner_id,baseline_turn,note_id,task_id,created_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP)",user,turn,note,tid);
  return detail(user,sql.queryForObject("SELECT id FROM learning_interview_retest WHERE owner_id=? AND baseline_turn=?",Long.class,user,turn));
 }
 public List<Map<String,Object>> list(long user,long turn){sources.readForRetest(user,"INTERVIEW_TURN",turn,0);return sql.queryForList("SELECT id FROM learning_interview_retest WHERE owner_id=? AND baseline_turn=?",Long.class,user,turn).stream().map(id->detail(user,id)).toList();}
 public Map<String,Object> detail(long user,long id){var r=own(user,id);var out=new LinkedHashMap<String,Object>();out.put("id",id);out.put("baselineTurn",r.get("baseline_turn"));out.put("noteId",r.get("note_id"));out.put("taskId",r.get("task_id"));out.put("createdAt",r.get("created_at"));if(r.get("retest_session")!=null){var current=interviews.get(user,n(r,"retest_session"));if(!"COMPLETED".equals(current.get("state"))){out.put("session",current);out.put("retestSessionId",r.get("retest_session"));out.put("completedCount",sql.queryForObject("SELECT COUNT(*) FROM learning_interview_practice WHERE plan_id=? AND owner_id=?",Integer.class,id,user));return out;}}try{sources.read(user,"INTERVIEW_TURN",n(r,"baseline_turn"),0);}catch(BusinessException e){if(e.getCode()!=409)throw e;out.put("referenceUnavailable",e.getMessage());out.put("completedCount",0);return out;}var note=notes.detail(n(r,"note_id"),user,false);out.put("noteContent",note.getContent());out.put("noteRevision",note.getRevision());out.put("baseline",evidence.get(user,"INTERVIEW_TURN",n(r,"baseline_turn")));
  var practice=new ArrayList<Map<String,Object>>();for(long mid:sql.queryForList("SELECT micro_id FROM learning_interview_practice WHERE plan_id=? AND owner_id=?",Long.class,id,user)){var m=micros.detail(mid,user);if("COMPLETED".equals(m.get("state")))practice.add(m);}out.put("practice",practice);out.put("completedCount",practice.size());out.put("retestSessionId",r.get("retest_session"));out.put("retestTurnId",r.get("retest_turn"));
  if(r.get("retest_session")!=null)out.put("session",interviews.get(user,n(r,"retest_session")));out.put("retestHistory",sql.query("SELECT session_id,turn_id,created_at FROM learning_interview_retest_history WHERE plan_id=? AND owner_id=? ORDER BY created_at,session_id",(rs,index)->Map.<String,Object>of("sessionId",rs.getLong(1),"turnId",rs.getLong(2),"createdAt",rs.getTimestamp(3).toString()),id,user));if(r.get("retest_turn")!=null)out.put("comparison",evidence.compare(user,"INTERVIEW_TURN",n(r,"baseline_turn"),"INTERVIEW_TURN",n(r,"retest_turn")));return out;
 }
 @Transactional public Map<String,Object> linkPractice(long user,long id,long mid){guard.lock(user);var r=own(user,id);var m=micros.detail(mid,user);if(!Objects.equals(m.get("noteId"),r.get("note_id"))||!"COMPLETED".equals(m.get("state")))throw new BusinessException(409,"需要当前原题补练笔记的真实交卷");if(m.get("createdAt") instanceof java.sql.Timestamp at&&at.before((java.sql.Timestamp)r.get("created_at")))throw new BusinessException(409,"不能用旧练习冒充当前补练");if(sql.queryForObject("SELECT COUNT(*) FROM learning_interview_practice WHERE plan_id=? AND micro_id=?",Integer.class,id,mid)==0)sql.update("INSERT INTO learning_interview_practice(plan_id,micro_id,owner_id,created_at) VALUES(?,?,?,CURRENT_TIMESTAMP)",id,mid,user);return detail(user,id);}
 @Transactional public Map<String,Object> start(long user,long id,String request){guard.lock(user);var r=own(user,id);if(r.get("retest_session")!=null)return detail(user,id);if(sql.queryForObject("SELECT COUNT(*) FROM mock_interview_v2_state WHERE user_id=? AND start_request=?",Integer.class,user,request)>0)throw new BusinessException(409,"复测标识已用于其他会话");if(((Number)detail(user,id).get("completedCount")).intValue()==0)throw new BusinessException(409,"请先实际提交一次当前原题补练");var session=interviews.startFrozenRetest(user,n(r,"baseline_turn"),request);sql.update("UPDATE learning_interview_retest SET retest_session=? WHERE id=?",session.get("sessionId"),id);sql.update("INSERT INTO interview_radar_session_binding(session_id,user_id,stage,interview_mode,source_review_status) VALUES(?,?,'CLOSED_BOOK','SPECIALTY','ORIGINAL_REFERENCE_RETAINED')",session.get("sessionId"),user);return detail(user,id);}
 @Transactional public Map<String,Object> finish(long user,long id){guard.lock(user);var r=own(user,id);if(r.get("retest_session")==null)throw new BusinessException(409,"尚未创建复测");var session=interviews.get(user,n(r,"retest_session"));if(!"COMPLETED".equals(session.get("state")))throw new BusinessException(409,"请先完成原引擎的真实复测");var turns=sql.queryForList("SELECT id FROM mock_interview_v2_turn WHERE session_id=? AND user_id=? AND depth=0",Long.class,r.get("retest_session"),user);if(turns.size()!=1)throw new BusinessException(409,"复测原答数量不明确");long turn=turns.get(0);evidence.link(user,new LearningEvidenceService.Retest("INTERVIEW_TURN",n(r,"baseline_turn"),"INTERVIEW_TURN",turn));if(sql.queryForObject("SELECT COUNT(*) FROM learning_interview_retest_history WHERE plan_id=? AND session_id=?",Integer.class,id,r.get("retest_session"))==0)sql.update("INSERT INTO learning_interview_retest_history(plan_id,session_id,turn_id,owner_id,created_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP)",id,r.get("retest_session"),turn,user);sql.update("UPDATE learning_interview_retest SET retest_turn=? WHERE id=?",turn,id);return detail(user,id);}
 @Transactional public Map<String,Object> repeat(long user,long id,String request){
  guard.lock(user);var r=own(user,id);if(r.get("retest_session")==null)throw new BusinessException(409,"请先完成首次复测");
  var duplicate=sql.queryForList("SELECT session_id FROM mock_interview_v2_state WHERE user_id=? AND start_request=?",Long.class,user,request);
  if(!duplicate.isEmpty()){if(!Objects.equals(duplicate.get(0),r.get("retest_session")))throw new BusinessException(409,"复测标识已经用于其他记录");return detail(user,id);}
  var previous=interviews.get(user,n(r,"retest_session"));if(!"COMPLETED".equals(previous.get("state")))throw new BusinessException(409,"请先完成正在进行的复测");
  if(r.get("retest_turn")==null)throw new BusinessException(409,"请先保存当前显式复测关联");
  // Idempotent finish also backfills history for plans created before this additive migration.
  finish(user,id);var next=interviews.startFrozenRetest(user,n(r,"baseline_turn"),request);
  sql.update("INSERT INTO interview_radar_session_binding(session_id,user_id,stage,interview_mode,source_review_status) VALUES(?,?,'CLOSED_BOOK','SPECIALTY','ORIGINAL_REFERENCE_RETAINED')",next.get("sessionId"),user);
  sql.update("UPDATE learning_interview_retest SET retest_session=?,retest_turn=NULL WHERE id=?",next.get("sessionId"),id);return detail(user,id);
 }
}
