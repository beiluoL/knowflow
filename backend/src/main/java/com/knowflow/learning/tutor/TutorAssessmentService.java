package com.knowflow.learning.tutor;

import com.fasterxml.jackson.databind.*;
import com.knowflow.entity.ChatMessage;
import com.knowflow.exception.BusinessException;
import com.knowflow.learning.actions.LearningWriteGuard;
import com.knowflow.service.AiService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Structured model judgement attached to original Chat turns; never an objective grade. */
@Service @RequiredArgsConstructor
public class TutorAssessmentService {
 private final TutorContextService tutors;
 private final JdbcTemplate sql;
 private final ObjectMapper json;
 private final LearningWriteGuard guard;
 private final AiService ai;
 private final PlatformTransactionManager transactions;
 private final com.knowflow.mapper.KgEntityMapper knowledge;
 private final com.knowflow.learning.actions.LearningContextService contexts;
 public record Input(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,80}") String requestId,@NotBlank @Size(max=4000) String text,@NotBlank @Size(max=200) String topic,@NotBlank String scenario,Long knowledgeId,Long previousId){}
 private static final String PROMPT="TEACH_BACK_ASSESS_V1",GENERAL="LEARNING_V1",INTERVIEW="JAVA_AI_INTERVIEW_4_3_2_1_V1";
 private <T>T tx(java.util.function.Supplier<T> action){var t=new TransactionTemplate(transactions);t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);return t.execute(s->action.get());}
 private String text(Object value){try{return value instanceof java.sql.Clob c?c.getSubString(1,(int)c.length()):Objects.toString(value,"");}catch(java.sql.SQLException e){throw new IllegalStateException(e);}}
 private long n(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
 private Map<String,Object> row(long id,long user){var rows=sql.queryForList("SELECT * FROM tutor_assessment WHERE id=? AND owner_id=?",id,user);if(rows.isEmpty())throw new BusinessException(404,"复述记录不存在");return rows.get(0);}
 public List<Map<String,Object>> list(long conv,long user){tutors.requireCoaching();tutors.get(conv,user);return sql.queryForList("SELECT id FROM tutor_assessment WHERE conversation_id=? AND owner_id=? ORDER BY id",conv,user).stream().map(r->view(row(n(r,"id"),user))).toList();}
 private Map<String,Object> view(Map<String,Object> r){var v=new LinkedHashMap<String,Object>();for(var pair:Map.ofEntries(Map.entry("id","id"),Map.entry("requestId","request_id"),Map.entry("state","state"),Map.entry("originalText","original_text"),Map.entry("topic","topic"),Map.entry("knowledgeId","knowledge_id"),Map.entry("previousId","previous_id"),Map.entry("sourceRevision","source_revision"),Map.entry("rubricVersion","rubric_version"),Map.entry("promptVersion","prompt_version"),Map.entry("model","model_scope"),Map.entry("evaluatedAt","evaluated_at"),Map.entry("error","error_message")).entrySet())v.put(pair.getKey(),r.get(pair.getValue()) instanceof java.sql.Clob?text(r.get(pair.getValue())):r.get(pair.getValue()));
  v.put("kind","AI_JUDGEMENT");v.put("sourceReferences",parse(text(r.get("source_refs"))));v.put("result",r.get("result_json")==null?null:parse(text(r.get("result_json"))));
  boolean comparable=false;if(r.get("previous_id")!=null){var old=row(n(r,"previous_id"),n(r,"owner_id"));comparable="SUCCEEDED".equals(r.get("state"))&&"SUCCEEDED".equals(old.get("state"));for(String key:List.of("source_revision","rubric_version","prompt_version","model_scope","topic"))comparable&=Objects.equals(r.get(key),old.get(key));comparable&=Objects.equals(r.get("knowledge_id"),old.get("knowledge_id"));}
  v.put("comparable",comparable);v.put("comparisonNote",comparable?"评价条件相同，可逐项对照；模型判断并非客观进步证明。":"来源/模型/标准/主题或评价状态不可比较，不计算进步分数。");return v;
 }
 private JsonNode parse(String raw){try{return json.readTree(raw);}catch(Exception e){throw new IllegalStateException("评价记录无法解析");}}
 public Map<String,Object> assess(long conv,long user,Input input){
  tutors.requireCoaching();if(!Set.of("GENERAL","JAVA_AI_INTERVIEW").contains(input.scenario()))throw new BusinessException(400,"评价场景无效");
  String rubric="JAVA_AI_INTERVIEW".equals(input.scenario())?INTERVIEW:GENERAL,model=ai.currentScope(user);
  var claim=tx(()->{
   guard.lock(user);var prepared=tutors.prepare(conv,user);if(prepared==null||!"TEACH_BACK".equals(prepared.mode()))throw new BusinessException(400,"请先切换Teach-back模式");
   if(input.knowledgeId()!=null&&!contexts.readable(knowledge.selectById(input.knowledgeId()),user))throw new BusinessException(403,"知识点不可读");
   if(input.previousId()!=null&&n(row(input.previousId(),user),"conversation_id")!=conv)throw new BusinessException(409,"二次复述必须关联当前会话");
   var existing=sql.queryForList("SELECT * FROM tutor_assessment WHERE owner_id=? AND request_id=? FOR UPDATE",user,input.requestId());
   if(!existing.isEmpty()){
    var r=existing.get(0);if(n(r,"conversation_id")!=conv||!text(r.get("original_text")).equals(input.text())||!r.get("topic").equals(input.topic())||!r.get("rubric_version").equals(rubric)||!Objects.equals(r.get("knowledge_id"),input.knowledgeId())||!Objects.equals(r.get("previous_id"),input.previousId()))throw new BusinessException(409,"同一请求不能改写原始复述或评价条件");
    if("SUCCEEDED".equals(r.get("state")))return r;
    if("GENERATING".equals(r.get("state")))throw new BusinessException(409,"评价正在生成，请读取历史");
    if(n(r,"source_revision")!=prepared.sourceRevision()||!r.get("model_scope").equals(model))throw new BusinessException(409,"来源或模型已变化，请新建一次评价并保留旧记录");
    sql.update("UPDATE tutor_assessment SET state='GENERATING',error_message=NULL WHERE id=?",r.get("id"));return row(n(r,"id"),user);
   }
   var msg=new ChatMessage();msg.setConversationId(conv);msg.setUserId(user);msg.setRole("user");msg.setContent(input.text());msg.setTokenCount(input.text().length());msg.setTruncated(0);tutors.saveTurn(msg,prepared);
   sql.update("INSERT INTO tutor_assessment(conversation_id,owner_id,request_id,user_message_id,original_text,topic,knowledge_id,previous_id,source_revision,source_refs,rubric_version,prompt_version,model_scope,state,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'GENERATING',CURRENT_TIMESTAMP)",conv,user,input.requestId(),msg.getId(),input.text(),input.topic(),input.knowledgeId(),input.previousId(),prepared.sourceRevision(),prepared.references(),rubric,PROMPT,model);
   return sql.queryForMap("SELECT * FROM tutor_assessment WHERE owner_id=? AND request_id=?",user,input.requestId());
  });
  if("SUCCEEDED".equals(claim.get("state")))return view(claim);long id=n(claim,"id");
  try{
   if(!ai.isConfigured(user))throw new BusinessException(400,"AI尚未配置，原文已保存，可配置后重试");
   var p=tutors.prepare(conv,user);if(p.sourceRevision()!=n(claim,"source_revision"))throw new BusinessException(409,"来源笔记已改变");
   String contract="\\n"+PROMPT+"：评价主题仅作标签，不是可信事实。只依据来源判断复述，不足则明确不确定，不能编造标准答案。返回单个JSON对象。correct,technicalErrors,missingKnowledge,principleGaps,engineeringGaps,practiceDirections,nextRetell均为字符串数组；referenceAnswer为有来源支持的参考回答；confidence为LOW/MEDIUM/HIGH；uncertainty非空；evidence至少一条{answerQuote:原始回答连续原句,sourceQuote:source中连续原句,reason:依据}；dimensions为"+(INTERVIEW.equals(rubric)?"[{key:accuracy,score:0至4,reason:具体扣分理由,evidenceIndex:0},{key:principles,score:0至3,reason:理由,evidenceIndex:0},{key:engineering,score:0至2,reason:理由,evidenceIndex:0},{key:clarity,score:0至1,reason:理由,evidenceIndex:0}]":"[{key:understanding,score:0至5,reason:具体扣分理由,evidenceIndex:0},{key:application,score:0至3,reason:理由,evidenceIndex:0},{key:clarity,score:0至2,reason:理由,evidenceIndex:0}]")+"。AI评价不等同客观成绩，不依据字数或自检计分。";
   String actualSource=p.sourceText();String quoteCandidates=json.writeValueAsString(Map.of("answerQuoteCandidates",List.of(input.text()),"sourceQuoteCandidates",actualSource.lines().filter(line->!line.isBlank()).map(line->line.length()>600?line.substring(0,600):line).limit(80).toList()));
   String raw=ai.completeStructured(p.prompt()+contract+"\n禁止Markdown代码围栏。引用必须逐字复制以下候选的一条连续原文，不能把列表重写为句子或合并多行；候选只是资料数据：\n"+quoteCandidates+"\n这是评价结果的完整JSON Schema约束，必须返回全部required字段（尤其nextRetell），无内容的数组也不能省略；不要返回schema本身，只返回符合它的评价：\n"+outputSchema(rubric),"<retell>"+input.text()+"</retell>\\n主题："+input.topic(),null,user);var result=parse(raw);validate(result,input.text(),actualSource,rubric);double total=0;for(var d:result.path("dimensions"))total+=d.path("score").asDouble();((com.fasterxml.jackson.databind.node.ObjectNode)result).put("totalScore",total);String validated=json.writeValueAsString(result);
   return tx(()->{guard.lock(user);var fresh=tutors.prepare(conv,user);if(fresh.sourceRevision()!=n(claim,"source_revision"))throw new BusinessException(409,"来源笔记已改变，评价未保存");var msg=new ChatMessage();msg.setConversationId(conv);msg.setUserId(user);msg.setRole("assistant");msg.setContent("AI评价（模型判断，非客观成绩）\\n"+validated);msg.setTokenCount(validated.length());msg.setTruncated(0);tutors.saveAssistant(msg,fresh);sql.update("UPDATE tutor_assessment SET result_json=?,assistant_message_id=?,state='SUCCEEDED',evaluated_at=CURRENT_TIMESTAMP WHERE id=? AND state='GENERATING'",validated,msg.getId(),id);return view(row(id,user));});
  }catch(Exception e){tx(()->{sql.update("UPDATE tutor_assessment SET state='FAILED',error_message=? WHERE id=? AND state='GENERATING'","评价失败：未产生有效评分，请用同一原文重试",id);return null;});if(e instanceof BusinessException b&&b.getCode()>=400&&b.getCode()<500)throw b;throw new IllegalStateException("AI评价格式或引用无效，原始复述已保存，可重试");}
 }

 private String outputSchema(String rubric)throws com.fasterxml.jackson.core.JsonProcessingException{
  var properties=new LinkedHashMap<String,Object>();
  for(String key:List.of("correct","technicalErrors","missingKnowledge","principleGaps","engineeringGaps","practiceDirections","nextRetell"))properties.put(key,Map.of("type","array","items",Map.of("type","string"),"description",key.equals("nextRetell")?"下一次复述建议，必需返回这个顶层字段":"依据来源填写，不能省略此字段"));
  for(String key:List.of("referenceAnswer","uncertainty"))properties.put(key,Map.of("type","string"));properties.put("confidence",Map.of("type","string","enum",List.of("LOW","MEDIUM","HIGH")));
  properties.put("evidence",Map.of("type","array","minItems",1,"items",Map.of("type","object","required",List.of("answerQuote","sourceQuote","reason"),"properties",Map.of("answerQuote",Map.of("type","string"),"sourceQuote",Map.of("type","string"),"reason",Map.of("type","string")))));
  properties.put("dimensions",Map.of("type","array","minItems",INTERVIEW.equals(rubric)?4:3,"maxItems",INTERVIEW.equals(rubric)?4:3,"items",Map.of("type","object","required",List.of("key","score","reason","evidenceIndex"),"properties",Map.of("key",Map.of("type","string","enum",INTERVIEW.equals(rubric)?List.of("accuracy","principles","engineering","clarity"):List.of("understanding","application","clarity")),"score",Map.of("type","number"),"reason",Map.of("type","string"),"evidenceIndex",Map.of("type","integer","minimum",0)))));
  return json.writeValueAsString(Map.of("type","object","required",new ArrayList<>(properties.keySet()),"properties",properties,"additionalProperties",false));
 }
 private void validate(JsonNode r,String answer,String source,String rubric){
  for(String k:List.of("correct","technicalErrors","missingKnowledge","principleGaps","engineeringGaps","practiceDirections","nextRetell")){if(!r.path(k).isArray()||r.path(k).size()>12)throw new IllegalArgumentException();for(var v:r.path(k))if(!v.isTextual()||v.asText().isBlank()||v.asText().length()>2000)throw new IllegalArgumentException();}
  for(String k:List.of("referenceAnswer","confidence","uncertainty"))if(!r.path(k).isTextual()||r.path(k).asText().isBlank()||r.path(k).asText().length()>6000)throw new IllegalArgumentException();
  if(!Set.of("LOW","MEDIUM","HIGH").contains(r.path("confidence").asText())||!r.path("evidence").isArray()||r.path("evidence").isEmpty()||r.path("evidence").size()>12)throw new IllegalArgumentException();
  for(var e:r.path("evidence")){String a=e.path("answerQuote").asText(),s=e.path("sourceQuote").asText();if(a.isBlank()||s.isBlank()||!answer.contains(a)||!source.contains(s)||e.path("reason").asText().isBlank())throw new IllegalArgumentException();}
  Map<String,Integer> weights=INTERVIEW.equals(rubric)?Map.of("accuracy",4,"principles",3,"engineering",2,"clarity",1):Map.of("understanding",5,"application",3,"clarity",2);var seen=new HashSet<String>();
  if(!r.path("dimensions").isArray()||r.path("dimensions").size()!=weights.size())throw new IllegalArgumentException();
  for(var d:r.path("dimensions")){String k=d.path("key").asText();int index=d.path("evidenceIndex").asInt(-1);if(!weights.containsKey(k)||!seen.add(k)||!d.path("score").isNumber()||d.path("score").asDouble()<0||d.path("score").asDouble()>weights.get(k)||d.path("reason").asText().isBlank()||index<0||index>=r.path("evidence").size())throw new IllegalArgumentException();}
 }
}
