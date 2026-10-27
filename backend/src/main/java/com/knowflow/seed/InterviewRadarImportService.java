package com.knowflow.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Private source import; never emits learning events or imports historical scores. */
@Service @RequiredArgsConstructor
public class InterviewRadarImportService {
 public static final String KEY="interview-radar-private-v1/";
 private final JdbcTemplate jdbc;
 private final SysUserMapper users;
 private final LearningPathMapper paths;
 private final LearningChapterMapper chapters;
 private final DocCategoryMapper categories;
 private final DocDocumentMapper documents;
 private final QuizQuestionMapper questions;
 private final com.knowflow.interview.radar.RadarSourceAssets assets;
 private record Unit(String identity,String title,String body,String answer) {}
 private void actor(Long actor){if(actor==null||users.selectById(actor)==null)throw new BusinessException(401,"请先登录本人账号");}
 private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private Long id(Long actor,String key){var found=jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=?",KEY+actor,key);return found.isEmpty()?null:((Number)found.get(0).get("resource_id")).longValue();}
 private void index(Long actor,String key,String kind,Long id){jdbc.update("INSERT INTO interview_seed_resource(seed_key,resource_key,resource_type,resource_id) VALUES(?,?,?,?)",KEY+actor,key,kind,id);}
 private List<Unit> units(JsonNode input){
  if(input==null||input.path("schemaVersion").asInt()!=1||!input.path("files").isArray()||!input.path("mergedQuestions").isArray())throw new BusinessException(400,"需要有效雷达审计清单");
  if(input.path("files").size()>1000||input.path("mergedQuestions").size()>1000)throw new BusinessException(400,"单批最多1000个来源/题目");
  Map<String,JsonNode> files=new HashMap<>();long chars=0;
  for(var f:input.path("files")){
   if(!f.path("kind").asText().equals("markdown"))continue;
   String path=f.path("sourceFile").asText(),raw=f.path("rawMarkdown").asText();chars+=raw.length();
   if(path.isBlank()||path.startsWith("/")||Arrays.asList(path.split("/")).contains("..")||path.contains("\\")||!hash(raw).equals(f.path("sourceHash").asText())||files.put(path,f)!=null)throw new BusinessException(400,"来源路径/哈希无效或重复");
  }
  if(chars>12_000_000)throw new BusinessException(400,"来源正文过大，请拆批");
  List<Unit> result=new ArrayList<>();Set<String> identities=new HashSet<>();
  for(var q:input.path("mergedQuestions")){
   String identity=q.path("identity").asText();
   if(identity.length()>180||!identity.equals(q.path("sourceNamespace").asText()+":"+q.path("sourceQuestionId").asText())||!q.path("sourceQuestionId").asText().matches("(?:ORIG-)?Q[0-9]{3}(?:-[0-9]{2})?")||!identities.add(identity))throw new BusinessException(400,"题目身份无效或重复");
   StringBuilder body=new StringBuilder("# "+q.path("sourceQuestionId").asText()+" · 来源教材\n\n来源中的个人成绩与训练状态仅是历史原文，不计入当前账号学习证据。\n\n");
   String title=null;Set<String> seen=new HashSet<>();
   for(var source:q.path("sources")){
    String path=source.path("sourceFile").asText();var f=files.get(path);
    if(f==null||!source.path("sourceHash").asText().equals(f.path("sourceHash").asText())||!seen.add(path))throw new BusinessException(400,"来源映射不完整或重复");
    if(title==null)title=f.path("title").asText(q.path("sourceQuestionId").asText());
    body.append("## 来源：").append(path).append("\n\nSHA-256：").append(source.path("sourceHash").asText()).append("\n\n").append(f.path("rawMarkdown").asText()).append("\n\n");
   }
   if(title==null)throw new BusinessException(400,"题目缺少来源");
   String answer=q.path("answerCandidates").isArray()&&q.path("answerCandidates").size()==1?q.path("answerCandidates").get(0).asText():null;
   result.add(new Unit(identity,title.substring(0,Math.min(title.length(),190)),body.toString(),answer));
  }
  if(result.isEmpty())throw new BusinessException(400,"没有可导入来源");return result;
 }
 private List<String> conflicts(Long actor,List<Unit> units){
  List<String> out=new ArrayList<>();Long pid=id(actor,"path/radar"),cid=id(actor,"category/radar");
  if(pid!=null){var p=paths.selectById(pid);if(p==null||!actor.equals(p.getOwnerUserId()))out.add("路径已删除或归属冲突");}
  if(cid!=null){var c=categories.selectById(cid);if(c==null||!actor.equals(c.getOwnerId()))out.add("知识库已删除或归属冲突");}
  for(var u:units){
   String k=hash(u.identity());Long did=id(actor,"source/document/"+k),ch=id(actor,"chapter/"+k);
   if(did==null&&ch==null)continue;
   var doc=did==null?null:documents.selectById(did);var chapter=ch==null?null:chapters.selectById(ch);
   if(doc==null||chapter==null||!Objects.equals(doc.getCategoryId(),cid)||!Objects.equals(chapter.getPathId(),pid)||!hash(Objects.toString(doc.getContent(),"")).equals(doc.getContentHash())||!Objects.equals(doc.getContent(),chapter.getContent())){out.add(u.identity()+"：删除、手工改动或索引冲突");continue;}
   Long qid=id(actor,"question/"+k);if(qid!=null){var q=questions.selectById(qid);if(q==null||!actor.equals(q.getCreatorId())||!Objects.equals(q.getCategoryId(),cid)||!Objects.toString(q.getAnswer(),"").equals(Objects.toString(u.answer(),"")))out.add(u.identity()+"：题库答案变更需审核");}
  }
  return out;
 }
 private List<com.fasterxml.jackson.databind.JsonNode> references(com.fasterxml.jackson.databind.JsonNode input){
  Set<String> used=new HashSet<>();for(var q:input.path("mergedQuestions"))for(var source:q.path("sources"))used.add(source.path("sourceFile").asText());
  List<com.fasterxml.jackson.databind.JsonNode> result=new ArrayList<>();for(var file:input.path("files"))if("markdown".equals(file.path("kind").asText())&&!used.contains(file.path("sourceFile").asText()))result.add(file);return result;
 }
 private int storeReferences(Long actor,Long category,com.fasterxml.jackson.databind.JsonNode input){
  int created=0;for(var file:references(input)){
   String source=file.path("sourceFile").asText(),key="reference/"+hash(source);
   String body="此文档为原始参考索引；表中历史成绩与进度不代表当前账号状态。\n\n来源："+source+"\nSHA-256："+file.path("sourceHash").asText()+"\n\n"+file.path("rawMarkdown").asText();
   Long existing=id(actor,key);
   if(existing==null){var doc=new DocDocument();String title="原始参考 · "+file.path("title").asText(source);doc.setTitle(title.substring(0,Math.min(190,title.length())));doc.setContent(body);doc.setContentHash(hash(body));doc.setSourcePath(source);doc.setCategoryId(category);doc.setCategoryPath(category.toString());doc.setStatus(1);doc.setTags("interview-radar-private,reference-only");doc.setWordCount(body.length());documents.insert(doc);index(actor,key,"DOC",doc.getId());created++;}
   else{var doc=documents.selectById(existing);if(doc==null||!category.equals(doc.getCategoryId())||!hash(Objects.toString(doc.getContent(),"")).equals(doc.getContentHash()))throw new BusinessException(409,"参考索引已删除、修改或归属冲突，未覆盖");if(!hash(body).equals(doc.getContentHash())){doc.setContent(body);doc.setContentHash(hash(body));doc.setWordCount(body.length());documents.updateById(doc);}}
  }return created;
 }
 @Transactional(readOnly=true)
 public Map<String,Object> preview(Long actor,JsonNode input){actor(actor);var units=units(input);var out=new LinkedHashMap<String,Object>();out.put("dryRun",true);out.put("imageAssets",assets.preview(input));out.put("referenceDocuments",references(input).size());out.put("chapters",units.size());out.put("questionCandidates",units.stream().filter(u->u.answer()!=null).count());out.put("conflicts",conflicts(actor,units));out.put("pathId",id(actor,"path/radar"));out.put("contentStatus","PRIVATE_SOURCE_AND_PENDING_QUESTIONS");return out;}
 @Transactional(rollbackFor=Exception.class)
 public Map<String,Object> apply(Long actor,JsonNode input){
  actor(actor);jdbc.queryForList("SELECT id FROM sys_user WHERE id=? AND deleted=0 FOR UPDATE",actor);actor(actor);
  var units=units(input);assets.preview(input);var conflicts=conflicts(actor,units);if(!conflicts.isEmpty())throw new BusinessException(409,String.join("；",conflicts));
  Long category=id(actor,"category/radar"),path=id(actor,"path/radar");
  if(category==null){var c=new DocCategory();c.setName("Java + AI 面试雷达 · 本人来源教材");c.setCode("radar-private-"+actor);c.setOwnerId(actor);c.setParentId(0L);c.setStatus(1);c.setDocCount(0);categories.insert(c);category=c.getId();index(actor,"category/radar","CATEGORY",category);}
  if(path==null){var p=new LearningPath();p.setTitle("Java + AI 工程师｜面试雷达");p.setDescription("同号合并并保留全部原文；历史成绩不作为当前学习证据。题库保持待审，未连接严格评分。时长待评估。");p.setOwnerUserId(actor);p.setLevel("进阶");p.setStatus(1);p.setChapterCount(0);p.setTotalDuration(0);p.setEnrolledCount(0);p.setSortOrder(1000);paths.insert(p);path=p.getId();index(actor,"path/radar","PATH",path);}
  int created=0,updated=0,questionCount=0,order=0;
  for(var u:units){
   String k=hash(u.identity());Long did=id(actor,"source/document/"+k),chid=id(actor,"chapter/"+k);
   if(did==null){var d=new DocDocument();d.setTitle(u.title());d.setContent(u.body());d.setContentHash(hash(u.body()));d.setSourcePath(u.identity());d.setCategoryId(category);d.setCategoryPath(category.toString());d.setStatus(0);d.setWordCount(u.body().length());d.setTags("interview-radar-private,source-material");documents.insert(d);did=d.getId();index(actor,"source/document/"+k,"DOC",did);
    var ch=new LearningChapter();ch.setPathId(path);ch.setTitle(u.title());ch.setContent(u.body());ch.setDocIds(did.toString());ch.setFlashcardIds("");ch.setPrerequisiteChapterIds("");ch.setSortOrder(++order);ch.setDuration(0);chapters.insert(ch);chid=ch.getId();index(actor,"chapter/"+k,"CHAPTER",chid);created++;
   }else{order++;var d=documents.selectById(did);if(!hash(u.body()).equals(d.getContentHash())){d.setContent(u.body());d.setContentHash(hash(u.body()));d.setWordCount(u.body().length());documents.updateById(d);var ch=chapters.selectById(chid);ch.setContent(u.body());chapters.updateById(ch);updated++;}}
   if(id(actor,"question/"+k)==null){var q=new QuizQuestion();q.setTitle(u.title());q.setContent(u.title());q.setQuestionType(QuizQuestion.TYPE_SHORT_ANSWER);q.setAnswer(Objects.toString(u.answer(),""));q.setExplanation((u.answer()==null?"未可靠提取标准答案，完整原文保留待审；不得评分。来源：":"原始参考答案候选；题干/评分点待核查，不用于自动评分。来源：")+u.identity());q.setCategoryId(category);q.setDocId(did);q.setCanonicalKey("radar/"+actor+"/"+k);q.setSource(QuizQuestion.SOURCE_MANUAL);q.setCreatorId(actor);q.setStatus(0);q.setReviewStatus(QuizQuestion.REVIEW_PENDING);q.setVersion(1);q.setSortOrder(order);questions.insert(q);index(actor,"question/"+k,"QUIZ",q.getId());questionCount++;}
  }
  var p=paths.selectById(path);p.setChapterCount(Math.toIntExact(chapters.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningChapter>().eq(LearningChapter::getPathId,path))));paths.updateById(p);
  int images=assets.store(actor,category,input);
  int referenceCount=storeReferences(actor,category,input);
  jdbc.update("UPDATE doc_category SET doc_count=(SELECT COUNT(*) FROM doc_document WHERE category_id=? AND deleted=0) WHERE id=? AND owner_id=?",category,category,actor);
  return Map.of("referenceDocumentsCreated",referenceCount,"imagesCreated",images,"pathId",path,"chaptersCreated",created,"chaptersUpdated",updated,"pendingQuestionsCreated",questionCount,"scoresImported",0,"private",true);
 }
}
