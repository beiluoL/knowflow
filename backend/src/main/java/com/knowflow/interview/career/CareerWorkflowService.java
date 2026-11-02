package com.knowflow.interview.career;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import com.knowflow.interview.core.InterviewQuestionBank;
import com.knowflow.interview.v2.InterviewV2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 确认资料→确定性匹配→冻结面试方案→原V2；不调用第二套评分/学习引擎。 */
@Service @RequiredArgsConstructor
public class CareerWorkflowService {
 private final CareerRecordService records;
 private final JdbcTemplate jdbc;
 private final ObjectMapper json;
 private final InterviewV2Service interviews;
 private final CareerMatchProperties matchPolicy;
 private final com.knowflow.interview.skill.InterviewSkillRegistry registry;
 private final com.knowflow.mapper.KgEntityMapper knowledge;
 private final com.knowflow.security.DocumentAccessPolicy access;
 public record MatchRequest(long resumeId,int resumeVersion,long jobId,int jobVersion,String requestId){}
 public record PlanRequest(long matchId,int projectCount,int coreCount,int gapCount,int designCount,int durationMinutes,String difficulty,String requestId){}
 private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception invalid){throw new BusinessException(400,"快照内容无效");}}
 private Map<String,Object> read(String text){try{return json.readValue(text,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception invalid){throw new BusinessException(500,"快照无法读取");}}
 private List<CareerMatcher.Claim> claims(Map<String,Object> record,String key){
  var sections=json.convertValue(record.get("sections"),new TypeReference<Map<String,List<CareerMatcher.Claim>>>(){});
  return sections.getOrDefault(key,List.of());
 }
 private void key(String key){if(key==null||!key.matches("[A-Za-z0-9_-]{8,100}"))throw new BusinessException(400,"创建标识无效");}
 private Map<String,Object> save(Long user,String kind,String key,Object input,Map<String,Object> payload){
  key(key);String serialized=write(input);
  var existing=jdbc.queryForList("SELECT id,input_json FROM interview_career_snapshot WHERE user_id=? AND kind=? AND request_id=?",user,kind,key);
  if(!existing.isEmpty()){
   if(!serialized.equals(existing.get(0).get("input_json")))throw new BusinessException(409,"创建标识已用于不同参数");
   return get(user,((Number)existing.get(0).get("id")).longValue(),kind);
  }
  var generated=new GeneratedKeyHolder();
  try { jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO interview_career_snapshot(user_id,kind,request_id,input_json,payload_json) VALUES(?,?,?,?,?)",new String[]{"id"});ps.setLong(1,user);ps.setString(2,kind);ps.setString(3,key);ps.setString(4,serialized);ps.setString(5,write(payload));return ps;},generated); }
  catch(org.springframework.dao.DuplicateKeyException duplicate){
   var rows=jdbc.queryForList("SELECT id,input_json FROM interview_career_snapshot WHERE user_id=? AND kind=? AND request_id=?",user,kind,key);
   if(rows.isEmpty()||!serialized.equals(rows.get(0).get("input_json")))throw new BusinessException(409,"创建标识冲突");
   return get(user,((Number)rows.get(0).get("id")).longValue(),kind);
  }
  return get(user,Objects.requireNonNull(generated.getKey()).longValue(),kind);
 }
 public Map<String,Object> get(Long user,long id,String kind){
  var rows=jdbc.queryForList("SELECT payload_json FROM interview_career_snapshot WHERE id=? AND user_id=? AND kind=?",id,user,kind);
  if(rows.isEmpty())throw new BusinessException(404,"求职快照不存在");var result=read(String.valueOf(rows.get(0).get("payload_json")));result.put("id",id);return result;
 }
 public Map<String,Object> match(Long user,MatchRequest request){
  if(request==null)throw new BusinessException(400,"缺少匹配参数");
  var resume=records.detail(user,request.resumeId(),request.resumeVersion());var job=records.detail(user,request.jobId(),request.jobVersion());
  if(!"RESUME".equals(resume.get("kind"))||!"JOB".equals(job.get("kind"))||!Boolean.TRUE.equals(resume.get("confirmed"))||!Boolean.TRUE.equals(job.get("confirmed")))throw new BusinessException(409,"请先确认所选简历和岗位版本");
  var dimensions=CareerMatcher.match(claims(resume,"skills"),claims(job,"requiredSkills"));
  var preferred=CareerMatcher.match(claims(resume,"skills"),claims(job,"preferredSkills"));
  var result=new LinkedHashMap<String,Object>();result.put("resume",resume);result.put("job",job);result.put("required",dimensions);result.put("preferred",preferred);
  result.put("score",dimensions.isEmpty()?null:Math.round(dimensions.stream().filter(d->"MATCHED".equals(d.matchStatus())).count()*10000.0/dimensions.size())/100.0);
  result.put("policyVersion","confirmed-skill-coverage-v1");result.put("scoreMeaning","已确认必须技能的简历证据覆盖率，不是熟练度或招聘成功率");
  var names=claims(job,"requiredSkills").stream().map(CareerMatcher.Claim::value).distinct().toList();
  var entities=names.isEmpty()?List.<com.knowflow.entity.KgEntity>of():knowledge.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.knowflow.entity.KgEntity>().in(com.knowflow.entity.KgEntity::getName,names));
  var mappings=new ArrayList<Map<String,Object>>();
  for(String name:names){
   String canonical=CareerMatcher.canonical(name);
   var skills=registry.list().stream().filter(skill->CareerMatcher.canonical(skill.id()).equals(canonical)||skill.tags().stream().anyMatch(tag->CareerMatcher.canonical(tag).equals(canonical))||("java-concurrency".equals(canonical)&&"JAVA_CONCURRENCY".equals(skill.trackCode()))).map(com.knowflow.interview.skill.InterviewSkillRegistry.Skill::id).toList();
   var nodes=entities.stream().filter(entity->entity.getName().equalsIgnoreCase(name)&&access.canReadKnowledge(entity,user)).map(com.knowflow.entity.KgEntity::getId).toList();
   mappings.add(Map.of("requirement",name,"skillIds",skills,"knowledgeIds",nodes,"mappingStatus",skills.size()==1?"REGISTERED":skills.isEmpty()?"UNMAPPED":"NEEDS_CONFIRMATION"));
  }
  result.put("skillMappings",mappings);
  var reports=new ArrayList<DimensionReport>();
  reports.add(skillReport("requiredSkills",dimensions,claims(job,"requiredSkills")));
  reports.add(skillReport("preferredSkills",preferred,claims(job,"preferredSkills")));
  for(String dimension:List.of("experience","education","projects","responsibilities","domain"))reports.add(qualitative(dimension,claims(resume,dimension),claims(job,dimension)));
  reports.add(new DimensionReport("technicalDepth","技术深度需要实际回答或练习证据",List.of(),"UNCERTAIN","NOT_CALIBRATED","不能从简历关键词推断技术深度",List.of(),null));
  var weights=matchPolicy.checked();double applicable=reports.stream().filter(d->!"NOT_APPLICABLE".equals(d.matchStatus())).mapToInt(d->weights.get(d.id())).sum();
  double observed=reports.stream().filter(d->d.coverage()!=null).mapToInt(d->weights.get(d.id())).sum();
  result.put("dimensions",reports);result.put("weights",weights);
  result.put("overallScore",applicable>0&&observed==applicable?Math.round(reports.stream().filter(d->d.coverage()!=null).mapToDouble(d->d.coverage()*weights.get(d.id())).sum()/applicable*100.0)/100.0:null);
  result.put("evaluationCoverage",applicable==0?0:observed/applicable);result.put("unassessedDimensions",reports.stream().filter(d->"UNCERTAIN".equals(d.matchStatus())).map(DimensionReport::id).toList());
  return save(user,"MATCH",request.requestId(),request,result);
 }
 public record Correction(String requirement,String matchStatus,List<String> resumeEvidence,String reason){}
 public record CorrectRequest(String requestId,List<Correction> corrections){}
 public Map<String,Object> correct(Long user,long id,CorrectRequest request){
  if(request==null||request.corrections()==null||request.corrections().isEmpty()||request.corrections().size()>80)throw new BusinessException(400,"缺少匹配修正");
  var snapshot=get(user,id,"MATCH");var resume=read(write(snapshot.get("resume")));
  String source=String.valueOf(resume.get("source"));
  var dimensions=json.convertValue(snapshot.get("required"),new TypeReference<List<CareerMatcher.Dimension>>(){});
  var revised=new LinkedHashMap<String,CareerMatcher.Dimension>();for(var row:dimensions)revised.put(row.requirement(),row);
  for(var correction:request.corrections()){
   if(correction==null||!revised.containsKey(correction.requirement())||correction.matchStatus()==null||!Set.of("MATCHED","PARTIAL","MISSING","UNCERTAIN","NOT_APPLICABLE").contains(correction.matchStatus())||correction.reason()==null||correction.reason().isBlank()||correction.reason().length()>500||correction.resumeEvidence()==null||correction.resumeEvidence().size()>10)throw new BusinessException(400,"修正状态、要求或理由无效");
   for(String quote:correction.resumeEvidence())if(quote==null||quote.isBlank()||quote.length()>2000||!source.contains(quote))throw new BusinessException(400,"修正证据必须来自该简历版本原文");
   if(Set.of("MATCHED","PARTIAL").contains(correction.matchStatus())&&correction.resumeEvidence().isEmpty())throw new BusinessException(400,"匹配判断必须提供简历证据");
   revised.put(correction.requirement(),new CareerMatcher.Dimension(correction.requirement(),List.copyOf(correction.resumeEvidence()),correction.matchStatus(),correction.reason(),"USER_CONFIRMED_CORRECTION"));
  }
  var values=List.copyOf(revised.values());snapshot.remove("id");snapshot.put("previousMatchId",id);snapshot.put("required",values);snapshot.put("corrections",request.corrections());snapshot.put("overallScore",null);
  boolean uncertain=values.stream().anyMatch(d->Set.of("PARTIAL","UNCERTAIN").contains(d.matchStatus()));
  long applicable=values.stream().filter(d->!"NOT_APPLICABLE".equals(d.matchStatus())).count();
  snapshot.put("score",uncertain||applicable==0?null:values.stream().filter(d->"MATCHED".equals(d.matchStatus())).count()*100.0/applicable);
  var job=read(write(snapshot.get("job")));
  var aggregate=json.convertValue(snapshot.get("dimensions"),new TypeReference<List<DimensionReport>>(){});
  snapshot.put("dimensions",aggregate.stream().map(d->"requiredSkills".equals(d.id())?new DimensionReport(d.id(),d.requirement(),values.stream().flatMap(v->v.resumeEvidence().stream()).toList(),uncertain?"UNCERTAIN":"USER_CORRECTED","USER_CONFIRMED_NOT_VERIFIED","已根据用户原文证据追加修正，旧快照未覆盖",claims(job,"requiredSkills").stream().map(CareerMatcher.Claim::quote).toList(),null):d).toList());
  return save(user,"MATCH",request.requestId(),new java.util.AbstractMap.SimpleImmutableEntry<>(id,request),snapshot);
 }
 public List<Map<String,Object>> history(Long user,String kind){
  if(user==null)throw new BusinessException(401,"请先登录");
  return jdbc.queryForList("SELECT id FROM interview_career_snapshot WHERE user_id=? AND kind=? ORDER BY id DESC LIMIT 30",user,kind).stream().map(row->{long id=((Number)row.get("id")).longValue();var value=get(user,id,kind);var resume=read(write(value.get("resume")));var job=read(write(value.get("job")));return Map.<String,Object>of("id",id,"resumeTitle",resume.get("title"),"jobTitle",job.get("title"));}).toList();
 }
 public record DimensionReport(String id,String requirement,List<String> resumeEvidence,String matchStatus,String confidence,String gapDescription,List<String> requirementEvidence,Double coverage){}
 private DimensionReport skillReport(String id,List<CareerMatcher.Dimension> dimensions,List<CareerMatcher.Claim> requirements){
  double ratio=dimensions.isEmpty()?0:(double)dimensions.stream().filter(d->"MATCHED".equals(d.matchStatus())).count()/dimensions.size();
  return new DimensionReport(id,String.join("、",requirements.stream().map(CareerMatcher.Claim::value).toList()),dimensions.stream().flatMap(d->d.resumeEvidence().stream()).toList(),dimensions.isEmpty()?"NOT_APPLICABLE":ratio==1?"MATCHED":ratio==0?"MISSING":"PARTIAL","USER_CONFIRMED_NOT_VERIFIED","仅比较已确认的原文技能证据",requirements.stream().map(CareerMatcher.Claim::quote).toList(),dimensions.isEmpty()?null:ratio*100);
 }
 private DimensionReport qualitative(String id,List<CareerMatcher.Claim> resume,List<CareerMatcher.Claim> required){
  String status=required.isEmpty()?"NOT_APPLICABLE":"UNCERTAIN";Double coverage=null;
  if(!required.isEmpty()&&required.stream().allMatch(q->resume.stream().anyMatch(r->CareerMatcher.canonical(r.value()).equals(CareerMatcher.canonical(q.value()))))){status="MATCHED";coverage=100.0;}
  if("experience".equals(id)&&required.size()==1&&resume.size()==1&&required.get(0).value().matches("[0-9]{1,2}年")&&resume.get(0).value().matches("[0-9]{1,2}年")){
   int expected=Integer.parseInt(required.get(0).value().replace("年","")),actual=Integer.parseInt(resume.get(0).value().replace("年",""));status=actual>=expected?"MATCHED":"PARTIAL";coverage=expected==0?100:Math.min(100,actual*100.0/expected);
  }
  return new DimensionReport(id,String.join("、",required.stream().map(CareerMatcher.Claim::value).toList()),resume.stream().map(CareerMatcher.Claim::quote).toList(),status,"USER_CONFIRMED_NOT_VERIFIED","无法从原文确定的语义或深度保持待确认，不补造匹配分数",required.stream().map(CareerMatcher.Claim::quote).toList(),coverage);
 }
 public Map<String,Object> plan(Long user,PlanRequest request){
  if(request==null)throw new BusinessException(400,"缺少方案配置");
  int count=request.projectCount()+request.coreCount()+request.gapCount()+request.designCount();
  if(count<1||count>8||request.projectCount()<0||request.coreCount()<0||request.gapCount()<0||request.designCount()<0||request.durationMinutes()<1||request.durationMinutes()>120||!Set.of("EASY","MEDIUM","HARD","SENIOR").contains(request.difficulty()==null?"":request.difficulty()))throw new BusinessException(400,"题量、时长或难度无效");
  var match=get(user,request.matchId(),"MATCH");var resume=read(write(match.get("resume")));var job=read(write(match.get("job")));
  var projects=claims(resume,"projects");var skills=claims(job,"requiredSkills");
  if(request.projectCount()>0&&projects.isEmpty())throw new BusinessException(409,"简历没有已确认项目证据，不能假设项目经历");
  if((request.coreCount()>0||request.gapCount()>0)&&skills.isEmpty())throw new BusinessException(409,"岗位未确认必须技能");
  var requiredRows=json.convertValue(match.get("required"),new TypeReference<List<CareerMatcher.Dimension>>(){});
  var gapNames=requiredRows.stream().filter(d->Set.of("MISSING","PARTIAL","UNCERTAIN").contains(d.matchStatus())).map(d->CareerMatcher.canonical(d.requirement())).collect(java.util.stream.Collectors.toSet());
  var gaps=skills.stream().filter(c->gapNames.contains(CareerMatcher.canonical(c.value()))).toList();
  if(request.gapCount()>0&&gaps.isEmpty())throw new BusinessException(409,"没有未覆盖的必须技能，不能伪造技能缺口");
  var angles=List.of("核心机制与适用条件","验证方法与失败边界","故障排查与恢复","性能容量取舍","安全与权限边界","一致性与幂等","观测指标与证据","扩展性和成本");
  List<Map<String,Object>> questions=new ArrayList<>();
  for(int i=0;i<count;i++){
   String purpose;String question;String quote;
   if(i<request.projectCount()){
    var claim=projects.get(i%projects.size());purpose="RESUME_EVIDENCE";quote=claim.quote();question="简历原文写到“"+quote+"”。请说明你的实际职责，并解释"+angles.get(i/projects.size())+"，提供可验证依据。";
   }else if(i<request.projectCount()+request.coreCount()+request.gapCount()){
    boolean core=i<request.projectCount()+request.coreCount();var pool=core?skills:gaps;int offset=core?i-request.projectCount():i-request.projectCount()-request.coreCount();var claim=pool.get(offset%pool.size());purpose=core?"JOB_CORE":"GAP_VERIFICATION";quote=claim.quote();question="岗位要求“"+quote+"”。请解释 "+claim.value()+" 的"+angles.get(offset/pool.size())+"；没有实践经历时请明确说明。";
   }else{purpose="SYSTEM_DESIGN";quote=String.valueOf(job.get("title"));question="针对岗位“"+quote+"”，请选择一个相关系统场景，说明"+angles.get(i-request.projectCount()-request.coreCount()-request.gapCount())+"。";}
   questions.add(Map.of("questionId","CAREER-"+UUID.nameUUIDFromBytes((request.matchId()+request.requestId()).getBytes(java.nio.charset.StandardCharsets.UTF_8))+"-"+i,"title",question,"questionPurpose",purpose,"evidenceReference",quote,"expectedKnowledgePoints",List.of("技术原理","工程边界","可验证依据")));
  }
  var result=new LinkedHashMap<String,Object>();result.put("matchId",request.matchId());result.put("resume",resume);result.put("job",job);result.put("questions",questions);result.put("durationMinutes",request.durationMinutes());result.put("difficulty",request.difficulty());result.put("generationSource","DETERMINISTIC_CONFIRMED_EVIDENCE");
  return save(user,"PLAN",request.requestId(),request,result);
 }
 @Transactional public Map<String,Object> start(Long user,long planId,String requestId){
  var plan=get(user,planId,"PLAN");
  var resume=read(write(plan.get("resume")));var job=read(write(plan.get("job")));
  records.detail(user,((Number)resume.get("id")).longValue(),((Number)resume.get("version")).intValue());
  records.detail(user,((Number)job.get("id")).longValue(),((Number)job.get("version")).intValue());
  var questions=json.convertValue(plan.get("questions"),new TypeReference<List<Map<String,Object>>>(){});
  var seeds=questions.stream().map(q->new InterviewQuestionBank.SeedQuestion(String.valueOf(q.get("questionId")),String.valueOf(q.get("title")),List.of(String.valueOf(q.get("evidenceReference")),"技术原理与工程边界"),List.of("你会如何验证上述判断的适用边界？"),new InterviewQuestionBank.Rubric("证据不足，需明确未知","能够说明原理及边界","能够以真实依据解释取舍和验证方法"))).toList();
  var session=interviews.startPersonalized(user,requestId,String.valueOf(job.get("title")),((Number)plan.get("durationMinutes")).intValue(),String.valueOf(plan.get("difficulty")),seeds);long sid=((Number)session.get("sessionId")).longValue();
  if(jdbc.queryForObject("SELECT COUNT(*) FROM interview_career_session WHERE user_id=? AND session_id=?",Integer.class,user,sid)==0)jdbc.update("INSERT INTO interview_career_session(user_id,plan_id,session_id) VALUES(?,?,?)",user,planId,sid);
  return session;
 }
}
