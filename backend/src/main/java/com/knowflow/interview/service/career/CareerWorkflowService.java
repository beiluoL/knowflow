package com.knowflow.interview.service.career;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import com.knowflow.interview.core.InterviewQuestionBank;
import com.knowflow.interview.service.v2.InterviewV2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

import com.knowflow.interview.career.CareerMatchProperties;
import com.knowflow.interview.career.CareerMatcher;
import com.knowflow.interview.skill.InterviewSkillRegistry;
/** 确认资料→确定性匹配→冻结面试方案→原V2；不调用第二套评分/学习引擎。 */
@Service @RequiredArgsConstructor
public class CareerWorkflowService {
 private final CareerRecordService records;
 private final JdbcTemplate jdbc;
 private final ObjectMapper json;
 private final InterviewV2Service interviews;
 private final CareerMatchProperties matchPolicy;
 private final com.knowflow.interview.skill.InterviewSkillRegistry registry;
 private final com.knowflow.knowledge.mapper.KgEntityMapper knowledge;
 private final com.knowflow.security.DocumentAccessPolicy access;
 public record MatchRequest(long resumeId,int resumeVersion,long jobId,int jobVersion,String requestId){}
 public record PlanRequest(long matchId,int projectCount,int coreCount,int gapCount,int designCount,int durationMinutes,String difficulty,List<String> focusSkills,String interviewType,String projectDepth,String requestId){}
 /** 方案题目审核后的编辑输入；保存为新方案快照，旧方案保留可追溯。 */
 public record PlanQuestionEdit(String questionId,String title,String questionPurpose,String evidenceReference,
                                List<String> expectedKnowledgePoints,String targetSkill,String source,
                                String scoringDimension,List<String> followupDirections,List<String> skillIds){}
 public record ReviseRequest(String requestId,List<PlanQuestionEdit> questions){}
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
  var rows=jdbc.queryForList("SELECT payload_json,create_time FROM interview_career_snapshot WHERE id=? AND user_id=? AND kind=?",id,user,kind);
  if(rows.isEmpty())throw new BusinessException(404,"求职快照不存在");
  var result=read(String.valueOf(rows.get(0).get("payload_json")));
  result.put("id",id);
  result.put("createdAt",rows.get(0).get("create_time"));
  return result;
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
  var entities=names.isEmpty()?List.<com.knowflow.knowledge.entity.KgEntity>of():knowledge.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.knowflow.knowledge.entity.KgEntity>().in(com.knowflow.knowledge.entity.KgEntity::getName,names));
  var mappings=new ArrayList<Map<String,Object>>();
  for(String name:names){
   String canonical=CareerMatcher.canonical(name);
   var skills=registry.list().stream().filter(skill->CareerMatcher.canonical(skill.id()).equals(canonical)||skill.tags().stream().anyMatch(tag->CareerMatcher.canonical(tag).equals(canonical))||("java-concurrency".equals(canonical)&&"JAVA_CONCURRENCY".equals(skill.trackCode()))).map(com.knowflow.interview.skill.InterviewSkillRegistry.Skill::id).toList();
   var nodes=entities.stream().filter(entity->entity.getName().equalsIgnoreCase(name)&&access.canReadKnowledge(entity,user)).map(com.knowflow.knowledge.entity.KgEntity::getId).toList();
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
  String interviewType=request.interviewType()==null?"TECHNICAL":request.interviewType();
  if(!Set.of("TECHNICAL","PROJECT","MIXED","SYSTEM_DESIGN").contains(interviewType))throw new BusinessException(400,"面试类型无效");
  String projectDepth=request.projectDepth()==null?"MEDIUM":request.projectDepth();
  if(!Set.of("LOW","MEDIUM","HIGH").contains(projectDepth))throw new BusinessException(400,"项目追问强度无效");
  var match=get(user,request.matchId(),"MATCH");var resume=read(write(match.get("resume")));var job=read(write(match.get("job")));
  var projects=claims(resume,"projects");var skills=claims(job,"requiredSkills");
  if(request.projectCount()>0&&projects.isEmpty())throw new BusinessException(409,"简历没有已确认项目证据，不能假设项目经历");
  if((request.coreCount()>0||request.gapCount()>0)&&skills.isEmpty())throw new BusinessException(409,"岗位未确认必须技能");
  var selectedSkills=selectFocusSkills(skills,request.focusSkills());
  var requiredRows=json.convertValue(match.get("required"),new TypeReference<List<CareerMatcher.Dimension>>(){});
  var gapNames=requiredRows.stream().filter(d->Set.of("MISSING","PARTIAL","UNCERTAIN").contains(d.matchStatus())).map(d->CareerMatcher.canonical(d.requirement())).collect(java.util.stream.Collectors.toSet());
  var gaps=skills.stream().filter(c->gapNames.contains(CareerMatcher.canonical(c.value()))).toList();
  if(request.gapCount()>0&&gaps.isEmpty())throw new BusinessException(409,"没有未覆盖的必须技能，不能伪造技能缺口");
  var skillIds=skillIdsByRequirement(match);
  var angles=List.of("核心机制与适用条件","验证方法与失败边界","故障排查与恢复","性能容量取舍","安全与权限边界","一致性与幂等","观测指标与证据","扩展性和成本");
  List<Map<String,Object>> questions=new ArrayList<>();
  for(int i=0;i<count;i++){
   Map<String,Object> question=new LinkedHashMap<>();
   if(i<request.projectCount()){
    var claim=projects.get(i%projects.size());
    int angleIndex=i/projects.size()%angles.size();
    question.put("questionId","CAREER-"+UUID.nameUUIDFromBytes((request.matchId()+request.requestId()).getBytes(java.nio.charset.StandardCharsets.UTF_8))+"-"+i);
    question.put("title","简历原文写到“"+claim.quote()+"”。请说明你的实际职责，并解释"+angles.get(angleIndex)+"，提供可验证依据。");
    question.put("questionPurpose","RESUME_EVIDENCE");
    question.put("evidenceReference",claim.quote());
    question.put("targetSkill","项目经历");
    question.put("source","RESUME_PROJECT");
    question.put("scoringDimension","ENGINEERING_PRACTICE");
    question.put("resumeEvidence",claim.quote());
    question.put("requirement","");
    question.put("expectedKnowledgePoints",List.of("项目实际职责","技术取舍","可验证依据"));
    question.put("followupDirections",followups(angles,angleIndex,projectDepth));
    question.put("skillIds",List.of());
   }else if(i<request.projectCount()+request.coreCount()+request.gapCount()){
    boolean core=i<request.projectCount()+request.coreCount();
    var pool=core?selectedSkills:gaps;
    int offset=core?i-request.projectCount():i-request.projectCount()-request.coreCount();
    var claim=pool.get(offset%pool.size());
    int angleIndex=offset/pool.size()%angles.size();
    String purpose=core?"JOB_CORE":"GAP_VERIFICATION";
    question.put("questionId","CAREER-"+UUID.nameUUIDFromBytes((request.matchId()+request.requestId()).getBytes(java.nio.charset.StandardCharsets.UTF_8))+"-"+i);
    question.put("title","岗位要求“"+claim.quote()+"”。请解释 "+claim.value()+" 的"+angles.get(angleIndex)+"；没有实践经历时请明确说明。");
    question.put("questionPurpose",purpose);
    question.put("evidenceReference",claim.quote());
    question.put("targetSkill",claim.value());
    question.put("source",core?"JD_REQUIRED_SKILL":"JD_GAP");
    question.put("scoringDimension",core?"TECHNICAL_CORRECTNESS":"KNOWLEDGE_COVERAGE");
    question.put("resumeEvidence","");
    question.put("requirement",claim.quote());
    question.put("expectedKnowledgePoints",List.of(claim.value(),"工程边界","可验证依据"));
    question.put("followupDirections",followups(angles,angleIndex,"HIGH".equals(projectDepth)?"HIGH":"MEDIUM"));
    question.put("skillIds",skillIds.getOrDefault(CareerMatcher.canonical(claim.value()),List.of()));
   }else{
    int angleIndex=(i-request.projectCount()-request.coreCount()-request.gapCount())%angles.size();
    String title=String.valueOf(job.get("title"));
    question.put("questionId","CAREER-"+UUID.nameUUIDFromBytes((request.matchId()+request.requestId()).getBytes(java.nio.charset.StandardCharsets.UTF_8))+"-"+i);
    question.put("title","针对岗位“"+title+"”，请选择一个相关系统场景，说明"+angles.get(angleIndex)+"。");
    question.put("questionPurpose","SYSTEM_DESIGN");
    question.put("evidenceReference",title);
    question.put("targetSkill","系统设计");
    question.put("source","SYSTEM_DESIGN");
    question.put("scoringDimension","ENGINEERING_PRACTICE");
    question.put("resumeEvidence","");
    question.put("requirement","");
    question.put("expectedKnowledgePoints",List.of("系统设计","容量与边界","可验证依据"));
    question.put("followupDirections",followups(angles,angleIndex,"HIGH"));
    question.put("skillIds",List.of());
   }
   questions.add(question);
  }
  var result=new LinkedHashMap<String,Object>();
  result.put("matchId",request.matchId());result.put("resume",resume);result.put("job",job);
  result.put("questions",questions);result.put("durationMinutes",request.durationMinutes());
  result.put("difficulty",request.difficulty());result.put("interviewType",interviewType);
  result.put("projectDepth",projectDepth);
  result.put("focusSkills",selectedSkills.stream().map(CareerMatcher.Claim::value).toList());
  result.put("generationSource","DETERMINISTIC_CONFIRMED_EVIDENCE");
  return save(user,"PLAN",request.requestId(),request,result);
 }

 private List<CareerMatcher.Claim> selectFocusSkills(List<CareerMatcher.Claim> required,List<String> focus){
  if(focus==null||focus.isEmpty())return required;
  if(focus.size()>8)throw new BusinessException(400,"技术考察方向最多选择8项");
  var byCanonical=new LinkedHashMap<String,CareerMatcher.Claim>();
  for(var claim:required)byCanonical.putIfAbsent(CareerMatcher.canonical(claim.value()),claim);
  var selected=new ArrayList<CareerMatcher.Claim>();
  for(String name:focus){
   if(name==null||name.isBlank()||name.length()>100)throw new BusinessException(400,"技术考察方向无效");
   var claim=byCanonical.get(CareerMatcher.canonical(name));
   if(claim==null)throw new BusinessException(400,"技术考察方向必须来自岗位已确认的必须技能");
   if(!selected.contains(claim))selected.add(claim);
  }
  return selected.isEmpty()?required:List.copyOf(selected);
 }

 private Map<String,List<String>> skillIdsByRequirement(Map<String,Object> match){
  var result=new LinkedHashMap<String,List<String>>();
  Object raw=match.get("skillMappings");
  if(raw instanceof List<?> rows){
   for(Object item:rows){
    if(item instanceof Map<?,?> row){
     Object requirement=row.get("requirement"),ids=row.get("skillIds");
     if(requirement instanceof String name&&ids instanceof List<?> values){
      result.put(CareerMatcher.canonical(name),values.stream().map(String::valueOf).toList());
     }
    }
   }
  }
  return result;
 }

 private List<String> followups(List<String> angles,int angleIndex,String depth){
  int count="LOW".equals(depth)?1:2;
  var result=new ArrayList<String>();
  for(int step=1;step<=count;step++)result.add(angles.get((angleIndex+step)%angles.size()));
  return List.copyOf(result);
 }

 /** 题目审核保存为新方案快照；原方案保留，正式面试只能从新快照开始。 */
 public Map<String,Object> revise(Long user,long planId,ReviseRequest request){
  if(request==null||request.questions()==null||request.questions().isEmpty()||request.questions().size()>8)
   throw new BusinessException(400,"审核后的题目数量必须为1到8题");
  var plan=get(user,planId,"PLAN");
  var seen=new java.util.HashSet<String>();
  var rows=new ArrayList<Map<String,Object>>();
  for(PlanQuestionEdit edit:request.questions()){
   if(edit==null||edit.questionId()==null||edit.questionId().isBlank()||edit.questionId().length()>100
     ||!seen.add(edit.questionId()))throw new BusinessException(400,"题目标识无效或重复");
   if(edit.title()==null||edit.title().isBlank()||edit.title().length()>500)throw new BusinessException(400,"题目内容无效");
   if(edit.questionPurpose()==null||edit.questionPurpose().isBlank()||edit.questionPurpose().length()>60)throw new BusinessException(400,"题目目的无效");
   if(edit.evidenceReference()==null||edit.evidenceReference().length()>2000)throw new BusinessException(400,"题目证据引用无效");
   if(edit.targetSkill()!=null&&edit.targetSkill().length()>100)throw new BusinessException(400,"目标技能无效");
   if(edit.source()!=null&&edit.source().length()>60)throw new BusinessException(400,"题目来源无效");
   if(edit.scoringDimension()!=null&&edit.scoringDimension().length()>60)throw new BusinessException(400,"评分维度无效");
   if(edit.expectedKnowledgePoints()!=null&&(edit.expectedKnowledgePoints().size()>10||edit.expectedKnowledgePoints().stream().anyMatch(v->v==null||v.length()>100)))
    throw new BusinessException(400,"考察知识点无效");
   if(edit.followupDirections()!=null&&(edit.followupDirections().size()>5||edit.followupDirections().stream().anyMatch(v->v==null||v.length()>100)))
    throw new BusinessException(400,"追问方向无效");
   if(edit.skillIds()!=null&&(edit.skillIds().size()>8||edit.skillIds().stream().anyMatch(v->v==null||v.length()>100)))
    throw new BusinessException(400,"关联技能无效");
   Map<String,Object> row=new LinkedHashMap<>();
   row.put("questionId",edit.questionId());row.put("title",edit.title());
   row.put("questionPurpose",edit.questionPurpose());row.put("evidenceReference",edit.evidenceReference());
   row.put("expectedKnowledgePoints",edit.expectedKnowledgePoints()==null?List.of():List.copyOf(edit.expectedKnowledgePoints()));
   row.put("targetSkill",edit.targetSkill()==null?"":edit.targetSkill());
   row.put("source",edit.source()==null?"USER_EDITED":edit.source());
   row.put("scoringDimension",edit.scoringDimension()==null?"":edit.scoringDimension());
   row.put("followupDirections",edit.followupDirections()==null?List.of():List.copyOf(edit.followupDirections()));
   row.put("skillIds",edit.skillIds()==null?List.of():List.copyOf(edit.skillIds()));
   row.put("resumeEvidence",edit.evidenceReference()==null?"":edit.evidenceReference());
   row.put("requirement","");
   rows.add(row);
  }
  var payload=new LinkedHashMap<>(plan);
  payload.remove("id");
  payload.put("previousPlanId",planId);
  payload.put("questions",rows);
  payload.put("generationSource","USER_REVIEWED");
  payload.put("editedAt",java.time.LocalDateTime.now().toString());
  return save(user,"PLAN",request.requestId(),new java.util.AbstractMap.SimpleImmutableEntry<>(planId,request.questions()),payload);
 }

 /** 返回一个基于同一证据的替代题目，不直接改库；用户确认后再通过 revise 保存。 */
 public Map<String,Object> regenerateQuestion(Long user,long planId,String questionId,String seed){
  var plan=get(user,planId,"PLAN");
  var questions=json.convertValue(plan.get("questions"),new TypeReference<List<Map<String,Object>>>(){});
  Map<String,Object> target=null;
  for(var question:questions)if(questionId!=null&&questionId.equals(String.valueOf(question.get("questionId")))){target=question;break;}
  if(target==null)throw new BusinessException(404,"面试题目不存在");
  var resume=read(write(plan.get("resume")));var job=read(write(plan.get("job")));
  var angles=List.of("核心机制与适用条件","验证方法与失败边界","故障排查与恢复","性能容量取舍","安全与权限边界","一致性与幂等","观测指标与证据","扩展性和成本");
  int angleIndex=Math.floorMod(Objects.hash(questionId,seed==null?"":seed),angles.size());
  String source=String.valueOf(target.get("source"));
  String evidence=String.valueOf(target.get("evidenceReference"));
  Map<String,Object> replacement=new LinkedHashMap<>(target);
  if("RESUME_PROJECT".equals(source)){
   var projects=claims(resume,"projects");
   if(projects.isEmpty())throw new BusinessException(409,"简历没有已确认项目证据，不能重新生成项目题");
   replacement.put("title","简历原文写到“"+projects.get(angleIndex%projects.size()).quote()+"”。请从"+angles.get(angleIndex)+"角度补充你的实际职责、取舍和验证依据。");
  }else if(Set.of("JD_REQUIRED_SKILL","JD_GAP").contains(source)){
   var skills=claims(job,"requiredSkills");
   var claim=skills.stream().filter(v->v.quote().equals(evidence)).findFirst().orElse(skills.isEmpty()?null:skills.get(angleIndex%skills.size()));
   if(claim==null)throw new BusinessException(409,"岗位未确认必须技能，不能重新生成技能题");
   replacement.put("title","岗位要求“"+claim.quote()+"”。请从"+angles.get(angleIndex)+"角度解释 "+claim.value()+"，并说明可验证依据。");
   replacement.put("targetSkill",claim.value());
   replacement.put("evidenceReference",claim.quote());
  }else{
   String title=String.valueOf(job.get("title"));
   replacement.put("title","针对岗位“"+title+"”，请选择一个相关系统场景，从"+angles.get(angleIndex)+"角度说明设计与权衡。");
  }
  replacement.put("followupDirections",followups(angles,angleIndex,"HIGH"));
  replacement.put("regeneratedFrom",questionId);
  return Map.of("planId",planId,"question",replacement);
 }
 @Transactional public Map<String,Object> start(Long user,long planId,String requestId){
  var plan=get(user,planId,"PLAN");
  var resume=read(write(plan.get("resume")));var job=read(write(plan.get("job")));
  records.detail(user,((Number)resume.get("id")).longValue(),((Number)resume.get("version")).intValue());
  records.detail(user,((Number)job.get("id")).longValue(),((Number)job.get("version")).intValue());
  var questions=json.convertValue(plan.get("questions"),new TypeReference<List<Map<String,Object>>>(){});
  if(questions.isEmpty())throw new BusinessException(409,"面试方案没有已确认题目");
  var seeds=questions.stream().map(q->{
   var reference=new ArrayList<String>();
   for(String key:List.of("evidenceReference","targetSkill","requirement")){
    String value=q.get(key)==null?"":String.valueOf(q.get(key));
    if(!value.isBlank()&&!reference.contains(value))reference.add(value);
   }
   Object points=q.get("expectedKnowledgePoints");
   if(points instanceof List<?> list)for(Object point:list){String value=String.valueOf(point);if(!value.isBlank()&&!reference.contains(value))reference.add(value);}
   if(reference.isEmpty())reference.add("技术原理与工程边界");
   Object directions=q.get("followupDirections");
   var followups=directions instanceof List<?> list?list.stream().map(String::valueOf).toList():List.of("你会如何验证上述判断的适用边界？");
   return new InterviewQuestionBank.SeedQuestion(String.valueOf(q.get("questionId")),String.valueOf(q.get("title")),List.copyOf(reference),followups,new InterviewQuestionBank.Rubric("证据不足，需明确未知","能够说明原理及边界","能够以真实依据解释取舍和验证方法"));
  }).toList();
  var session=interviews.startPersonalized(user,requestId,String.valueOf(job.get("title")),((Number)plan.get("durationMinutes")).intValue(),String.valueOf(plan.get("difficulty")),seeds);long sid=((Number)session.get("sessionId")).longValue();
  if(jdbc.queryForObject("SELECT COUNT(*) FROM interview_career_session WHERE user_id=? AND session_id=?",Integer.class,user,sid)==0)jdbc.update("INSERT INTO interview_career_session(user_id,plan_id,session_id) VALUES(?,?,?)",user,planId,sid);
  return session;
 }
}
