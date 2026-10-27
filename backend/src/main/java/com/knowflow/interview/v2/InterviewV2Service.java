package com.knowflow.interview.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import com.knowflow.interview.bridge.InterviewProjectContextService;
import com.knowflow.interview.core.InterviewQuestionBank.SeedQuestion;
import com.knowflow.interview.core.InterviewQuestionBank.Rubric;
import com.knowflow.interview.service.MockInterviewService;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** 一对一扩展原面试会话；所有状态推进、归属、版本、幂等均由服务端决定。 */
@Service
public class InterviewV2Service {
 private final JdbcTemplate jdbc;private final ObjectMapper json;private final MockInterviewService legacy;
 private final InterviewFollowupEngine engine;private final InterviewRubricProperties rubric;private final InterviewProjectContextService projects;
 @org.springframework.beans.factory.annotation.Autowired private com.knowflow.interview.radar.RadarInterviewBank curatedBank;
 private final TransactionTemplate tx;
 private final com.knowflow.service.LearningEventService events;
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"interview-v2-evaluate");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private record Running(long version,Future<?> future){}
 private final ConcurrentHashMap<Long,Running> futures=new ConcurrentHashMap<>();
 public record Start(String requestId,String track,Integer questionCount,String mode,String role,Integer durationMinutes,Integer maxDepth,String difficulty,String projectContext,List<Long> projectDocumentIds,String questionId){}
 public record Answer(Long version,String requestId,String answer,Integer selfScore,Long speechId){}
 public record Command(Long version,String requestId){}
 private record Work(long user,long sid,long turn,long version,SeedQuestion question,String prompt,String answer,int depth,int maxDepth,List<String> asked,String role,String mode,String project,List<Map<String,Object>> history){}
 public InterviewV2Service(JdbcTemplate jdbc,ObjectMapper json,MockInterviewService legacy,InterviewFollowupEngine engine,InterviewRubricProperties rubric,InterviewProjectContextService projects,PlatformTransactionManager manager,com.knowflow.service.LearningEventService events){
  this.jdbc=jdbc;this.json=json;this.legacy=legacy;this.engine=engine;this.rubric=rubric;this.projects=projects;this.events=events;tx=new TransactionTemplate(manager);
  tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
 }
 public Map<String,Object> start(Long user,Start req){
  if(req!=null&&req.track()!=null&&req.track().startsWith("RADAR_"))throw new BusinessException(409,"雷达训练请通过来源确认入口创建");
  return startInternal(user,req,false);
 }
 /** Internal entry after RadarStudyService has checked source ownership and the training stage. */
 public Map<String,Object> startRadar(Long user,Start req){return startInternal(user,req,true);}
 /** Internal curated entry: callers authorize every source; the original engine owns all state and grading. */
 public Map<String,Object> startCuratedRadar(Long user,Start request,List<SeedQuestion> questions){
  if(questions==null||questions.size()!=3||questions.stream().anyMatch(q->!q.id().matches("RADAR-[0-9]+")))throw new BusinessException(400,"模拟面试需要三道已授权原题");
  return startInternal(user,request,true,request.projectDocumentIds()!=null&&!request.projectDocumentIds().isEmpty()?null:List.copyOf(questions));
 }
 /** Starts a new original-engine session with the exact completed root snapshot. No score/answer is copied. */
 public Map<String,Object> startFrozenRetest(Long user,long baselineTurn,String requestId){
  var turns=jdbc.queryForList("SELECT session_id,depth,prompt,question_id FROM mock_interview_v2_turn WHERE id=? AND user_id=?",baselineTurn,user);
  if(turns.isEmpty())throw new BusinessException(404,"原作答不存在");var t=turns.get(0);long sid=number(t,"session_id");var state=own(user,sid,false);
  if(!"COMPLETED".equals(state.get("state"))||!"STRICT".equals(state.get("mode"))||number(t,"depth")!=0)throw new BusinessException(409,"只支持已完成的闭卷原题复测");
  var original=questions(sid).stream().filter(q->q.id().equals(t.get("question_id"))).findFirst().orElseThrow(()->new BusinessException(409,"冻结原题不可用"));
  if(!original.title().equals(t.get("prompt"))||!original.id().matches("RADAR-[0-9]+"))throw new BusinessException(409,"该作答不是可关联的原题主问题");
  var req=new Start(requestId,"RADAR_PRIVATE",1,"STRICT",string(state,"role"),(int)number(state,"duration_minutes"),0,string(state,"difficulty"),null,List.of(),original.id());
  return startInternal(user,req,true,List.of(original));
 }
 private Map<String,Object> startInternal(Long user,Start req,boolean radar){return startInternal(user,req,radar,null);}
 private Map<String,Object> startInternal(Long user,Start req,boolean radar,List<SeedQuestion> frozen){
  if(user==null)throw new BusinessException(401,"未登录");if(req==null)throw new BusinessException(400,"缺少创建参数");key(req.requestId());
  String mode=req.mode()==null?"STRICT":req.mode();if(!Set.of("BASIC","STRICT","TEACHING").contains(mode))throw new BusinessException(400,"面试模式无效");
  String role=req.role()==null?"Java + AI 高级工程师":text(req.role(),100,"目标岗位");
  String difficulty=req.difficulty()==null?"HARD":req.difficulty();if(!Set.of("EASY","MEDIUM","HARD","SENIOR").contains(difficulty))throw new BusinessException(400,"难度无效");
  int minutes=range(req.durationMinutes(),15,1,120,"时长"),depth=range(req.maxDepth(),2,0,3,"追问深度");
  int requested=range(req.questionCount(),3,1,8,"题数");String fingerprint=hash(req);
  Long sid;
  try{sid=tx.execute(status->{
   var previous=jdbc.queryForList("SELECT session_id,start_hash FROM mock_interview_v2_state WHERE user_id=? AND start_request=?",user,req.requestId());
   if(!previous.isEmpty()){if(!fingerprint.equals(previous.get(0).get("start_hash")))throw new BusinessException(409,"创建标识已用于不同参数");return number(previous.get(0),"session_id");}
   // 资料来源在创建前授权；原会话和V2状态一起回滚，避免产生孤立历史。
   var project=projects.resolve(user,req.projectDocumentIds(),req.projectContext());
   boolean projectMode=!project.userDescription().isBlank()||!project.evidence().isEmpty();
   if(projectMode&&req.questionId()!=null&&!req.questionId().isBlank())throw new BusinessException(400,"项目专项不能同时指定题库场景");
   var input=new com.knowflow.interview.vo.InterviewVos.StartRequest();input.setTrack(req.track());input.setQuestionCount(Math.min(requested,5));input.setQuestionId(req.questionId());
   var session=legacy.start(user,input);long id=session.getId();
   var qs=frozen!=null?frozen:projectMode?projectQuestions(requested):questions(id);
   if(radar&&projectMode)qs=qs.stream().map(q->new SeedQuestion("RADAR-PROJECT-"+q.id(),q.title(),q.referencePoints(),q.followups(),q.rubric())).toList();
   if(qs.isEmpty())throw new BusinessException(400,"此方向没有可用面试题");
   if(jdbc.update("UPDATE mock_interview_session SET mode=?,questions_json=?,question_count=? WHERE id=? AND user_id=? AND deleted=0",mode,write(qs),qs.size(),id,user)!=1)throw new BusinessException(409,"会话创建失败");
   LocalDateTime now=LocalDateTime.now();
   jdbc.update("INSERT INTO mock_interview_v2_state(session_id,user_id,start_request,start_hash,version,state,mode,role,difficulty,max_depth,question_index,followup_depth,duration_minutes,deadline,current_prompt,current_question_id,project_json,created_at,updated_at) VALUES(?,?,?,?,0,'ASKING',?,?,?,?,0,0,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
    id,user,req.requestId(),fingerprint,mode,role,difficulty,depth,minutes,Timestamp.valueOf(now.plusMinutes(minutes)),qs.get(0).title(),qs.get(0).id(),write(project));return id;
  });}catch(org.springframework.dao.DuplicateKeyException duplicate){var row=jdbc.queryForList("SELECT session_id,start_hash FROM mock_interview_v2_state WHERE user_id=? AND start_request=?",user,req.requestId());if(row.isEmpty()||!fingerprint.equals(row.get(0).get("start_hash")))throw new BusinessException(409,"创建请求冲突");sid=number(row.get(0),"session_id");}
  return get(user,sid);
 }
 public Map<String,Object> get(Long user,Long sid){
  Map<String,Object> state=own(user,sid,false);expire(user,sid,state);return view(own(user,sid,false));
 }
 public Map<String,Object> answer(Long user,Long sid,Answer req){
  if(req==null)throw new BusinessException(400,"缺少确认答案");key(req.requestId());text(req.answer(),8000,"确认答案");
  if(req.selfScore()!=null&&(req.selfScore()<1||req.selfScore()>5))throw new BusinessException(400,"自评范围为1到5");
  Work work=tx.execute(status->{
   var state=own(user,sid,true);String fingerprint=hash(req);
   var duplicate=jdbc.queryForList("SELECT * FROM mock_interview_v2_turn WHERE user_id=? AND request_id=?",user,req.requestId());
   if(!duplicate.isEmpty()){if(number(duplicate.get(0),"session_id")!=sid||!fingerprint.equals(duplicate.get(0).get("request_hash")))throw new BusinessException(409,"确认标识已用于其他答案");return null;}
   version(state,req.version());
   if(!Set.of("ASKING","FOLLOW_UP","ANSWERING").contains(string(state,"state")))throw new BusinessException(409,"当前状态不能提交答案，请重载会话");
   if(LocalDateTime.now().isAfter(time(state,"deadline")))throw new BusinessException(409,"本场已到时，请重载查看已保存回答");
   String qid=string(state,"current_question_id");
   if(req.speechId()!=null){
    var speech=jdbc.queryForList("SELECT id,v2_version,v2_depth,v2_prompt_hash FROM mock_interview_speech WHERE id=? AND user_id=? AND session_id=? AND question_id=? AND status='TRANSCRIBED'",req.speechId(),user,sid,qid);
    if(speech.isEmpty())throw new BusinessException(400,"语音转写未完成或不属于当前问题");
    var binding=speech.get(0);
    if(!(binding.get("v2_version") instanceof Number version)||version.longValue()!=number(state,"version")
      ||!(binding.get("v2_depth") instanceof Number savedDepth)||savedDepth.intValue()!=number(state,"followup_depth")
      ||!Objects.equals(binding.get("v2_prompt_hash"),com.knowflow.interview.speech.SpeechV2Admission.promptHash(string(state,"current_prompt"))))
      throw new BusinessException(409,"录音对应的问题或版本已变化，请重新录音或使用文字回答");
    if(!jdbc.queryForList("SELECT id FROM mock_interview_v2_turn WHERE speech_id=?",Long.class,req.speechId()).isEmpty())throw new BusinessException(409,"此转写已确认，请重新录音或使用文字");
   }
   var main=questions(sid).get((int)number(state,"question_index"));
   var keyholder=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO mock_interview_v2_turn(session_id,user_id,request_id,request_hash,question_id,prompt,depth,answer,speech_id,self_score,source,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,'PENDING',CURRENT_TIMESTAMP)",new String[]{"id"});
    ps.setLong(1,sid);ps.setLong(2,user);ps.setString(3,req.requestId());ps.setString(4,fingerprint);ps.setString(5,qid);ps.setString(6,string(state,"current_prompt"));ps.setInt(7,(int)number(state,"followup_depth"));ps.setString(8,req.answer().trim());ps.setObject(9,req.speechId());ps.setObject(10,req.selfScore());return ps;},keyholder);
   long turn=Objects.requireNonNull(keyholder.getKey()).longValue(),nextVersion=number(state,"version")+1;
   // 答案先落库再调用AI；网络失败、进程退出都不会丢失用户确认的正文。
   if(jdbc.update("UPDATE mock_interview_v2_state SET state='EVALUATING',version=?,message=NULL,updated_at=CURRENT_TIMESTAMP WHERE session_id=? AND version=?",nextVersion,sid,req.version())!=1)throw new BusinessException(409,"版本已变化");
   var prior=jdbc.queryForList("SELECT prompt,answer FROM mock_interview_v2_turn WHERE session_id=? AND question_id=? ORDER BY id",sid,qid);
   List<String> asked=new ArrayList<>();asked.add(main.title());for(var p:prior)asked.add(string(p,"prompt"));
   List<Map<String,Object>> history=prior.stream().skip(Math.max(0,prior.size()-6)).map(p->Map.<String,Object>of("question",string(p,"prompt"),"confirmedAnswer",string(p,"answer").substring(0,Math.min(2000,string(p,"answer").length())))).toList();
   return new Work(user,sid,turn,nextVersion,main,string(state,"current_prompt"),req.answer().trim(),(int)number(state,"followup_depth"),(int)number(state,"max_depth"),asked,string(state,"role"),string(state,"mode"),string(state,"project_json"),history);
  });
  if(work!=null){
   // 完成回调只能移除自己的Future，避免快速下一轮提交后误删新任务。
   FutureTask<Void> future=new FutureTask<>(()->{apply(work,engine.evaluate(work.user(),work.question(),work.prompt(),work.answer(),work.depth(),work.maxDepth(),work.asked(),work.role(),work.mode(),work.project(),work.history()));return null;}){
    @Override protected void done(){var running=futures.get(work.sid());if(running!=null&&running.future()==this)futures.remove(work.sid(),running);}
   };
   var running=new Running(work.version(),future);futures.put(sid,running);
   try{workers.execute(future);}catch(RejectedExecutionException e){futures.remove(sid,running);apply(work,engine.fallback(work.question(),work.depth(),work.maxDepth(),work.asked(),work.answer(),work.mode(),"AI队列已满，使用预置追问"));}
  }
  return get(user,sid);
 }
 private void apply(Work work,InterviewFollowupEngine.Proposal proposal){
  tx.executeWithoutResult(status->{
   var state=own(work.user(),work.sid(),true);
   if(!"EVALUATING".equals(state.get("state"))||number(state,"version")!=work.version())return;
   jdbc.update("UPDATE mock_interview_v2_turn SET evaluation_json=?,decision=?,source=? WHERE id=? AND session_id=?",proposal.evaluation()==null?null:write(proposal.evaluation()),proposal.decision(),proposal.source(),work.turn(),work.sid());
   if(LocalDateTime.now().isAfter(time(state,"deadline"))){complete(state,"面试时间已到，保留所有已确认回答");return;}
   if("FOLLOW_UP".equals(proposal.decision())&&work.depth()<work.maxDepth()){
    jdbc.update("UPDATE mock_interview_v2_state SET state='FOLLOW_UP',followup_depth=followup_depth+1,current_prompt=?,message=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE session_id=? AND version=?",proposal.followupQuestion(),proposal.message(),work.sid(),work.version());
   }else{
    var qs=questions(work.sid());int next=(int)number(state,"question_index")+1;
    if(next>=qs.size())complete(state,proposal.message());
    else jdbc.update("UPDATE mock_interview_v2_state SET state='ASKING',question_index=?,followup_depth=0,current_prompt=?,current_question_id=?,message=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE session_id=? AND version=?",next,qs.get(next).title(),qs.get(next).id(),proposal.message(),work.sid(),work.version());
   }
  });
 }
 public Map<String,Object> command(Long user,Long sid,String operation,Command req){
  if(req==null)throw new BusinessException(400,"缺少操作参数");key(req.requestId());
  tx.executeWithoutResult(status->{
   var state=own(user,sid,true);String fingerprint=hash(Map.of("session",sid,"operation",operation,"version",req.version()==null?-1:req.version()));
   var previous=jdbc.queryForList("SELECT * FROM mock_interview_v2_command WHERE user_id=? AND request_id=?",user,req.requestId());
   if(!previous.isEmpty()){if(!fingerprint.equals(previous.get(0).get("request_hash")))throw new BusinessException(409,"操作标识已用于其他请求");return;}
   version(state,req.version());String current=string(state,"state");
   if("COMPLETED".equals(current))throw new BusinessException(409,"面试已结束");
   if("EVALUATING".equals(current)&&!"finish".equals(operation))throw new BusinessException(409,"答案已保存，正在评估；请稍后暂停");
   switch(operation){
    case "pause"->{if("PAUSED".equals(current))throw new BusinessException(409,"面试已经暂停");jdbc.update("UPDATE mock_interview_v2_state SET state='PAUSED',paused_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE session_id=?",sid);}
    case "resume"->{if(!"PAUSED".equals(current))throw new BusinessException(409,"面试未暂停");long seconds=Math.max(0,Duration.between(time(state,"paused_at"),LocalDateTime.now()).getSeconds());
     jdbc.update("UPDATE mock_interview_v2_state SET state=?,paused_at=NULL,deadline=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE session_id=?",number(state,"followup_depth")>0?"FOLLOW_UP":"ASKING",Timestamp.valueOf(time(state,"deadline").plusSeconds(seconds)),sid);}
    case "finish"->complete(state,"用户结束面试；未回答和证据不足的维度不计为零分");
    default->throw new BusinessException(400,"未知面试操作");
   }
   jdbc.update("INSERT INTO mock_interview_v2_command(user_id,request_id,session_id,operation,request_hash) VALUES(?,?,?,?,?)",user,req.requestId(),sid,operation,fingerprint);
  });return get(user,sid);
 }
 private void complete(Map<String,Object> state,String message){
  long sid=number(state,"session_id");
  if(jdbc.update("UPDATE mock_interview_v2_state SET state='COMPLETED',paused_at=NULL,message=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE session_id=? AND version=?",message,sid,number(state,"version"))!=1)throw new BusinessException(409,"会话已变化");
  jdbc.update("UPDATE mock_interview_v2_turn SET source='UNASSESSED',decision='NO_EVIDENCE' WHERE session_id=? AND source='PENDING'",sid);
  var scores=jdbc.queryForList("SELECT self_score FROM mock_interview_v2_turn WHERE session_id=? AND self_score IS NOT NULL",Integer.class,sid);
  java.math.BigDecimal avg=scores.isEmpty()?null:java.math.BigDecimal.valueOf(scores.stream().mapToInt(i->i).average().orElseThrow()).setScale(1,java.math.RoundingMode.HALF_UP);
  LocalDateTime started=jdbc.queryForObject("SELECT started_at FROM mock_interview_session WHERE id=?",Timestamp.class,sid).toLocalDateTime();
  long pauses=Math.max(0,Duration.between(started.plusMinutes(number(state,"duration_minutes")),time(state,"deadline")).getSeconds());
  if("PAUSED".equals(state.get("state")))pauses+=Math.max(0,Duration.between(time(state,"paused_at"),LocalDateTime.now()).getSeconds());
  long elapsed=Math.max(0,Math.min(number(state,"duration_minutes")*60,Duration.between(started,LocalDateTime.now()).getSeconds()-pauses));
  jdbc.update("UPDATE mock_interview_session SET status='FINISHED',finished_at=CURRENT_TIMESTAMP,avg_self_score=?,duration_sec=? WHERE id=? AND user_id=? AND deleted=0",avg,elapsed,sid,number(state,"user_id"));
  // 只记录真实完成活动和原始证据引用；不把模型分数写入掌握度或数值Evidence。
  List<Map<String,Object>> provenance=new ArrayList<>();
  for(var turn:jdbc.queryForList("SELECT id,answer,evaluation_json,speech_id FROM mock_interview_v2_turn WHERE session_id=? ORDER BY id",sid)) {
   Map<String,Object> source=new LinkedHashMap<>();source.put("turnId",turn.get("id"));String answer=string(turn,"answer");source.put("confirmedAnswerExcerpt",answer.substring(0,Math.min(500,answer.length())));source.put("answerDigest",hash(answer));source.put("speechId",turn.get("speech_id"));
   if(turn.get("evaluation_json")!=null){var evaluation=readMap(string(turn,"evaluation_json"));source.put("model",evaluation.get("model"));source.put("rubricVersion",evaluation.get("rubricVersion"));source.put("confidence",evaluation.get("confidence"));}
   provenance.add(source);
  }
  events.recordRequired(number(state,"user_id"),"INTERVIEW_COMPLETED","INTERVIEW",sid,Map.of("sessionId",sid,"mode",state.get("mode"),"confirmed",true,"sources",provenance,"assessmentType","ACTIVITY_ONLY_NOT_VERIFIED_MASTERY"));
  var running=futures.remove(sid);if(running!=null&&!Thread.currentThread().getName().startsWith("interview-v2-evaluate"))running.future().cancel(true);
 }
 private void expire(Long user,Long sid,Map<String,Object> initial){
  if("COMPLETED".equals(initial.get("state"))||"PAUSED".equals(initial.get("state")))return;
  if(LocalDateTime.now().isAfter(time(initial,"deadline"))){tx.executeWithoutResult(status->{var state=own(user,sid,true);if(!"COMPLETED".equals(state.get("state"))&&!"PAUSED".equals(state.get("state")))complete(state,"时间已到，已确认回答已保留");});return;}
  if("EVALUATING".equals(initial.get("state"))&&Duration.between(time(initial,"updated_at"),LocalDateTime.now()).getSeconds()>Math.max(1,Math.min(90,rubric.getEvaluationTimeoutSeconds()))){
   var turn=jdbc.queryForList("SELECT * FROM mock_interview_v2_turn WHERE session_id=? ORDER BY id DESC LIMIT 1",sid);
   if(!turn.isEmpty()){var t=turn.get(0);var q=questions(sid).get((int)number(initial,"question_index"));List<String> asked=jdbc.queryForList("SELECT prompt FROM mock_interview_v2_turn WHERE session_id=? AND question_id=?",String.class,sid,string(initial,"current_question_id"));
    Work work=new Work(user,sid,number(t,"id"),number(initial,"version"),q,string(initial,"current_prompt"),string(t,"answer"),(int)number(initial,"followup_depth"),(int)number(initial,"max_depth"),asked,string(initial,"role"),string(initial,"mode"),null,List.of());
    // 先捕获超时这一轮的任务；状态提交后新一轮可能立即开始，不得取消后继任务。
    var running=futures.get(sid);apply(work,engine.fallback(q,work.depth(),work.maxDepth(),asked,work.answer(),work.mode(),"模型任务超时或服务中断，恢复预置追问"));if(running!=null&&running.version()==work.version()&&futures.remove(sid,running))running.future().cancel(true);
   }
  }
 }
 private Map<String,Object> own(Long user,Long sid,boolean lock){
  if(user==null)throw new BusinessException(401,"未登录");
  var rows=jdbc.queryForList("SELECT v.* FROM mock_interview_v2_state v JOIN mock_interview_session s ON s.id=v.session_id WHERE v.session_id=? AND v.user_id=? AND s.user_id=? AND s.deleted=0"+(lock?" FOR UPDATE":""),sid,user,user);
  if(rows.isEmpty())throw new BusinessException(404,"面试记录不存在");var state=rows.get(0);
  // Batch03 sessions must retain current source access on every original-engine entry point.
  var curated=jdbc.queryForList("SELECT session_id FROM interview_radar_session_binding WHERE session_id=? AND user_id=? AND (source_review_status LIKE 'PRODUCT03_%' OR source_review_status='ORIGINAL_REFERENCE_RETAINED')",Long.class,sid,user);
  if(!curated.isEmpty()){
   for(var question:questions(sid))if(question.id().matches("RADAR-[0-9]+"))curatedBank.material(user,Long.parseLong(question.id().substring(6)));
   try{var frozen=json.readTree(string(state,"project_json"));var ids=new ArrayList<Long>();for(var item:frozen.path("evidence"))if(item.path("documentId").isIntegralNumber())ids.add(item.path("documentId").asLong());projects.resolve(user,ids,null);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new BusinessException(500,"项目来源快照损坏");}
  }
  return state;
 }
 private List<SeedQuestion> questions(long sid){try{return json.readValue(jdbc.queryForObject("SELECT questions_json FROM mock_interview_session WHERE id=?",String.class,sid),new TypeReference<List<SeedQuestion>>(){});}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new BusinessException(500,"题目快照损坏");}}
 private Map<String,Object> view(Map<String,Object> state){
  long sid=number(state,"session_id");boolean done="COMPLETED".equals(state.get("state")),show=done||"TEACHING".equals(state.get("mode"));
  List<Map<String,Object>> turns=new ArrayList<>();for(var row:jdbc.queryForList("SELECT * FROM mock_interview_v2_turn WHERE session_id=? AND user_id=? ORDER BY id",sid,number(state,"user_id"))){
   Map<String,Object> turn=new LinkedHashMap<>();turn.put("id",row.get("id"));turn.put("questionId",row.get("question_id"));turn.put("question",row.get("prompt"));turn.put("depth",row.get("depth"));turn.put("answer",row.get("answer"));turn.put("source",row.get("source"));turn.put("selfScore",row.get("self_score"));turn.put("decision",row.get("decision"));
   if(show)turn.put("referencePoints",questions(sid).stream().filter(q->q.id().equals(row.get("question_id"))).findFirst().map(SeedQuestion::referencePoints).orElse(List.of()));
   turn.put("evaluation",show&&row.get("evaluation_json")!=null?readMap(string(row,"evaluation_json")):null);turns.add(turn);
  }
  Map<String,Object> out=new LinkedHashMap<>();out.put("sessionId",sid);out.put("version",state.get("version"));out.put("state",state.get("state"));out.put("mode",state.get("mode"));out.put("role",state.get("role"));out.put("difficulty",state.get("difficulty"));out.put("questionIndex",state.get("question_index"));out.put("questionCount",questions(sid).size());out.put("followupDepth",state.get("followup_depth"));out.put("maxDepth",state.get("max_depth"));
  LocalDateTime now="PAUSED".equals(state.get("state"))?time(state,"paused_at"):LocalDateTime.now();out.put("remainingSeconds",done?0:Math.max(0,Duration.between(now,time(state,"deadline")).getSeconds()));out.put("deadline",time(state,"deadline").toString());
  String questionSource="FOLLOW_UP".equals(state.get("state"))&&!turns.isEmpty()?String.valueOf(turns.get(turns.size()-1).get("source")):"QUESTION_BANK";
  out.put("currentQuestion",done?null:Map.of("id",state.get("current_question_id"),"title",state.get("current_prompt"),"source",questionSource));out.put("turns",turns);out.put("message",state.get("message"));
  if(done){out.put("referencePoints",questions(sid).stream().filter(q->!q.id().startsWith("RADAR-")||turns.stream().anyMatch(t->q.id().equals(t.get("questionId"))&&t.get("answer") instanceof String a&&!a.isBlank())).flatMap(q->q.referencePoints().stream()).distinct().toList());out.put("projectContext",readMap(string(state,"project_json")));out.put("report",report(state,turns));}return out;
 }
 private Map<String,Object> report(Map<String,Object> state,List<Map<String,Object>> turns){
  List<Map<String,Object>> evaluated=new ArrayList<>();for(var turn:turns)if(turn.get("evaluation") instanceof Map<?,?> raw){@SuppressWarnings("unchecked") var value=(Map<String,Object>)raw;evaluated.add(value);}
  Map<String,Integer> weights=evaluated.isEmpty()?rubric.checkedWeights():storedWeights(evaluated.get(0));
  String version=evaluated.isEmpty()?rubric.getVersion():String.valueOf(evaluated.get(0).get("rubricVersion"));
  boolean mixed=evaluated.stream().anyMatch(e->!version.equals(e.get("rubricVersion"))||!weights.equals(storedWeights(e)));
  Map<String,Object> dimensions=dimensionMeans(evaluated,weights,mixed);
  Double overall=!mixed&&evaluated.size()==turns.size()&&completeRadarEvaluations(evaluated,version,weights)?weighted(dimensions,weights):null;
  Map<String,Object> result=new LinkedHashMap<>();result.put("overallScore",overall);result.put("dimensionScores",dimensions);
  result.put("evaluatedTurns",evaluated.size());result.put("answeredTurns",turns.size());result.put("rubricVersion",version);result.put("weights",weights);result.put("mixedRubric",mixed);
  result.put("coverage",turns.isEmpty()?0:(double)evaluated.size()/turns.size());result.put("historyComparison",mixed?null:history(state,overall,version,weights));
  if(com.knowflow.interview.radar.RadarRubric.VERSION.equals(version))result.put("radarOverallScore",overall==null?null:Math.round(overall*10)/100.0);
  result.put("assessmentType","AI_SUGGESTION_NOT_VERIFIED_MASTERY");result.put("evidenceIngestion","ACTIVITY_ONLY_NO_MASTERY_SCORE");return result;
 }
 private boolean completeRadarEvaluations(List<Map<String,Object>> evaluations,String version,Map<String,Integer> weights){
  if(!com.knowflow.interview.radar.RadarRubric.VERSION.equals(version))return true;
  return !evaluations.isEmpty()&&evaluations.stream().allMatch(e->e.get("dimensions") instanceof Map<?,?> dims&&weights.entrySet().stream().allMatch(w->w.getValue()==0||dims.get(w.getKey()) instanceof Number n&&Double.isFinite(n.doubleValue())&&n.doubleValue()>=0&&n.doubleValue()<=100));
 }
 private Map<String,Integer> storedWeights(Map<String,Object> evaluation){
  Map<String,Integer> result=new LinkedHashMap<>();if(evaluation.get("weights") instanceof Map<?,?> raw)for(var entry:raw.entrySet())if(entry.getValue() instanceof Number n)result.put(String.valueOf(entry.getKey()),n.intValue());
  if(result.size()!=5||result.values().stream().mapToInt(i->i).sum()!=100)throw new BusinessException(500,"历史Rubric快照损坏");return result;
 }
 private Map<String,Object> dimensionMeans(List<Map<String,Object>> evaluations,Map<String,Integer> weights,boolean mixed){
  Map<String,Object> result=new LinkedHashMap<>();for(String key:weights.keySet()){
   List<Double> values=new ArrayList<>();if(!mixed)for(var evaluation:evaluations)if(evaluation.get("dimensions") instanceof Map<?,?> dims&&dims.get(key) instanceof Number n)values.add(n.doubleValue());
   result.put(key,values.isEmpty()?null:Math.round(values.stream().mapToDouble(d->d).average().orElseThrow()*10)/10.0);
  }return result;
 }
 private Double weighted(Map<String,Object> dimensions,Map<String,Integer> weights){
  if(weights.entrySet().stream().anyMatch(e->e.getValue()>0&&dimensions.get(e.getKey())==null))return null;
  return Math.round(weights.entrySet().stream().filter(w->w.getValue()>0).mapToDouble(w->((Number)dimensions.get(w.getKey())).doubleValue()*w.getValue()/100).sum()*10)/10.0;
 }
 private Map<String,Object> history(Map<String,Object> state,Double current,String version,Map<String,Integer> weights){
  // 只比较同岗位、难度、方向、模式、Rubric的真实完整评分，且与本场使用同一聚合口径。
  var prior=jdbc.queryForList("SELECT v.session_id FROM mock_interview_v2_state v JOIN mock_interview_session s ON s.id=v.session_id WHERE v.user_id=? AND v.mode=? AND v.role=? AND v.difficulty=? AND s.track=(SELECT track FROM mock_interview_session WHERE id=?) AND s.deleted=0 AND v.state='COMPLETED' AND v.session_id<? ORDER BY v.session_id DESC LIMIT 1",number(state,"user_id"),string(state,"mode"),string(state,"role"),string(state,"difficulty"),number(state,"session_id"),number(state,"session_id"));
  if(prior.isEmpty()||current==null)return null;long sid=number(prior.get(0),"session_id");List<Map<String,Object>> evaluations=new ArrayList<>();
  for(String raw:jdbc.queryForList("SELECT evaluation_json FROM mock_interview_v2_turn WHERE session_id=? ORDER BY id",String.class,sid)){
   if(raw==null)return null;var evaluation=readMap(raw);if(!version.equals(evaluation.get("rubricVersion"))||!weights.equals(storedWeights(evaluation)))return null;evaluations.add(evaluation);
  }
  Double previous=completeRadarEvaluations(evaluations,version,weights)?weighted(dimensionMeans(evaluations,weights,false),weights):null;if(previous==null)return null;
  return Map.of("previousSessionId",sid,"previousScore",previous,"currentScore",current);
 }
 private static List<SeedQuestion> projectQuestions(int count){
  var titles=List.of("请根据你提供的真实资料介绍项目业务目标和整体架构。","项目的技术选型有哪些取舍？请区分已实现部分与规划。","请解释项目一条核心数据链路及你的实际职责。","这条链路有什么设计取舍，依据是什么？","出现故障、事务回滚或模型429时，项目实际上怎样处理？","项目性能有哪些实际测量结果，哪些指标尚未验证？","请说明你个人实现的部分，以及可核对的代码或资料证据。","总结这次项目的限制与下一步验证计划。");
  var out=new ArrayList<SeedQuestion>();for(int i=0;i<Math.min(count,titles.size());i++)out.add(new SeedQuestion("PROJECT-"+(i+1),titles.get(i),List.of("项目业务目标","系统架构","技术选型","核心数据链路","设计取舍","异常处理","性能验证","个人贡献"),List.of("你能指出一项真实资料证据，并说明哪些部分还没有验证吗？","如果这条链路失败，你会怎样验证边界和恢复过程？"),new Rubric("尚缺具体资料","已说明部分链路与本人职责","能用实际资料解释取舍和边界")));return out;
 }
 private void version(Map<String,Object> state,Long version){if(version==null||version!=number(state,"version"))throw new BusinessException(409,"会话版本已变化，请重载；你的文字可以保留后再提交");}
 private void key(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{8,80}"))throw new BusinessException(400,"请求标识无效");}
 private String text(String raw,int bound,String name){if(raw==null||raw.isBlank()||raw.length()>bound)throw new BusinessException(400,name+"不能为空且不超过"+bound+"字");return raw.trim();}
 private int range(Integer n,int fallback,int min,int max,String name){int v=n==null?fallback:n;if(v<min||v>max)throw new BusinessException(400,name+"范围为"+min+"到"+max);return v;}
 private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
 private static String string(Map<String,Object> row,String key){Object v=row.get(key);return v==null?null:String.valueOf(v);}
 private static LocalDateTime time(Map<String,Object> row,String key){Object v=row.get(key);return v instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)v;}
 private String hash(Object body){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(write(body).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new BusinessException(500,"面试数据无法序列化");}}
 private Map<String,Object> readMap(String raw){try{return json.readValue(raw,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new BusinessException(500,"面试快照读取失败");}}
 @PreDestroy public void close(){workers.shutdownNow();}
}
