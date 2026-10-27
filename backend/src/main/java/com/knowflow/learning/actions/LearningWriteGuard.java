package com.knowflow.learning.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.QuizSubmitDTO;
import com.knowflow.exception.BusinessException;
import com.knowflow.vo.QuizSubmitResultVO;
import com.knowflow.entity.Task;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Unique intent/receipt indexes only: original Task, answer records and events remain authoritative. */
@Component @RequiredArgsConstructor
public class LearningWriteGuard {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 private final com.knowflow.mapper.QuizQuestionMapper questions;
 private final com.knowflow.mapper.DocDocumentMapper documents;
 private final PersonalLearningResourceScope personal;
 @PostConstruct void initialize(){
  jdbc.execute("CREATE TABLE IF NOT EXISTS learning_action_owner (user_id BIGINT PRIMARY KEY)");
  jdbc.execute("CREATE TABLE IF NOT EXISTS learning_action_identity (user_id BIGINT NOT NULL,study_date DATE NOT NULL,resource_key VARCHAR(64) NOT NULL,task_id BIGINT NOT NULL,PRIMARY KEY(user_id,study_date,resource_key),UNIQUE(task_id))");
  jdbc.execute("CREATE TABLE IF NOT EXISTS quiz_submit_receipt (user_id BIGINT NOT NULL,request_id VARCHAR(80) NOT NULL,request_hash VARCHAR(64) NOT NULL,result_json TEXT NOT NULL,PRIMARY KEY(user_id,request_id))");
  jdbc.execute("CREATE TABLE IF NOT EXISTS learning_task_evidence (task_id BIGINT PRIMARY KEY,event_id BIGINT NOT NULL)");
 }
 public String identity(PlanModels.Item item){return item.resourceId()!=null?item.resourceType()+":"+item.resourceId():"TOOL:"+item.type()+":"+item.key();}
 public String resourceHash(String type,long id){return hash(type+":"+id);}
 /**
  * 复用原metadata与identity识别证明协议；标题和前端type从不参与身份判定。
  * TOOL/阅读等无完成事件协议的任务仍可手动完成；损坏登记不能退回普通Todo。
  */
 public PlanModels.CompletionRequirement completionRequirement(Task task){
  var identities=jdbc.queryForList("SELECT user_id,resource_key FROM learning_action_identity WHERE task_id=?",task.getId());
  String raw=task.getLearningMetadata();boolean plan=task.getLearningPlanRef()!=null&&!task.getLearningPlanRef().isBlank();
  if(raw==null){if(plan||!identities.isEmpty())throw invalidRegistration();return null;}
  com.fasterxml.jackson.databind.JsonNode meta;
  try{meta=json.readTree(raw);}catch(com.fasterxml.jackson.core.JsonProcessingException error){throw invalidRegistration();}
  if(meta==null||!meta.isObject()||!meta.path("resourceType").isTextual())throw invalidRegistration();
  String type=meta.path("resourceType").asText();
  if(!Set.of("TOOL","CHAPTER","KNOWLEDGE","FLASHCARD","QUIZ").contains(type))throw invalidRegistration();
  Long resource=id(meta,"resourceId"),path=id(meta,"pathId"),knowledge=id(meta,"knowledgeId");
  if(!"TOOL".equals(type)&&resource==null)throw invalidRegistration();
  if("TOOL".equals(type)&&resource!=null)throw invalidRegistration();
  String identity=resource==null?"TOOL:"+meta.path("type").asText()+":"+meta.path("key").asText():type+":"+resource;
  if(!identities.isEmpty()){
   var saved=identities.get(0);
   if(identities.size()!=1||!Objects.equals(task.getUserId(),((Number)saved.get("user_id")).longValue())||!hash(identity).equals(saved.get("resource_key")))throw invalidRegistration();
  }
  var minutes=meta.get("estimatedMinutes");
  if(minutes!=null&&(!minutes.isIntegralNumber()||!minutes.canConvertToInt()||minutes.asInt()<1||minutes.asInt()>720))throw invalidRegistration();
  if(!Set.of("FLASHCARD","QUIZ").contains(type)){
   if(meta.has("completionProtocol")&&!"MANUAL".equals(meta.path("completionProtocol").asText("")))throw invalidRegistration();
   return null;
  }
  if(!plan||identities.isEmpty())throw invalidRegistration();
  var question="QUIZ".equals(type)?questions.selectById(resource):null;
  // 旧metadata没有协议快照：已知主观/代码题从未具备客观提交协议，保留手动合同。
  // 新登记显式冻结协议，不能因后续改题型降级；旧资源丢失/类型未知仍保守保护。
  String legacyProtocol="QUIZ".equals(type)&&question!=null&&Set.of("SHORT_ANSWER","CODE").contains(question.getQuestionType())?"MANUAL":"FLASHCARD".equals(type)?"FSRS_REVIEW":"QUIZ_SUBMISSION";
  String protocol=meta.has("completionProtocol")?meta.path("completionProtocol").asText(""):legacyProtocol;
  if(!Set.of("MANUAL","FSRS_REVIEW","QUIZ_SUBMISSION").contains(protocol))throw invalidRegistration();
  if("MANUAL".equals(protocol)){if("FLASHCARD".equals(type))throw invalidRegistration();return null;}
  if("FLASHCARD".equals(type)!=protocol.equals("FSRS_REVIEW"))throw invalidRegistration();
  boolean available;
  if("QUIZ".equals(type)){
   // 与原计划选择器相同的个人学习权限：Admin管理权限不等于私人资源学习资格。
   available=question!=null&&Integer.valueOf(1).equals(question.getStatus())&&"APPROVED".equals(question.getReviewStatus())
     &&objective(question.getQuestionType())&&personal.category(question.getCategoryId(),task.getUserId())
     &&(question.getDocId()==null||personal.document(documents.selectById(question.getDocId()),task.getUserId()));
  }else available=jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard WHERE id=? AND user_id=? AND deleted=0",Integer.class,resource,task.getUserId())==1;
  // 资源撤下不能隐藏原任务或让它退回可手动完成；读投影保留身份，UI显示不可用。
  return new PlanModels.CompletionRequirement(type,resource,path,knowledge,available);
 }
 /** 新计划冻结协议；改题型/撤下资源不能把已经登记的证明任务降级成普通Todo。 */
 public String taskMetadata(Task previous,PlanModels.Item item){
  var node=json.<com.fasterxml.jackson.databind.node.ObjectNode>valueToTree(item);
  String protocol=null;
  var registered=completionRequirement(previous);
  if(previous.getLearningMetadata()!=null){
   try{var old=json.readTree(previous.getLearningMetadata());if(old.has("completionProtocol"))protocol=old.path("completionProtocol").asText("");}
   catch(com.fasterxml.jackson.core.JsonProcessingException error){throw invalidRegistration();}
  }
  if(protocol==null&&registered!=null)protocol="FLASHCARD".equals(registered.resourceType())?"FSRS_REVIEW":"QUIZ_SUBMISSION";
  if(protocol==null){
   protocol="MANUAL";
   if("FLASHCARD".equals(item.resourceType()))protocol="FSRS_REVIEW";
   else if("QUIZ".equals(item.resourceType())){
    var rows=jdbc.queryForList("SELECT question_type FROM quiz_question WHERE id=? AND deleted=0",item.resourceId());
    if(rows.isEmpty())throw new BusinessException(409,"原学习资源已不可用，请重新预览");
    if(objective(rows.get(0).get("question_type")))protocol="QUIZ_SUBMISSION";
   }
  }
  node.put("completionProtocol",protocol);if(previous.getLearningMetadata()!=null){try{var old=json.readTree(previous.getLearningMetadata());if(old.has("planningLocked"))node.set("planningLocked",old.get("planningLocked"));if("TOOL".equals(item.resourceType())&&"TOOL".equals(old.path("resourceType").asText())&&old.has("key"))node.set("key",old.get("key"));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw invalidRegistration();}}return write(node);
 }
 private boolean objective(Object type){return Set.of("SINGLE_CHOICE","MULTIPLE_CHOICE","TRUE_FALSE","FILL_BLANK").contains(type);}
 public void requireManualTransition(Task task,Integer status,Integer stage){
  if(status!=null&&(status<0||status>1))throw new BusinessException(400,"任务状态只能为待办或已完成");
  if(stage!=null&&(stage<0||stage>2))throw new BusinessException(400,"非法的看板阶段");
  var requirement=completionRequirement(task);if(requirement==null)return;
  if(Integer.valueOf(1).equals(status)||Integer.valueOf(2).equals(stage))throw new BusinessException(409,"此任务需要真实学习记录，请通过对应练习完成，不能手动勾选");
  if(Integer.valueOf(1).equals(task.getStatus())&&(status!=null||stage!=null))throw new BusinessException(409,"此任务已凭学习记录完成，暂不支持手动撤销；原学习证据会保留");
 }
 private Long id(com.fasterxml.jackson.databind.JsonNode meta,String field){
  var value=meta.get(field);if(value==null||value.isNull())return null;
  if(!value.isIntegralNumber()||!value.canConvertToLong()||value.asLong()<=0)throw invalidRegistration();
  return value.asLong();
 }
 private BusinessException invalidRegistration(){return new BusinessException(409,"任务学习资源登记不完整或不一致，请重新加载并检查原学习计划");}
 public void moveTask(long uid,long task,LocalDate date){
  var existing=jdbc.queryForList("SELECT resource_key FROM learning_action_identity WHERE user_id=? AND task_id=?",uid,task);
  if(existing.isEmpty())return;
  var targets=jdbc.queryForList("SELECT i.task_id,t.deleted FROM learning_action_identity i LEFT JOIN task t ON t.id=i.task_id AND t.user_id=i.user_id WHERE i.user_id=? AND i.study_date=? AND i.resource_key=?",uid,date,existing.get(0).get("resource_key"));
  for(var target:targets)if(((Number)target.get("task_id")).longValue()!=task){
   if(target.get("deleted")!=null&&((Number)target.get("deleted")).intValue()==0)throw new BusinessException(409,"目标日期已存在同一资源任务，请重新预览并选择调整项；原计划未改变");
   jdbc.update("DELETE FROM learning_action_identity WHERE user_id=? AND task_id=?",uid,target.get("task_id"));
  }
  jdbc.update("UPDATE learning_action_identity SET study_date=? WHERE user_id=? AND task_id=?",date,uid,task);
 }
 public void lock(long uid){jdbc.update("INSERT INTO learning_action_owner(user_id) VALUES(?) ON DUPLICATE KEY UPDATE user_id=user_id",uid);jdbc.queryForList("SELECT user_id FROM learning_action_owner WHERE user_id=? FOR UPDATE",uid);}
 public Long task(long uid,LocalDate date,String key){
  var rows=jdbc.queryForList("SELECT i.task_id,t.deleted FROM learning_action_identity i LEFT JOIN task t ON t.id=i.task_id AND t.user_id=i.user_id WHERE i.user_id=? AND i.study_date=? AND i.resource_key=?",uid,date,hash(key));
  if(rows.isEmpty())return null;
  if(rows.get(0).get("deleted")==null||((Number)rows.get(0).get("deleted")).intValue()!=0){jdbc.update("DELETE FROM learning_action_identity WHERE user_id=? AND study_date=? AND resource_key=?",uid,date,hash(key));return null;}
  return ((Number)rows.get(0).get("task_id")).longValue();
 }
 public void rememberTask(long uid,LocalDate date,String key,long id){jdbc.update("INSERT INTO learning_action_identity(user_id,study_date,resource_key,task_id) VALUES(?,?,?,?)",uid,date,hash(key),id);}
 public QuizSubmitResultVO quiz(long uid,QuizSubmitDTO input){
  if(input==null||input.getRequestId()==null)return null;
  if(!input.getRequestId().matches("[A-Za-z0-9_-]{8,80}"))throw new BusinessException(400,"无效作答请求编号");
  lock(uid);var rows=jdbc.queryForList("SELECT request_hash,result_json FROM quiz_submit_receipt WHERE user_id=? AND request_id=?",uid,input.getRequestId());
  if(rows.isEmpty())return null;if(!rows.get(0).get("request_hash").equals(hash(write(input.getAnswers()))))throw new BusinessException(409,"同一作答编号内容已变化，请重新开始本轮练习");
  try{return json.readValue((String)rows.get(0).get("result_json"),QuizSubmitResultVO.class);}catch(Exception e){throw new IllegalStateException("Invalid quiz receipt",e);}
 }
 public void rememberQuiz(long uid,QuizSubmitDTO input,QuizSubmitResultVO result){if(input!=null&&input.getRequestId()!=null)jdbc.update("INSERT INTO quiz_submit_receipt(user_id,request_id,request_hash,result_json) VALUES(?,?,?,?)",uid,input.getRequestId(),hash(write(input.getAnswers())),write(result));}
 private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
