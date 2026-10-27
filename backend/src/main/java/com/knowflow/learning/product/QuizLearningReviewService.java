package com.knowflow.learning.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.*;
import com.knowflow.entity.*;import com.knowflow.mapper.*;
import com.knowflow.exception.BusinessException;
import com.knowflow.learning.actions.*;
import com.knowflow.service.LearningService;
import com.knowflow.dto.FlashcardSaveDTO;
import com.knowflow.flashcards.FlashcardScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Read projection over original answers/mistakes/mappings/events. No separate learning state. */
@Service @RequiredArgsConstructor
public class QuizLearningReviewService {
 private final org.springframework.jdbc.core.JdbcTemplate jdbc;
 private final QuizAnswerRecordMapper answers;private final QuizQuestionMapper questions;private final DocDocumentMapper docs;private final KgEntityMapper knowledge;
 private final ResourceKnowledgeMappingMapper mappings;private final LearningFlashcardMapper cards;private final LearningEventMapper events;private final LearningMistakeMapper mistakes;
 private final LearningChapterMapper chapters;private final LearningPathMapper paths;
 private final PersonalLearningResourceScope personal;private final ObjectMapper json;private final LearningService learning;private final LearningWriteGuard guard;
 public List<Long> acceptedKnowledge(String type,long id,long actor){
  return mappings.selectList(new LambdaQueryWrapper<ResourceKnowledgeMapping>().eq(ResourceKnowledgeMapping::getResourceType,type).eq(ResourceKnowledgeMapping::getResourceId,id).eq(ResourceKnowledgeMapping::getStatus,"ACCEPTED").in(ResourceKnowledgeMapping::getSource,List.of("MANUAL","IMPORT"))).stream().map(ResourceKnowledgeMapping::getKnowledgeId).distinct().filter(kid->{var k=knowledge.selectById(kid);return k!=null&&personal.category(k.getCategoryId(),actor)&&(k.getDocId()==null||personal.document(docs.selectById(k.getDocId()),actor));}).sorted().toList();
 }
 private boolean available(QuizQuestion q,long uid){return q!=null&&Integer.valueOf(1).equals(q.getStatus())&&"APPROVED".equals(q.getReviewStatus())&&personal.category(q.getCategoryId(),uid)&&(q.getDocId()==null||personal.document(docs.selectById(q.getDocId()),uid));}
 public QuizAnswerRecord owned(long id,long uid){var a=answers.selectById(id);if(a==null)throw new BusinessException(404,"作答记录不存在");if(!Objects.equals(a.getUserId(),uid))throw new BusinessException(403,"无权查看私人作答");return a;}
 private JsonNode snapshot(QuizAnswerRecord a){if(a.getQuestionSnapshot()==null)return null;try{return json.readTree(a.getQuestionSnapshot());}catch(Exception e){throw new IllegalStateException("Invalid persisted answer snapshot",e);}}
 public void validateBaseline(Long id,long qid,long uid,String expectedHash){if(id==null)return;var a=owned(id,uid);if(!Objects.equals(a.getQuestionId(),qid)||!Integer.valueOf(0).equals(a.getIsCorrect())||snapshot(a)==null)throw new BusinessException(409,"请选择有原始快照的本人错题作答进行复测");if(expectedHash==null)throw new BusinessException(400,"复测必须携带当前题目版本摘要");if(!available(questions.selectById(qid),uid))throw new BusinessException(403,"题目当前不可用于个人复测");}
 private long answerEvent(long uid,long recordId,List<LearningEvent> rows){for(var e:rows)if("QUESTION_ANSWERED".equals(e.getEventType())&&"QUIZ".equals(e.getResourceType())&&meta(e).path("answerRecordId").asLong()==recordId)return e.getId();return 0;}
 private JsonNode meta(LearningEvent e){try{return json.readTree(e.getMetadata()==null?"{}":e.getMetadata());}catch(Exception error){throw new IllegalStateException("Invalid persisted learning event",error);}}
 private Map<String,Object> attempt(QuizAnswerRecord a,long event){var m=new LinkedHashMap<String,Object>();m.put("answerRecordId",a.getId());m.put("questionId",a.getQuestionId());m.put("userAnswer",a.getUserAnswer());m.put("correct",Integer.valueOf(1).equals(a.getIsCorrect()));m.put("score",a.getScore());m.put("answeredAt",a.getCreateTime());m.put("eventId",event==0?null:event);var s=snapshot(a);m.put("snapshot",s);m.put("snapshotState",s==null?"LEGACY_UNAVAILABLE":"FROZEN");return m;}
 @Transactional(readOnly=true) public Map<String,Object> report(long uid,long qid){
  var q=questions.selectById(qid);boolean usable=available(q,uid);var history=new ArrayList<>(answers.selectList(new LambdaQueryWrapper<QuizAnswerRecord>().eq(QuizAnswerRecord::getUserId,uid).eq(QuizAnswerRecord::getQuestionId,qid).orderByDesc(QuizAnswerRecord::getId).last("LIMIT 50")));Collections.reverse(history);
  var stream=events.selectList(new LambdaQueryWrapper<LearningEvent>().eq(LearningEvent::getUserId,uid).in(LearningEvent::getEventType,List.of("QUESTION_ANSWERED","FLASHCARD_REVIEWED")).orderByAsc(LearningEvent::getId));
  QuizAnswerRecord baseline=history.stream().filter(a->Integer.valueOf(0).equals(a.getIsCorrect())&&snapshot(a)!=null).findFirst().orElse(null);
  QuizAnswerRecord latest=history.isEmpty()?null:history.get(history.size()-1);
  if(latest!=null&&snapshot(latest)!=null&&snapshot(latest).has("baselineAnswerRecordId"))baseline=owned(snapshot(latest).path("baselineAnswerRecordId").asLong(),uid);
  var kids=usable?acceptedKnowledge("QUIZ",qid,uid):List.<Long>of();var refs=new ArrayList<Map<String,Object>>();
  if(!kids.isEmpty())for(var c:cards.selectList(new LambdaQueryWrapper<LearningFlashcard>().eq(LearningFlashcard::getUserId,uid).orderByAsc(LearningFlashcard::getId))){if(!personal.category(c.getCategoryId(),uid)||(c.getDocId()!=null&&!personal.document(docs.selectById(c.getDocId()),uid)))continue;var common=acceptedKnowledge("FLASHCARD",c.getId(),uid).stream().filter(kids::contains).toList();if(common.isEmpty())continue;refs.add(Map.of("cardId",c.getId(),"front",c.getFront(),"knowledgeId",common.get(0),"mappingStatus","ACCEPTED"));if(refs.size()==20)break;}
  long from=baseline==null?0:answerEvent(uid,baseline.getId(),stream),to=latest==null||baseline==null||Objects.equals(latest.getId(),baseline.getId())?Long.MAX_VALUE:answerEvent(uid,latest.getId(),stream);
  Set<Long> historicalKids=new HashSet<>();if(baseline!=null){var original=snapshot(baseline);if(original!=null)original.path("knowledgeIds").forEach(n->historicalKids.add(n.asLong()));}
  var reviews=new ArrayList<Map<String,Object>>();
  if(from>0&&to>0)for(var e:stream){if(e.getId()<=from||e.getId()>=to||!"FLASHCARD_REVIEWED".equals(e.getEventType())||!"FLASHCARD".equals(e.getResourceType()))continue;var m=meta(e);if(e.getResourceId()==null||!FlashcardScheduler.VERSION.equals(m.path("algorithm").asText())||!historicalKids.contains(m.path("knowledgeId").asLong()))continue;reviews.add(Map.of("eventId",e.getId(),"cardId",e.getResourceId(),"knowledgeId",m.path("knowledgeId").asLong(),"rating",m.path("rating").asInt(),"algorithm",m.path("algorithm").asText(),"reviewedAt",Objects.toString(e.getCreateTime()),"dueAt",m.path("dueAt").asText()));}
  var comparison=new LinkedHashMap<String,Object>();boolean paired=baseline!=null&&latest!=null&&!Objects.equals(baseline.getId(),latest.getId())&&snapshot(latest)!=null&&snapshot(latest).path("baselineAnswerRecordId").asLong()==baseline.getId();boolean same=paired&&Objects.equals(snapshot(baseline).path("contentHash"),snapshot(latest).path("contentHash"))&&Objects.equals(snapshot(baseline).path("gradingRule"),snapshot(latest).path("gradingRule"))&&Objects.equals(snapshot(baseline).path("conditions"),snapshot(latest).path("conditions"));
  comparison.put("paired",paired);comparison.put("comparable",same);comparison.put("reviewConfirmed",!reviews.isEmpty());comparison.put("note",same?"同题、同版本摘要、同规则、均为无时限且可查看答案的练习；仅比较本次作答，不代表能力提升。":"本次测验与上次条件不完全一致，或尚无成对快照；以下仅供参考。");if(paired){comparison.put("firstAnswerRecordId",baseline.getId());comparison.put("secondAnswerRecordId",latest.getId());comparison.put("firstCorrect",Integer.valueOf(1).equals(baseline.getIsCorrect()));comparison.put("secondCorrect",Integer.valueOf(1).equals(latest.getIsCorrect()));}
  Long chapterId=null;if(usable&&q.getDocId()!=null)for(var ch:chapters.selectList(null)){var path=paths.selectById(ch.getPathId());if(path==null||!Integer.valueOf(1).equals(path.getStatus())||!(path.getOwnerUserId()==null||path.getOwnerUserId()==0||Objects.equals(path.getOwnerUserId(),uid)))continue;if(ch.getDocIds()!=null&&Arrays.stream(ch.getDocIds().split(",")).map(String::trim).anyMatch(v->v.equals(q.getDocId().toString()))){chapterId=ch.getId();break;}}
  var result=new LinkedHashMap<String,Object>();result.put("questionId",qid);result.put("practiceAvailable",usable);result.put("chapterId",chapterId);result.put("baselineAnswerRecordId",baseline==null?null:baseline.getId());result.put("attempts",history.stream().map(a->attempt(a,answerEvent(uid,a.getId(),stream))).toList());result.put("cards",refs);result.put("reviews",reviews);result.put("comparison",comparison);result.put("mistakes",mistakes.selectList(new LambdaQueryWrapper<LearningMistake>().eq(LearningMistake::getUserId,uid).eq(LearningMistake::getQuestionId,qid).eq(LearningMistake::getSource,"QUIZ")).stream().map(a->Map.of("mistakeId",a.getId(),"mastered",a.getMastered()==null?0:a.getMastered())).toList());result.put("knowledgeIds",kids);result.put("historyLimit",50);return result;
 }
 @Transactional public Map<String,Object> createCard(long uid,long answerId,String front,String back){
  guard.lock(uid);var a=owned(answerId,uid);var q=questions.selectById(a.getQuestionId());
  if(!Integer.valueOf(0).equals(a.getIsCorrect())||snapshot(a)==null||!available(q,uid))throw new BusinessException(409,"当前错题来源不可用，不能创建关联草稿");
  if(!Objects.equals(snapshot(a).path("contentHash").asText(),QuizAnswerSnapshot.hash(json,q)))throw new BusinessException(409,"题目来源已变更，请先核对旧快照与当前来源");
  if(front==null||front.isBlank()||back==null||back.isBlank())throw new BusinessException(400,"卡片正反面不能为空");
  front=front.trim();back=back.trim();
  var query=new LambdaQueryWrapper<LearningFlashcard>().eq(LearningFlashcard::getUserId,uid).eq(LearningFlashcard::getFront,front).eq(LearningFlashcard::getBack,back);
  if(q.getDocId()==null)query.isNull(LearningFlashcard::getDocId);else query.eq(LearningFlashcard::getDocId,q.getDocId());
  if(q.getCategoryId()==null)query.isNull(LearningFlashcard::getCategoryId);else query.eq(LearningFlashcard::getCategoryId,q.getCategoryId());
  var existing=cards.selectList(query.orderByAsc(LearningFlashcard::getId).last("LIMIT 1"));long cardId;
  if(!existing.isEmpty())cardId=existing.get(0).getId();else{var input=new FlashcardSaveDTO();input.setFront(front);input.setBack(back);input.setDocId(q.getDocId());input.setCategoryId(q.getCategoryId());cardId=learning.createMyFlashcard(uid,input).getId();}
  var kids=acceptedKnowledge("QUIZ",q.getId(),uid);var states=new ArrayList<String>();
  for(long kid:kids){var rows=jdbc.queryForList("SELECT status,deleted FROM resource_knowledge_mapping WHERE resource_type='FLASHCARD' AND resource_id=? AND knowledge_id=?",cardId,kid);
   if(!rows.isEmpty()){if(!Integer.valueOf(0).equals(((Number)rows.get(0).get("deleted")).intValue()))throw new BusinessException(409,"已有映射已删除，请经原审核流程处理");states.add(Objects.toString(rows.get(0).get("status")));continue;}
   var m=new ResourceKnowledgeMapping();m.setResourceType("FLASHCARD");m.setResourceId(cardId);m.setKnowledgeId(kid);m.setSource("MANUAL");m.setStatus("PENDING");mappings.insert(m);states.add("PENDING");
  }
  String state=states.isEmpty()?"UNMAPPED":states.stream().allMatch("ACCEPTED"::equals)?"ACCEPTED":states.contains("PENDING")?"PENDING":"REJECTED";
  return Map.of("cardId",cardId,"mappingStatus",state,"knowledgeIds",kids);
 }
}
