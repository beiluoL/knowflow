package com.knowflow.catalog;
import com.knowflow.common.PageResult;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.*;
import com.knowflow.coding.service.CodeProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/** 只读来源适配目录：DB UNION + 真实 count/limit/offset，不改题目表和历史引用。 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class QuestionCatalogService {
 private final QuestionMutationAudit audit;
 private final QuizQuestionAdminService quizAdmin;
 private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
 private final JdbcTemplate jdbc;
 private final QuizQuestionMapper quiz;
 private final LearningScenarioMapper scenarios;
 private final CodeQuestionMapper legacy;
 private final CodeProblemService code;
 private static final Set<String> SOURCES=Set.of("QUIZ","SCENARIO","LEGACY_CODE","CODE_PROBLEM");
 private static final String BANK="""
 SELECT 'QUIZ' source_type,q.id source_question_id,q.canonical_key,q.question_type,q.title,q.content search_text,q.category_id,q.topic_code,NULL knowledge_id,q.difficulty,q.difficulty source_difficulty,q.tags,q.status,q.review_status,q.version source_version,q.create_time,q.update_time FROM quiz_question q WHERE q.deleted=0 AND (q.question_type IS NULL OR q.question_type<>'CODE')
 UNION ALL
 SELECT 'CODE_PROBLEM',q.id,q.canonical_key,'CODE',q.title,q.content,q.category_id,q.topic_code,NULL,q.difficulty,q.difficulty,q.tags,q.status,q.review_status,q.version,q.create_time,q.update_time FROM quiz_question q LEFT JOIN code_problem c ON c.question_id=q.id AND c.deleted=0 WHERE q.deleted=0 AND q.question_type='CODE'
 UNION ALL
 SELECT 'SCENARIO',s.id,NULL,'SCENARIO',s.title,CONCAT(s.context_text,' ',s.question_text),NULL,NULL,s.knowledge_id,s.difficulty,s.difficulty,NULL,s.status,NULL,NULL,s.create_time,s.update_time FROM learning_scenario s WHERE s.deleted=0
 UNION ALL
 SELECT 'LEGACY_CODE',c.id,NULL,'LEGACY_CODE',c.title,c.description,NULL,NULL,NULL,c.difficulty+1,c.difficulty,c.tags,c.status,NULL,NULL,c.create_time,c.update_time FROM code_question c WHERE c.deleted=0
 """;
 public PageResult<QuestionCatalogItem> page(int page,int size,String keyword,String type,String source,Integer difficulty,Integer status,String review,Long category,String topicCode) {
  if(page<1 || size<1 || size>100)throw new BusinessException(400,"分页范围为 page>=1、pageSize 1–100");
  if(source!=null&&!source.isBlank()&&!SOURCES.contains(source))throw new BusinessException(400,"未知题目来源");
  List<Object> args=new ArrayList<>();StringBuilder where=new StringBuilder(" WHERE 1=1");
  if(keyword!=null&&!keyword.isBlank()){where.append(" AND (LOWER(title) LIKE ? ESCAPE '!' OR LOWER(search_text) LIKE ? ESCAPE '!' OR LOWER(tags) LIKE ? ESCAPE '!')");String value="%"+keyword.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";args.add(value);args.add(value);args.add(value);}
  filter(where,args,"question_type",type);filter(where,args,"source_type",source);filter(where,args,"difficulty",difficulty);filter(where,args,"status",status);filter(where,args,"category_id",category);filter(where,args,"topic_code",topicCode);
  if("UNKNOWN".equals(review))where.append(" AND review_status IS NULL");else filter(where,args,"review_status",review);
  String from=" FROM ("+BANK+") catalog"+where;
  Long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());
  List<Object> paged=new ArrayList<>(args);paged.add(size);paged.add(((long)page-1)*size);
  List<QuestionCatalogItem> rows=jdbc.query("SELECT *"+from+" ORDER BY create_time DESC, source_type ASC, source_question_id DESC LIMIT ? OFFSET ?",this::row,paged.toArray());
  for(var item:rows)if(item.isCanonicalEvidence())item.setDuplicateCount(jdbc.queryForObject("SELECT COUNT(*) FROM ("+BANK+") bank WHERE canonical_key=?",Long.class,item.getCanonicalKey()));
  PageResult<QuestionCatalogItem> result=new PageResult<>();result.setRecords(rows);result.setTotal(total==null?0:total);result.setPageNum(page);result.setPageSize(size);result.setPages((result.getTotal()+size-1)/size);return result;
 }
 private static void filter(StringBuilder sql,List<Object> args,String column,Object value){if(value!=null&&!(value instanceof String s&&s.isBlank())){sql.append(" AND ").append(column).append("=?");args.add(value);}}
 private QuestionCatalogItem row(ResultSet r,int index)throws SQLException{
  QuestionCatalogItem v=new QuestionCatalogItem();String source=r.getString("source_type");long id=r.getLong("source_question_id");v.setCatalogId(source+":"+id);v.setSourceType(source);v.setSourceQuestionId(id);String canonical=r.getString("canonical_key");v.setCanonicalEvidence(canonical!=null&&!canonical.isBlank());v.setCanonicalKey(v.isCanonicalEvidence()?canonical:v.getCatalogId());v.setQuestionType(r.getString("question_type"));v.setTitle(r.getString("title"));v.setCategoryId(number(r,"category_id",Long.class));v.setDifficulty(number(r,"difficulty",Integer.class));v.setSourceDifficulty(number(r,"source_difficulty",Integer.class));v.setStatus(number(r,"status",Integer.class));v.setReviewStatus(r.getString("review_status"));v.setSourceVersion(number(r,"source_version",Integer.class));v.setTags(split(r.getString("tags")));Long knowledge=number(r,"knowledge_id",Long.class);v.setKnowledgePointIds(knowledge==null?List.of():List.of(knowledge));String topic=r.getString("topic_code");v.setKnowledgePointCodes(topic==null?List.of():List.of(topic));v.setCreatedAt(r.getTimestamp("create_time")==null?null:r.getTimestamp("create_time").toLocalDateTime());v.setUpdatedAt(r.getTimestamp("update_time")==null?null:r.getTimestamp("update_time").toLocalDateTime());String path=switch(source){case "QUIZ"->"/admin/quiz";case "SCENARIO"->"/admin/scenarios";case "LEGACY_CODE"->"/admin/code-questions";default->"/admin/code-problems";};v.setEditorPath(path+"?questionId="+id);v.setRestrictions(source.equals("SCENARIO")?List.of("场景目前不能直接加入普通组卷；原场景训练流程保留"):source.equals("LEGACY_CODE")?List.of("旧版代码执行不直接加入考试；其浏览器执行/统计体系独立，不能当作安全Judge"):List.of());if(source.equals("CODE_PROBLEM")){var storage=jdbc.queryForList("SELECT id FROM code_problem WHERE question_id=? AND deleted=0",Long.class,id);if(storage.isEmpty())v.setRestrictions(List.of("缺少算法扩展数据；仅基本信息可编辑，专业编辑/判题/组卷不可用，需要修复扩展关系"));else v.setSourceStorageId(storage.get(0));}v.setCapabilities(source.equals("QUIZ")||source.equals("CODE_PROBLEM")?List.of("EDIT","EXAM","PRACTICE","REVIEW","PUBLISH"):List.of("EDIT",source.equals("SCENARIO")?"SCENARIO_PRACTICE":"LEGACY_CODE_PRACTICE"));if(source.equals("CODE_PROBLEM")&&v.getSourceStorageId()==null)v.setCapabilities(List.of("EDIT"));return v;
 }
 @SuppressWarnings("unchecked") private static <T> T number(ResultSet r,String column,Class<T> cls)throws SQLException{Number n=(Number)r.getObject(column);return n==null?null:(T)(cls==Long.class?(Object)n.longValue():(Object)n.intValue());}
 private static List<String> split(String tags){return tags==null?List.of():Arrays.stream(tags.split(",")).map(String::trim).filter(s->!s.isBlank()).distinct().toList();}
 public Map<String,Object> detail(String catalogId){var identity=parse(catalogId);var rows=jdbc.query("SELECT * FROM ("+BANK+") bank WHERE source_type=? AND source_question_id=?",this::row,identity.source(),identity.id());if(rows.isEmpty())throw new BusinessException(404,"题目不存在");if(rows.get(0).isCanonicalEvidence())rows.get(0).setDuplicateCount(jdbc.queryForObject("SELECT COUNT(*) FROM ("+BANK+") bank WHERE canonical_key=?",Long.class,rows.get(0).getCanonicalKey()));Object payload=switch(identity.source()){case "QUIZ"->quiz.selectById(identity.id());case "SCENARIO"->scenarios.selectById(identity.id());case "LEGACY_CODE"->legacy.selectById(identity.id());default->rows.get(0).getSourceStorageId()==null?quiz.selectById(identity.id()):code.adminDetail(identity.id());};rows.get(0).setEditToken(token(payload));return Map.of("item",rows.get(0),"payload",payload);}
 private String token(Object value){return QuestionEditToken.of(objectMapper,value);}
 public record Identity(String source,long id){}
 public static Identity parse(String value){if(value==null||!value.matches("(QUIZ|SCENARIO|LEGACY_CODE|CODE_PROBLEM):[1-9][0-9]*"))throw new BusinessException(400,"题目身份必须包含来源与ID");String[] p=value.split(":");try{return new Identity(p[0],Long.parseLong(p[1]));}catch(NumberFormatException e){throw new BusinessException(400,"题目ID超出范围");}}
 public record BasicEdit(String title,Integer difficulty,String tags,Integer expectedSourceVersion,String expectedUpdatedAt,Long categoryId,String topicCode,List<Long> knowledgePointIds,String expectedEditToken){}
 @org.springframework.transaction.annotation.Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public void editBasic(String catalogId,BasicEdit req){
  if(req.title()==null||req.title().isBlank()||req.title().length()>200||req.difficulty()==null||req.difficulty()<1||req.difficulty()>3)throw new BusinessException(400,"标题不能为空且不超过200字，难度必须为1–3");
  if(req.tags()!=null&&req.tags().length()>500)throw new BusinessException(400,"标签不超过500字");
  var identity=parse(catalogId);String lockedTable=switch(identity.source()){case "SCENARIO"->"learning_scenario";case "LEGACY_CODE"->"code_question";default->"quiz_question";};if(jdbc.queryForList("SELECT id FROM "+lockedTable+" WHERE id=? AND deleted=0 FOR UPDATE",Long.class,identity.id()).isEmpty())throw new BusinessException(404,"题目不存在");var item=(QuestionCatalogItem)detail(catalogId).get("item");
  if((identity.source().equals("SCENARIO")||identity.source().equals("LEGACY_CODE")||req.expectedEditToken()!=null)&&!Objects.equals(item.getEditToken(),req.expectedEditToken()))throw new BusinessException(409,"题目内容已变更，请重新加载后编辑");
  if(!Objects.equals(item.getSourceVersion(),req.expectedSourceVersion()))throw new BusinessException(409,"题目已变更，请重新加载后编辑");
  java.time.LocalDateTime expected=null;try{if(req.expectedUpdatedAt()!=null)expected=java.time.LocalDateTime.parse(req.expectedUpdatedAt().replace(' ','T'));}catch(java.time.format.DateTimeParseException e){throw new BusinessException(400,"更新时间格式错误");}
  if(!Objects.equals(item.getUpdatedAt(),expected))throw new BusinessException(409,"题目已变更，请重新加载后编辑");
  List<Object> args=new ArrayList<>();args.add(req.title().trim());args.add(identity.source().equals("LEGACY_CODE")?req.difficulty()-1:req.difficulty());
  String sql="UPDATE ";String table=switch(identity.source()){case "SCENARIO"->"learning_scenario";case "LEGACY_CODE"->"code_question";default->"quiz_question";};sql+=table+" SET title=?,difficulty=?,update_time=CURRENT_TIMESTAMP";
  if(!identity.source().equals("SCENARIO")){sql+=",tags=?";args.add(req.tags()==null?"":req.tags());}
  if(identity.source().equals("QUIZ")||identity.source().equals("CODE_PROBLEM")){
   if(req.categoryId()!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM doc_category WHERE id=? AND deleted=0",Long.class,req.categoryId())!=1)throw new BusinessException(400,"知识库不存在");
   String topic=req.topicCode()==null||req.topicCode().isBlank()?null:req.topicCode().trim();
   if(topic!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM question_topic WHERE code=? AND deleted=0 AND status=1",Long.class,topic)!=1)throw new BusinessException(400,"知识点编码不存在或已停用");
   sql+=",category_id=?,topic_code=?,version=COALESCE(version,0)+1";args.add(req.categoryId());args.add(topic);
   var q=quiz.selectById(identity.id());if(identity.source().equals("CODE_PROBLEM")||"AI".equals(q.getSource())||q.getReviewStatus()!=null)sql+=",review_status='PENDING_REVIEW',status=0";
  }else if(identity.source().equals("SCENARIO")){
   List<Long> knowledge=req.knowledgePointIds()==null?List.of():req.knowledgePointIds();if(knowledge.size()>1)throw new BusinessException(400,"场景目前仅支持一个知识点");Long knowledgeId=knowledge.isEmpty()?null:knowledge.get(0);
   if(knowledgeId!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM kg_entity WHERE id=? AND deleted=0",Long.class,knowledgeId)!=1)throw new BusinessException(400,"知识点不存在");sql+=",knowledge_id=?";args.add(knowledgeId);
  }
  sql+=" WHERE id=? AND deleted=0";args.add(identity.id());
  if(expected==null)sql+=" AND update_time IS NULL";else{sql+=" AND update_time=?";args.add(java.sql.Timestamp.valueOf(expected));}
  if(identity.source().equals("QUIZ")||identity.source().equals("CODE_PROBLEM")){if(req.expectedSourceVersion()==null)sql+=" AND version IS NULL";else{sql+=" AND version=?";args.add(req.expectedSourceVersion());}}
  if(jdbc.update(sql,args.toArray())!=1)throw new BusinessException(409,"题目已变更，请重新加载后编辑");
  audit.record(identity.source(),identity.id(),"EDIT_BASIC","version="+item.getSourceVersion(),"title/difficulty/tags/classification updated");
 }
 @org.springframework.transaction.annotation.Transactional
 public void batchStatus(List<String> ids,boolean published){
  if(ids==null||ids.isEmpty()||ids.size()>50)throw new BusinessException(400,"批量操作需要1–50个题目身份");
  var identities=ids.stream().distinct().map(QuestionCatalogService::parse).sorted(Comparator.comparingLong(Identity::id).thenComparing(Identity::source)).toList();
  for(var i:identities){if(!Set.of("QUIZ","CODE_PROBLEM").contains(i.source()))throw new BusinessException(400,"该来源不支持统一发布；请使用专业编辑器");var item=(QuestionCatalogItem)detail(i.source()+":"+i.id()).get("item");if(!item.getCapabilities().contains("PUBLISH"))throw new BusinessException(409,"题目缺少专业扩展，不能发布");}
  for(var i:identities){if(i.source().equals("QUIZ"))quizAdmin.publish(i.id(),published);else {code.publish(i.id(),published);audit.record("CODE_PROBLEM",i.id(),published?"PUBLISH":"UNPUBLISH",null,String.valueOf(published));}}
 }
 public record Topic(String code,String name,String parentCode,long questionCount){}
 public Map<String,Object> facets(){
  var counts=jdbc.queryForList("SELECT topic_code,COUNT(*) question_count FROM ("+BANK+") bank WHERE topic_code IS NOT NULL GROUP BY topic_code");Map<String,Long> countByCode=new HashMap<>();for(var row:counts)countByCode.put(String.valueOf(row.get("topic_code")),((Number)row.get("question_count")).longValue());
  var topics=jdbc.query("SELECT code,name,parent_code FROM question_topic WHERE deleted=0 AND status=1 ORDER BY sort_order,id",(rs,index)->new Topic(rs.getString("code"),rs.getString("name"),rs.getString("parent_code"),countByCode.getOrDefault(rs.getString("code"),0L)));
  return Map.of("sources",SOURCES.stream().sorted().toList(),"categories",jdbc.queryForList("SELECT id,name FROM doc_category WHERE deleted=0 ORDER BY id"),"topics",topics,"questionTypes",List.of("SINGLE_CHOICE","MULTIPLE_CHOICE","TRUE_FALSE","FILL_BLANK","SHORT_ANSWER","SCENARIO","LEGACY_CODE","CODE"));
 }
 public record AuditEntry(long id,long actorId,String sourceType,long sourceQuestionId,String action,String beforeState,String afterState,java.time.LocalDateTime createdAt){}
 public PageResult<AuditEntry> auditPage(int page,int size){if(page<1||size<1||size>100)throw new BusinessException(400,"分页范围不正确");var result=new PageResult<AuditEntry>();result.setTotal(jdbc.queryForObject("SELECT COUNT(*) FROM question_catalog_audit",Long.class));result.setPageNum(page);result.setPageSize(size);result.setPages((result.getTotal()+size-1)/size);result.setRecords(jdbc.query("SELECT * FROM question_catalog_audit ORDER BY id DESC LIMIT ? OFFSET ?",(rs,index)->new AuditEntry(rs.getLong("id"),rs.getLong("actor_id"),rs.getString("source_type"),rs.getLong("source_question_id"),rs.getString("action"),rs.getString("before_state"),rs.getString("after_state"),rs.getTimestamp("created_at").toLocalDateTime()),size,((long)page-1)*size));return result;}
}
