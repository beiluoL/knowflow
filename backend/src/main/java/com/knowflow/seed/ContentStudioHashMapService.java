package com.knowflow.seed;

import com.fasterxml.jackson.databind.*;
import com.knowflow.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Explicitly enabled isolated pilot. Cannot write MySQL, normal H2 files, or a production profile. */
@Service @ConditionalOnProperty(name="knowflow.content-studio.qa.enabled",havingValue="true")
public class ContentStudioHashMapService {
 @org.springframework.beans.factory.annotation.Autowired private HashMapAuthorPreview authorPreview;
 public static final String KEY="java-ai-interview-v2/hashmap/2.0.2-qa";
 private final JdbcTemplate jdbc; private final ObjectMapper json; private final Environment env;
 private final JsonNode source; private final String sourceHash; private final String key; private final int questionCount,cardCount,experimentCount;
 @org.springframework.beans.factory.annotation.Autowired
 public ContentStudioHashMapService(JdbcTemplate jdbc,ObjectMapper json,Environment env)throws Exception {this(jdbc,json,env,"hashmap");}
 ContentStudioHashMapService(JdbcTemplate jdbc,ObjectMapper json,Environment env,String topic)throws Exception {
  if(!Set.of("hashmap","threadpool").contains(topic))throw new IllegalArgumentException("Unknown isolated QA topic");
  this.key=topic.equals("hashmap")?KEY:"java-ai-interview-v2/threadpool/2.0.3-qa";
  this.questionCount=topic.equals("hashmap")?9:30;this.cardCount=topic.equals("hashmap")?6:8;this.experimentCount=topic.equals("hashmap")?2:4;
  this.jdbc=jdbc;this.json=json;this.env=env;
  byte[] bytes=Files.readAllBytes(Path.of(env.getRequiredProperty("knowflow.content-studio.qa.package")).resolve("import/"+topic+"-qa.json"));source=json.readTree(bytes);sourceHash=hash(new String(bytes,StandardCharsets.UTF_8));
  if(!key.equals(source.path("version").asText())||!"pending_review".equals(source.path("status").asText())||source.path("humanApproved").asBoolean()||!"PASS".equals(source.path("machineReview").asText())||!"isolated_qa_only".equals(source.path("scope").asText()))throw new IllegalStateException("Not a reviewed isolated QA bundle");
  if(source.path("questions").size()!=questionCount||source.path("cards").size()!=cardCount||source.path("experiments").size()!=experimentCount)throw new IllegalStateException("Incomplete pilot bundle");
 }
 @Transactional(readOnly=true) public Map<String,Object> authorPreview(long actor){actor(actor);context(actor);return authorPreview.preview(actor,chapter(),List.of(kid(1),kid(2),kid(3)));}
 public Map<String,Object> importAuthor(long actor){actor(actor);throw new BusinessException(409,"BLOCKED_BY_HUMAN_REVIEW: 作者稿未获得人工批准，无正式导入");}
 private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private void guard(){
  if(!Arrays.asList(env.getActiveProfiles()).contains("local")||Arrays.stream(env.getActiveProfiles()).anyMatch(p->p.equals("prod")||p.equals("production"))||!"127.0.0.1".equals(env.getProperty("server.address"))||env.getProperty("knowflow.datasource.allow-runtime-switch",Boolean.class,true))throw new BusinessException(403,"Only a locked loopback QA environment is allowed");
  String url=jdbc.execute((ConnectionCallback<String>)c->c.getMetaData().getURL());
  boolean memory=url!=null&&url.startsWith("jdbc:h2:mem:phase3a-qa-test");
  boolean file=false;
  if(url!=null&&url.startsWith("jdbc:h2:file:")){Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();if(root.getFileName().toString().equals("backend"))root=root.getParent();file=isQaDatabaseFile(url,root);}

  if(!memory&&!file)throw new BusinessException(403,"Pilot refuses this database URL");
 }
 static boolean isQaDatabaseFile(String url,Path root){
  if(url==null||!url.startsWith("jdbc:h2:file:"))return false;
  try{Path p=Path.of(url.substring(13).split(";",2)[0]).toAbsolutePath().normalize();Path data=root.toAbsolutePath().normalize().resolve("backend/data");Path parent=p.getParent();
   if(parent==null||!p.startsWith(data)||!parent.getFileName().toString().startsWith("phase3a-qa-")||!p.getFileName().toString().equals("knowledge"))return false;
   Path db=Path.of(p+".mv.db");return Files.isRegularFile(db,LinkOption.NOFOLLOW_LINKS)&&data.toRealPath().equals(data)&&parent.toRealPath().equals(parent)&&db.toRealPath().equals(db);
  }catch(java.io.IOException|java.nio.file.InvalidPathException e){return false;}
 }
 private void actor(long actor){guard();var rows=jdbc.queryForList("SELECT role FROM sys_user WHERE id=? AND deleted=0",actor);if(rows.isEmpty())throw new BusinessException(401,"未登录");if(!"USER".equals(rows.get(0).get("role")))throw new BusinessException(403,"普通用户本人隔离试点");}
 private long old(String key){var ids=jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",Long.class,InterviewSeedCatalog.KEY,key);if(ids.size()!=1)throw new BusinessException(409,"Missing stable original identity: "+key);return ids.get(0);}
 private Long find(String key){var ids=jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",Long.class,this.key,key);return ids.isEmpty()?null:ids.get(0);}
 private long chapter(){return old(source.required("chapterKey").asText());}
 private long kid(int i){return old(source.required("knowledgeKeys").get(i-1).asText());}
 private Map<String,Object> row(String table,long id){var rows=jdbc.queryForList("SELECT * FROM "+table+" WHERE id=? AND deleted=0",id);if(rows.size()!=1)throw new BusinessException(409,"Missing/deleted resource: "+table+"/"+id);return rows.get(0);}
 private Map<String,Object> context(long actor){var c=row("learning_chapter",chapter());var p=row("learning_path",((Number)c.get("path_id")).longValue());if(!"1".equals(str(p.get("status")))||!Set.of("0",Long.toString(actor)).contains(str(p.get("owner_user_id"))))throw new BusinessException(403,"Chapter ownership conflict");for(int i=1;i<=3;i++)row("kg_entity",kid(i));return c;}
 private static String str(Object x){if(x instanceof Clob c){try{return c.getSubString(1,(int)c.length());}catch(SQLException e){throw new IllegalStateException(e);}}return x==null?null:x.toString();}
 private Map<String,Object> fields(Object... kv){var m=new LinkedHashMap<String,Object>();for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);return m;}
 private record Item(String key,String type,String table,Map<String,Object> values,List<Long> knowledge){}
 private List<Item> items(long actor){
  var c=context(actor);long category=old(source.path("categoryKey").asText("shared/java-core/category"));row("doc_category",category);var out=new ArrayList<Item>();Long doc=find("shared/document");
  out.add(new Item("shared/document","DOC","doc_document",fields("title",source.required("title").asText(),"content",source.required("body").asText(),"summary","V2 pending_review 隔离审核内容；非正式发布", "category_id",category,"category_path",Long.toString(category),"difficulty",2,"status",1,"tags",key,"word_count",source.required("body").asText().length(),"source_path",key,"content_hash",sourceHash),List.of(kid(1),kid(2),kid(3))));
  int order=0;for(var q:source.required("questions")){out.add(new Item("shared/question/"+q.required("key").asText(),"QUIZ","quiz_question",fields("title",q.required("title").asText(),"content",q.required("title").asText(),"question_type",q.required("type").asText(),"options",q.path("options").isNull()?null:q.get("options").toString(),"answer",q.required("answer").asText(),"explanation",q.required("explanation").asText(),"difficulty",q.required("difficulty").asInt(),"category_id",category,"doc_id",doc,"status",1,"source","MANUAL","tags",key,"sort_order",order++),List.of(kid(q.required("knowledge").asInt()))));}
  for(var e:source.required("experiments"))out.add(new Item("shared/code/"+e.required("key").asText(),"CODE_QUESTION","code_question",fields("title",e.required("title").asText(),"description",e.required("description").asText().replace("{chapterId}",Long.toString(chapter())),"language","java","code_template",e.required("code").asText(),"example_output",e.required("output").asText(),"solution_hint","先预测输出再运行；隔离教材实验不等于生产验收。","test_cases","[]","difficulty",1,"duration",20,"status",1,"tags",key),List.of(kid(e.required("knowledge").asInt()))));
  for(var f:source.required("cards"))out.add(new Item("user/"+actor+"/card/"+f.required("key").asText(),"FLASHCARD","learning_flashcard",fields("user_id",actor,"path_id",c.get("path_id"),"chapter_id",chapter(),"category_id",category,"doc_id",doc,"front",f.required("front").asText(),"back",f.required("back").asText(),"source_type","IMPORT","difficulty",2,"tags",key),List.of(kid(f.required("knowledge").asInt()))));return out;
 }
 private List<String> conflicts(long actor){var list=new ArrayList<String>();var expected=items(actor);Set<String> keys=new HashSet<>();for(var i:expected)keys.add(i.key());var rows=jdbc.queryForList("SELECT resource_key,resource_type,resource_id FROM interview_seed_resource WHERE seed_key=? AND (resource_key LIKE 'shared/%' OR resource_key LIKE ?)",key,"user/"+actor+"/%");
  for(String group:List.of("shared/","user/"+actor+"/")){var actual=new HashSet<String>();var wanted=new HashSet<String>();for(String k:keys)if(k.startsWith(group))wanted.add(k);for(var r:rows)if(str(r.get("resource_key")).startsWith(group))actual.add(str(r.get("resource_key")));if(!actual.isEmpty()&&!actual.equals(wanted))list.add("Partial or unexpected version resource ledger: "+group);}
  for(var i:expected){Long id=find(i.key());if(id==null)continue;try{String registered=jdbc.queryForObject("SELECT resource_type FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",String.class,key,i.key());if(!i.type().equals(registered))list.add(i.key()+": resource type conflict");var actual=row(i.table(),id);for(var e:i.values().entrySet())if(!Objects.equals(str(e.getValue()),str(actual.get(e.getKey()))))list.add(i.key()+": edited/conflicting "+e.getKey());for(long k:i.knowledge()){int n=jdbc.queryForObject("SELECT COUNT(*) FROM resource_knowledge_mapping WHERE resource_type=? AND resource_id=? AND knowledge_id=? AND deleted=0 AND status='ACCEPTED'",Integer.class,i.type(),id,k);if(n!=1)list.add(i.key()+": missing knowledge link "+k);}}catch(BusinessException e){list.add(e.getMessage());}}
  return list;
 }
 private boolean active(long actor){return jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_activation WHERE seed_key=? AND user_id=?",Integer.class,key,actor)==1;}
 @Transactional(readOnly=true) public Map<String,Object> dryRun(long actor){actor(actor);var conflicts=conflicts(actor);var mappings=new ArrayList<Map<String,Object>>();for(var i:items(actor))mappings.add(fields("contentUnitId",i.key(),"businessEntity",i.table(),"businessId",find(i.key()),"semanticId",semantic(i.key()),"knowledgeIds",i.knowledge(),"operation",find(i.key())==null?"APPEND_VERSION":"PRESERVE_EXISTING"));var result=fields("dryRun",true,"version",key,"contentStatus","pending_review","humanApproved",false,"scope","isolated_qa_only","chapterId",chapter(),"pathId",context(actor).get("path_id"),"sourceHash",sourceHash,"activated",active(actor),"conflicts",conflicts,"mappings",mappings,"changes",fields("existingRowsUpdated",0,"quizVersions",questionCount,"cards",cardCount,"codeExercises",experimentCount,"documents",1));result.put("planHash",hash(result.toString()));return result;}
 private String semantic(String key){for(var q:source.required("questions"))if(key.equals("shared/question/"+q.required("key").asText()))return q.required("canonicalKey").asText();return key;}
 private long insert(Item i){var holder=new GeneratedKeyHolder();String columns=String.join(",",i.values().keySet());String marks=String.join(",",Collections.nCopies(i.values().size(),"?"));jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO "+i.table()+"("+columns+") VALUES("+marks+")",new String[]{"id"});int n=1;for(Object value:i.values().values())ps.setObject(n++,value);return ps;},holder);long id=Objects.requireNonNull(holder.getKey()).longValue();jdbc.update("INSERT INTO interview_seed_resource(seed_key,resource_key,resource_type,resource_id) VALUES(?,?,?,?)",key,i.key(),i.type(),id);for(long k:i.knowledge())jdbc.update("INSERT INTO resource_knowledge_mapping(resource_type,resource_id,knowledge_id,source,confidence,status) VALUES(?,?,?,'IMPORT',1,'ACCEPTED')",i.type(),id,k);return id;}
 @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Map<String,Object> apply(long actor,String expectedPlanHash){actor(actor);jdbc.queryForList("SELECT id FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE",actor);jdbc.queryForList("SELECT seed_key FROM interview_seed_pack WHERE seed_key=? FOR UPDATE",InterviewSeedCatalog.KEY);
  if(jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_activation WHERE seed_key=? AND user_id=?",Integer.class,InterviewSeedCatalog.KEY,actor)!=1)throw new BusinessException(409,"Activate original course first");
  var plan=dryRun(actor);if(!Objects.equals(expectedPlanHash,plan.get("planHash"))||!((List<?>)plan.get("conflicts")).isEmpty())throw new BusinessException(409,"Stale plan or user edit/link conflict; no content overwritten");
  for(var i:items(actor))if(i.key().equals("shared/document")&&find(i.key())==null)insert(i);
  for(var i:items(actor))if(!i.key().equals("shared/document")&&find(i.key())==null)insert(i);
  if(!active(actor))jdbc.update("INSERT INTO interview_seed_activation(seed_key,user_id,goal_id,activated_at) SELECT ?,user_id,goal_id,CURRENT_TIMESTAMP FROM interview_seed_activation WHERE seed_key=? AND user_id=?",key,InterviewSeedCatalog.KEY,actor);
  var after=dryRun(actor);if(!((List<?>)after.get("conflicts")).isEmpty())throw new BusinessException(409,"Post import integrity failure");return fields("dryRun",false,"mapping",after,"revision",revision(actor,chapter()));
 }
 /** Rollback only selection; appended versions, cards, review schedules and every history row survive. */
 @Transactional(rollbackFor=Exception.class) public Map<String,Object> rollback(long actor){actor(actor);jdbc.queryForList("SELECT id FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE",actor);int changed=jdbc.update("DELETE FROM interview_seed_activation WHERE seed_key=? AND user_id=?",key,actor);return fields("version",key,"selectionRolledBack",changed>0,"contentDeleted",false,"learningHistoryTouched",false);}
 @Transactional(readOnly=true) public Map<String,Object> revision(long actor,long chapter){actor(actor);if(chapter()!=chapter||!active(actor))return null;context(actor);var errors=conflicts(actor);if(!errors.isEmpty())throw new BusinessException(409,"QA revision integrity conflict: "+errors.get(0));var d=row("doc_document",Objects.requireNonNull(find("shared/document")));var qs=new ArrayList<Map<String,Object>>();for(var q:source.required("questions")){var r=row("quiz_question",Objects.requireNonNull(find("shared/question/"+q.required("key").asText())));qs.add(fields("id",r.get("id"),"canonicalKey",q.required("canonicalKey").asText(),"parentKey",q.path("parentKey").isNull()?null:q.get("parentKey").asText(),"title",r.get("title"),"answer",str(r.get("answer")),"explanation",str(r.get("explanation")),"questionType",r.get("question_type"),"options",str(r.get("options")),"importance","S"));}
  var es=new ArrayList<Map<String,Object>>();for(var e:source.required("experiments")){var r=row("code_question",Objects.requireNonNull(find("shared/code/"+e.required("key").asText())));es.add(fields("id",r.get("id"),"title",r.get("title"),"description",str(r.get("description")),"codeTemplate",str(r.get("code_template")),"solutionHint",str(r.get("solution_hint"))));}var ks=new ArrayList<Map<String,Object>>();for(int i=1;i<=3;i++){var k=row("kg_entity",kid(i));ks.add(fields("id",k.get("id"),"name",k.get("name"),"type",k.get("type"),"description",str(k.get("description")),"docId",k.get("doc_id"),"categoryId",k.get("category_id")));}return fields("version",key,"content",str(d.get("content")),"docId",d.get("id"),"docTitle",d.get("title"),"questions",qs,"exercises",es,"knowledge",ks,"notes",List.of(),"flashcardCount",cardCount);
 }
}
