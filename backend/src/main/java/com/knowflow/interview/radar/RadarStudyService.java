package com.knowflow.interview.radar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.interview.v2.InterviewV2Service;
import com.knowflow.seed.InterviewRadarImportService;
import com.knowflow.exception.BusinessException;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.dto.*;
import com.knowflow.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Reads existing V2 turns; first scores are derived from the first confirmed root, never overwritten. */
@Service @RequiredArgsConstructor
public class RadarStudyService {
 private final RadarInterviewBank bank;
 private final RadarSchema schema;
 private final InterviewV2Service interviews;
 private final JdbcTemplate jdbc;
 private final ObjectMapper json;
 private final SysUserMapper users;
 private final DocCategoryMapper categories;
 private final DocDocumentMapper documents;
 private final LearningService learning;
 private final MistakeService mistakes;
 private final TaskService tasks;
 public record Start(Long questionId,String stage,String interviewMode,String domain,Integer minutes,Integer count,Boolean acknowledgeDraft,String requestId,String projectContext,List<Long> projectDocumentIds){}
 private void actor(Long actor){if(actor==null||users.selectById(actor)==null)throw new BusinessException(401,"请先登录本人账号");}
 private void lock(Long actor){actor(actor);jdbc.queryForList("SELECT id FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE",actor);}
 @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public Map<String,Object> start(Long actor,Start request){
  lock(actor);if(request==null||request.requestId()==null||!request.requestId().matches("[A-Za-z0-9_-]{8,80}"))throw new BusinessException(400,"训练参数或请求标识无效");
  if(!Boolean.TRUE.equals(request.acknowledgeDraft()))throw new BusinessException(400,"请先确认本人待审来源训练及AI评价边界");
  String stage=request.stage()==null?"CLOSED_BOOK":request.stage(),kind=request.interviewMode()==null?"SPECIALTY":request.interviewMode();
  if(!Set.of("CLOSED_BOOK","RETELL","MOCK").contains(stage)||!Set.of("SPECIALTY","COMPREHENSIVE","WEAK","PROJECT","TIMED").contains(kind))throw new BusinessException(400,"不支持的训练阶段或模式");
  int minutes=request.minutes()==null?15:request.minutes(),count=request.count()==null?3:request.count();
  if(minutes<1||minutes>90||count<1||count>5)throw new BusinessException(400,"时长为1–90分钟，模拟面试为1–5题");
  String track=RadarInterviewBank.TRACK,question=null;
  if(!stage.equals("MOCK")){
   if(request.questionId()==null)throw new BusinessException(400,"请选择一道题");
   var material=bank.material(actor,request.questionId());if(!material.metadata().eligible())throw new BusinessException(409,"题干或来源身份待核定，暂不能训练");
   question="RADAR-"+request.questionId();count=1;
   if(stage.equals("RETELL")&&rootTurns(actor).stream().noneMatch(t->questionKey(request.questionId()).equals(t.get("question_id"))))throw new BusinessException(409,"完成本人闭卷回答后才能对照复述");
  }else{
   if(kind.equals("WEAK"))track="RADAR_WEAK";
   else if(kind.equals("SPECIALTY")&&request.domain()!=null&&!request.domain().isBlank())track=RadarInterviewBank.domainTrack(request.domain());
   if(kind.equals("PROJECT")&&(request.projectContext()==null||request.projectContext().isBlank())&&(request.projectDocumentIds()==null||request.projectDocumentIds().isEmpty()))throw new BusinessException(400,"项目面试需要本人提供真实项目描述或可读资料");
  }
  String selectedTrack=track;
  var pool=bank.tracks(actor).stream().filter(t->t.code().equals(selectedTrack)).findFirst().orElseThrow(()->new BusinessException(400,"当前条件下没有可训练题目；薄弱模式需要真实低分记录"));
  count=Math.min(count,pool.questions().size());
  var input=new InterviewV2Service.Start(request.requestId(),track,count,stage.equals("RETELL")?"TEACHING":"STRICT","Java + AI 工程师",minutes,stage.equals("CLOSED_BOOK")?0:2,"HARD",kind.equals("PROJECT")?request.projectContext():null,kind.equals("PROJECT")?request.projectDocumentIds():List.of(),question);
  var result=interviews.startRadar(actor,input);long sid=((Number)result.get("sessionId")).longValue();
  var prior=jdbc.queryForList("SELECT stage,interview_mode,user_id FROM interview_radar_session_binding WHERE session_id=?",sid);
  if(prior.isEmpty())jdbc.update("INSERT INTO interview_radar_session_binding(session_id,user_id,stage,interview_mode,source_review_status) VALUES(?,?,?,?,?)",sid,actor,stage,kind,"PRIVATE_PENDING_REFERENCE");
  else if(!Objects.equals(((Number)prior.get(0).get("user_id")).longValue(),actor)||!stage.equals(prior.get(0).get("stage"))||!kind.equals(prior.get(0).get("interview_mode")))throw new BusinessException(409,"创建标识已用于不同训练阶段");
  var response=new LinkedHashMap<>(result);response.put("trainingStage",stage);response.put("sourceReviewStatus","PRIVATE_PENDING_REFERENCE");response.put("maxAiCalls",count*(stage.equals("CLOSED_BOOK")?1:3));return response;
 }
 private String questionKey(Long id){return "RADAR-"+id;}
 private List<Map<String,Object>> rootTurns(Long actor){
  return jdbc.queryForList("SELECT t.*,b.stage,b.interview_mode FROM mock_interview_v2_turn t JOIN interview_radar_session_binding b ON b.session_id=t.session_id AND b.user_id=t.user_id JOIN mock_interview_session s ON s.id=t.session_id AND s.user_id=t.user_id WHERE t.user_id=? AND t.depth=0 ORDER BY t.id",actor);
 }
 private Double score(Map<String,Object> turn){
  if(turn==null||turn.get("evaluation_json")==null||!"AI".equals(turn.get("source")))return null;
  try{var evaluation=json.readTree(turn.get("evaluation_json").toString());if(!RadarRubric.VERSION.equals(evaluation.path("rubricVersion").asText()))return null;
   var values=json.convertValue(evaluation.path("dimensions"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});return RadarRubric.grade(values).score();
  }catch(com.fasterxml.jackson.core.JsonProcessingException|IllegalArgumentException e){throw new BusinessException(500,"雷达评价记录损坏；未使用替代成绩");}
 }
 public List<Map<String,Object>> questions(Long actor){
  actor(actor);var turns=rootTurns(actor);var result=new ArrayList<Map<String,Object>>();
  for(var q:bank.catalog(actor)){
   var answered=turns.stream().filter(t->questionKey(q.questionId()).equals(t.get("question_id"))).toList();
   var first=answered.stream().filter(t->!"RETELL".equals(t.get("stage"))).findFirst().orElse(null);var recent=answered.isEmpty()?null:answered.get(answered.size()-1);
   Double current=score(recent),initial=score(first),best=answered.stream().map(this::score).filter(Objects::nonNull).max(Double::compareTo).orElse(null);
   var row=new LinkedHashMap<String,Object>();row.put("question",q);row.put("firstScore",initial);row.put("currentScore",current);row.put("bestScore",best);row.put("answeredCount",answered.size());row.put("firstTurnId",first==null?null:first.get("id"));row.put("latestTurnId",recent==null?null:recent.get("id"));row.put("latestSessionId",recent==null?null:recent.get("session_id"));row.put("lastPracticedAt",recent==null?null:recent.get("created_at").toString());row.put("passed",current!=null&&current>=8.5);row.put("everPassed",best!=null&&best>=8.5);
   Long card=index(actor,"review/card/"+q.questionId());row.put("flashcardId",card);boolean due=false;
   if(card!=null){var cards=jdbc.queryForList("SELECT next_review_time FROM learning_flashcard WHERE id=? AND user_id=? AND deleted=0",card,actor);if(!cards.isEmpty()){Object time=cards.get(0).get("next_review_time");due=time==null||!((java.sql.Timestamp)time).toLocalDateTime().isAfter(LocalDateTime.now());}}
   row.put("reviewDue",due);result.add(row);
  }
  return List.copyOf(result);
 }
 public Map<String,Object> overview(Long actor){
  var rows=questions(actor);var domains=new LinkedHashMap<String,List<Map<String,Object>>>();for(String name:List.of("Java 基础","Java 并发","JVM","Spring","MySQL","Redis","消息队列","分布式","大模型基础","RAG","Agent","AI 工程化","项目面试"))domains.put(name,new ArrayList<>());for(var row:rows){var q=(RadarInterviewBank.Question)row.get("question");domains.computeIfAbsent(q.domain(),k->new ArrayList<>()).add(row);}
  var axes=new ArrayList<Map<String,Object>>();
  for(var entry:domains.entrySet()){
   var items=entry.getValue();long answered=items.stream().filter(r->((Number)r.get("answeredCount")).intValue()>0).count();long graded=items.stream().filter(r->r.get("currentScore")!=null).count();
   var axis=new LinkedHashMap<String,Object>();axis.put("domain",entry.getKey());axis.put("total",items.size());axis.put("answered",answered);axis.put("untested",items.size()-answered);axis.put("graded",graded);axis.put("firstAverage",mean(items,"firstScore"));axis.put("currentAverage",mean(items,"currentScore"));axis.put("passed",items.stream().filter(r->Boolean.TRUE.equals(r.get("passed"))).count());axis.put("low",items.stream().filter(r->r.get("currentScore") instanceof Number n&&n.doubleValue()<8.5).count());axis.put("coverage",items.isEmpty()?0:(double)answered/items.size());axis.put("sampleNotice",items.isEmpty()?"待补充来源题目":graded<4?"样本不足，不能据此判断整个领域掌握程度":"仅代表已测题，仍需关注覆盖率");axes.add(axis);
  }
  return Map.of("domains",axes,"questionCount",rows.size(),"assessmentType","AI_REFERENCE_TRAINING_NOT_VERIFIED_MASTERY","rubricVersion",RadarRubric.VERSION,"passScore",8.5,"historyImported",false);
 }
 private Double mean(List<Map<String,Object>> rows,String key){return rows.stream().map(r->r.get(key)).filter(Number.class::isInstance).mapToDouble(v->((Number)v).doubleValue()).average().stream().boxed().findFirst().orElse(null);}
 private Long index(Long actor,String key){var ids=jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",Long.class,InterviewRadarImportService.KEY+actor,key);return ids.isEmpty()?null:ids.get(0);}
 private void index(Long actor,String key,String kind,long id){jdbc.update("INSERT INTO interview_seed_resource(seed_key,resource_key,resource_type,resource_id) VALUES(?,?,?,?)",InterviewRadarImportService.KEY+actor,key,kind,id);}
 @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public Map<String,Long> collectReview(Long actor,long questionId){
  lock(actor);var material=bank.material(actor,questionId);if(!material.metadata().eligible())throw new BusinessException(409,"来源冲突需先核定");
  var answered=rootTurns(actor).stream().filter(t->questionKey(questionId).equals(t.get("question_id"))).toList();if(answered.isEmpty())throw new BusinessException(409,"实际提交回答后才能收集薄弱点");var latest=answered.get(answered.size()-1);
  Long card=index(actor,"review/card/"+questionId),mistake=index(actor,"review/mistake/"+questionId);
  var doc=material.document();var category=categories.selectById(doc.getCategoryId());if(category==null||!actor.equals(category.getOwnerId()))throw new BusinessException(403,"只能启用本人私有资料作为复习来源");
  // The owner explicitly activates a private readable study source; the question remains pending.
  if(!Integer.valueOf(1).equals(doc.getStatus())){doc.setStatus(1);documents.updateById(doc);}
  if(card==null){var dto=new FlashcardSaveDTO();dto.setFront(material.prompt());dto.setBack(material.question().getAnswer()+"\n\n来源为本人待审教材；此卡和AI评价不代表客观掌握度。");dto.setCategoryId(doc.getCategoryId());dto.setDocId(doc.getId());dto.setChapterId(material.metadata().chapterId());dto.setCategory("Java + AI 面试雷达");dto.setTags("interview-radar,private-reference");card=learning.createMyFlashcard(actor,dto).getId();index(actor,"review/card/"+questionId,"FLASHCARD",card);}
  else learning.getMyFlashcard(card,actor);
  if(mistake==null){var m=new LearningMistake();m.setQuestion("["+material.metadata().sourceNamespace()+":"+material.metadata().sourceQuestionId()+"] "+material.prompt());m.setWrongAnswer(latest.get("answer").toString());m.setCorrectAnswer(material.question().getAnswer());m.setQuestionId(questionId);m.setCategory(material.metadata().domain());m.setSource("INTERVIEW_RADAR_USER_COLLECTED");mistakes.addMistake(m,actor);mistake=m.getId();if(mistake==null)mistake=jdbc.queryForObject("SELECT id FROM learning_mistake WHERE user_id=? AND question=? AND deleted=0 LIMIT 1",Long.class,actor,m.getQuestion());index(actor,"review/mistake/"+questionId,"MISTAKE",mistake);}
  else mistakes.getMistakeDetail(mistake,actor);
  return Map.of("flashcardId",card,"mistakeId",mistake);
 }
 @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public Map<String,Long> savePlan(Long actor,int minutes){
  if(!Set.of(15,45,90).contains(minutes))throw new BusinessException(400,"学习计划支持15、45、90分钟");lock(actor);String key="plan/"+LocalDate.now()+"/"+minutes;Long existing=index(actor,key);
  if(existing!=null){var rows=jdbc.queryForList("SELECT id FROM task WHERE id=? AND user_id=? AND deleted=0",existing,actor);if(rows.isEmpty())throw new BusinessException(409,"已保存计划被删除，不自动重建");return Map.of("taskId",existing);}
  var rows=new ArrayList<>(questions(actor));rows.removeIf(r->!((RadarInterviewBank.Question)r.get("question")).eligible());
  if(rows.isEmpty())throw new BusinessException(409,"请先导入并核定至少一道可训练题目");
  rows.sort(Comparator.<Map<String,Object>>comparingInt(r->Boolean.TRUE.equals(r.get("reviewDue"))?0:1).thenComparingDouble(r->r.get("currentScore") instanceof Number n?n.doubleValue():11).thenComparing(r->Objects.toString(((RadarInterviewBank.Question)r.get("question")).priority(),"P9")));
  StringBuilder notes=new StringBuilder("知识复习 → 独立闭卷回答 → 查看真实AI评价或无评分说明 → 补最重要漏洞 → 闭卷复述 → 变式检验。\n不预设完成成绩。计划仅保存在现有任务系统，实际完成由用户操作。\n");
  rows.stream().limit(minutes==15?1:minutes==45?3:5).forEach(r->{var q=(RadarInterviewBank.Question)r.get("question");notes.append(q.sourceQuestionId()).append(" · ").append(q.title()).append("；章节ID ").append(q.chapterId()).append('\n');});
  var dto=new TaskDTO();dto.setTitle("Java + AI 面试雷达 · "+minutes+"分钟");dto.setNotes(notes.toString());dto.setScheduledDate(LocalDate.now());Long id=tasks.createTask(actor,dto);index(actor,key,"TASK",id);return Map.of("taskId",id);
 }
}
