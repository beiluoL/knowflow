package com.knowflow.learning.actions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.security.DocumentAccessPolicy;
import com.knowflow.service.LearningService;
import com.knowflow.service.LearningGoalService;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
/** Bounded metadata only; documents are untrusted content and never tool instructions. */
@Service @RequiredArgsConstructor
public class LearningContextService {
 private final com.knowflow.service.ResourceKnowledgeService mappings;
 private final LearningFlashcardMapper flashcards;
 private final com.knowflow.flashcards.FlashcardFsrsService fsrs;
 private final KnowledgeMasteryMapper mastery; private final KgEntityMapper knowledge; private final DocDocumentMapper docs;
 private final LearningUserPathMapper enrolled; private final LearningChapterMapper chapters; private final LearningUserChapterMapper learned;
 private final LearningMistakeMapper mistakes; private final QuizQuestionMapper questions; private final TaskMapper tasks;
 private final PersonalLearningResourceScope personal;
 private final LearningService learning; private final LearningGoalService goals; private final DocumentAccessPolicy access;
 private final com.knowflow.exam.mapper.ExamAttemptMapper exams;
 private final com.knowflow.interview.mapper.MockInterviewSessionMapper interviews;
 public PlanModels.Context read(Long user,PlanModels.Request req){
  if(user==null)throw new BusinessException(401,"未登录");ZoneId zone=zone(req.getZoneId());LocalDate date=req.getDate()==null?LocalDate.now(zone):req.getDate();
  List<PlanModels.Candidate> c=new ArrayList<>();List<String> missing=new ArrayList<>();Map<String,Object> facts=new LinkedHashMap<>();
  var settings=fsrs.settings(user);var now=Instant.now();
  var dueCards=flashcards.selectList(new LambdaQueryWrapper<LearningFlashcard>().eq(LearningFlashcard::getUserId,user).isNotNull(LearningFlashcard::getNextReviewTime).le(LearningFlashcard::getNextReviewTime,LocalDateTime.ofInstant(now,ZoneId.systemDefault())).orderByAsc(LearningFlashcard::getNextReviewTime).orderByAsc(LearningFlashcard::getId).last("LIMIT 50"));
  facts.put("dueFlashcards",dueCards.size());facts.put("dueFlashcardsBounded",dueCards.size()==50);facts.put("fsrsTimezone",settings.timezone());facts.put("fsrsDayStartHour",settings.dayStartHour());
  for(var card:dueCards.stream().limit(3).toList())c.add(new PlanModels.Candidate("flashcard:"+card.getId(),"到期主动回忆："+card.getFront().replaceAll("\\s+"," ").substring(0,Math.min(60,card.getFront().replaceAll("\\s+"," ").length())),"REVIEW","FLASHCARD",card.getId(),card.getPathId(),null,"本人原Card的next_review_time已到期（"+card.getNextReviewTime()+"）；下次时间由原FSRS四档评分决定","先揭晓答案，再提交一次真实FSRS评分；自评不是客观正确率",-10));
  var goal=goals.activeGoal(user);if(goal==null)missing.add("学习目标");else facts.put("goal",Map.of("title",goal.getTitle()));
  var mastered=mastery.selectList(new LambdaQueryWrapper<KnowledgeMastery>().eq(KnowledgeMastery::getUserId,user).orderByAsc(KnowledgeMastery::getNextReviewAt).last("LIMIT 20"));
  int evidence=0;
  for(var m:mastered){
   if((m.getLastLearnedAt()==null&&m.getLastReviewedAt()==null&&m.getLastAssessedAt()==null)||m.getConfidenceScore()==null||m.getConfidenceScore()<=0)continue;
   var k=knowledge.selectById(m.getKnowledgeId());if(!readable(k,user))continue;evidence++;
   boolean due=m.getNextReviewAt()!=null&&!m.getNextReviewAt().isAfter(LocalDateTime.ofInstant(now,ZoneId.systemDefault()));
   boolean weak="WEAK".equals(m.getLearningStatus())&&m.getConfidenceScore()>=30;
   if(due||weak)c.add(new PlanModels.Candidate("knowledge:"+k.getId(),(due?"到期复习：":"巩固：")+k.getName(),due?"REVIEW":"THEORY","KNOWLEDGE",k.getId(),null,k.getId(),due?"本人真实复习日期已到期":"本人掌握记录标记WEAK且置信度≥30","回忆后核对来源，记录不确定点",due?0:10));
  }
  if(evidence==0)missing.add("有效掌握证据（没有数据不等于薄弱）；先提交客观练习建立初始诊断");facts.put("masteryRecordsWithEvidence",evidence);
  var paths=enrolled.selectList(new LambdaQueryWrapper<LearningUserPath>().eq(LearningUserPath::getUserId,user).orderByDesc(LearningUserPath::getUpdateTime).last("LIMIT 12"));
  for(var p:paths){
   // Existing path ownership and publication checks remain authoritative.
   com.knowflow.vo.LearningPathVO path;
   try{path=learning.getPathDetail(p.getPathId(),user);}catch(BusinessException ex){if(ex.getCode()==403||ex.getCode()==404)continue;throw ex;}
   // Filter completed rows in SQL before LIMIT: long paths must not disappear after chapter twelve.
   var pending=chapters.selectList(new LambdaQueryWrapper<LearningChapter>().eq(LearningChapter::getPathId,p.getPathId())
    .apply("NOT EXISTS (SELECT 1 FROM learning_user_chapter uc WHERE uc.chapter_id=learning_chapter.id AND uc.user_id={0} AND uc.deleted=0 AND uc.complete_time IS NOT NULL)",user)
    .orderByAsc(LearningChapter::getSortOrder).orderByAsc(LearningChapter::getId).last("LIMIT 12"));
   if(!pending.isEmpty()){var ch=pending.get(0);c.add(new PlanModels.Candidate("chapter:"+ch.getId(),"学习："+path.getTitle()+" · "+ch.getTitle(),"THEORY","CHAPTER",ch.getId(),p.getPathId(),null,"本人已采用路径中的未完成章节","阅读并复述关键概念、边界与例子",30));}
  }
  if(paths.isEmpty())missing.add("已采用学习路径");
  var wrong=mistakes.selectList(new LambdaQueryWrapper<LearningMistake>().eq(LearningMistake::getUserId,user).eq(LearningMistake::getMastered,0).orderByDesc(LearningMistake::getCreateTime).last("LIMIT 5"));
  for(var w:wrong){if(w.getQuestionId()==null)continue;var q=questions.selectById(w.getQuestionId());if(!readableQuestion(q,user))continue;c.add(new PlanModels.Candidate("mistake:"+w.getId(),"复盘错题："+q.getTitle(),"REVIEW","QUIZ",q.getId(),null,knowledgeFor(q.getId(),user),"本人未掌握的真实错题，不推断未作答题的正确率","重新作答并解释错误原因",5));}
  for(var w:wrong){
   if(w.getQuestionId()==null)continue;var q=questions.selectById(w.getQuestionId());if(!readableQuestion(q,user))continue;Long kid=knowledgeFor(q.getId(),user);if(kid==null)continue;
   var recall=flashcards.selectList(new LambdaQueryWrapper<LearningFlashcard>().eq(LearningFlashcard::getUserId,user).apply("EXISTS (SELECT 1 FROM resource_knowledge_mapping m WHERE m.resource_type='FLASHCARD' AND m.resource_id=learning_flashcard.id AND m.knowledge_id={0} AND m.status='ACCEPTED' AND m.deleted=0)",kid).orderByAsc(LearningFlashcard::getId).last("LIMIT 1"));
   for(var card:recall)if(c.stream().noneMatch(x->x.key().equals("flashcard:"+card.getId())))c.add(new PlanModels.Candidate("flashcard:"+card.getId(),"主动回忆补救："+q.getTitle(),"REVIEW","FLASHCARD",card.getId(),card.getPathId(),kid,"本人真实错题 #"+w.getId()+" 与此卡共享已接受的知识映射；不推断未作答表现","揭晓后提交一次真实FSRS评分；任务完成不等于知识掌握",6));
  }
  for(String type:List.of("TRUE_FALSE","SINGLE_CHOICE","SHORT_ANSWER","CODE")){
   var pool=questions.selectList(new LambdaQueryWrapper<QuizQuestion>().eq(QuizQuestion::getQuestionType,type).eq(QuizQuestion::getStatus,1).eq(QuizQuestion::getReviewStatus,"APPROVED").orderByAsc(QuizQuestion::getId).last("LIMIT 50"));
   for(var q:pool){if(!readableQuestion(q,user)||!matchesFocus(q,req.getFocusTopics()))continue;c.add(new PlanModels.Candidate("question:"+q.getId(),("CODE".equals(type)?"代码练习：":"练习：")+q.getTitle(),"CODE".equals(type)?"CODE":"PRACTICE","QUIZ",q.getId(),null,knowledgeFor(q.getId(),user),"本人可读、已发布的真实题库资源","实际作答并核对解析；打开页面不算完成",50));break;}
  }
  if(wrong.isEmpty())missing.add("错题记录");facts.put("unmasteredMistakes",wrong.size());
  var recentExams=exams.selectList(new LambdaQueryWrapper<com.knowflow.exam.entity.ExamAttempt>().eq(com.knowflow.exam.entity.ExamAttempt::getUserId,user).orderByDesc(com.knowflow.exam.entity.ExamAttempt::getCreateTime).last("LIMIT 3"));
  if(recentExams.isEmpty())missing.add("近期考试记录");facts.put("recentExamStatuses",recentExams.stream().map(e->Objects.toString(e.getStatus(),"UNKNOWN")).toList());
  var recentInterviews=interviews.selectList(new LambdaQueryWrapper<com.knowflow.interview.entity.MockInterviewSession>().eq(com.knowflow.interview.entity.MockInterviewSession::getUserId,user).orderByDesc(com.knowflow.interview.entity.MockInterviewSession::getCreateTime).last("LIMIT 3"));
  if(recentInterviews.isEmpty())missing.add("近期模拟面试记录");facts.put("recentInterviewCount",recentInterviews.size());
  int today=Math.toIntExact(tasks.selectCount(new LambdaQueryWrapper<Task>().eq(Task::getUserId,user).le(Task::getScheduledDate,date).eq(Task::getStatus,0)));
  if(c.size()>12){var practice=c.stream().filter(x->Set.of("CODE","PRACTICE").contains(x.type())).limit(2).toList();var bounded=new ArrayList<>(c.stream().filter(x->!Set.of("CODE","PRACTICE").contains(x.type())).limit(12-practice.size()).toList());bounded.addAll(practice);c=bounded;}return new PlanModels.Context(List.copyOf(c),List.copyOf(missing),today,Map.copyOf(facts));
 }
 private Long knowledgeFor(Long resource,Long user){return mappings.resolveKnowledgeIds("QUIZ",resource).stream().filter(id->readable(knowledge.selectById(id),user)).findFirst().orElse(null);}
 private boolean matchesFocus(QuizQuestion q,List<String> focus){if(focus==null||focus.isEmpty())return true;String hay=(q.getTitle()+" "+q.getTopicCode()).toLowerCase(Locale.ROOT);return focus.stream().anyMatch(f->hay.contains(f.toLowerCase(Locale.ROOT))||f.contains("并发")&&hay.contains("java_concurrent")||f.equalsIgnoreCase("Java")&&hay.contains("java")||f.equals("算法")&&hay.contains("algo"));}
 private boolean readableQuestion(QuizQuestion q,Long user){return q!=null&&Integer.valueOf(1).equals(q.getStatus())&&"APPROVED".equals(q.getReviewStatus())&&personal.category(q.getCategoryId(),user)&&(q.getDocId()==null||personal.document(docs.selectById(q.getDocId()),user));}
 public boolean readable(KgEntity k,Long user){return k!=null&&personal.category(k.getCategoryId(),user)&&(k.getDocId()==null||personal.document(docs.selectById(k.getDocId()),user));}
 public void validateResource(PlanModels.Item item,Long user){
  if(item.resourceId()==null){if(!"TOOL".equals(item.resourceType()))throw new BusinessException(400,"缺少学习资源");return;}
  switch(item.resourceType()){
   case "FLASHCARD"->learning.getMyFlashcard(item.resourceId(),user);
   case "CHAPTER"->learning.getChapterDetail(item.resourceId(),user);
   case "KNOWLEDGE"->{if(!readable(knowledge.selectById(item.resourceId()),user))throw new BusinessException(403,"无权使用知识资源");}
   case "QUIZ"->{var q=questions.selectById(item.resourceId());if(!readableQuestion(q,user))throw new BusinessException(403,"无权使用练习资源");}
   default->throw new BusinessException(400,"不支持的资源类型");
  }
 }
 public static ZoneId zone(String value){try{return ZoneId.of(value==null?"Asia/Shanghai":value);}catch(DateTimeException ex){throw new BusinessException(400,"无效时区");}}
}
