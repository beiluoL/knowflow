package com.knowflow.catalog;
import com.knowflow.entity.QuizQuestion;
import com.knowflow.mapper.QuizQuestionMapper;
import com.knowflow.common.SecurityUtils;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Set;
import java.util.Objects;
@Service @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class QuizQuestionAdminService {
 private final org.mybatis.spring.SqlSessionTemplate sessions;
 private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
 private final QuizQuestionMapper mapper;
 private final QuestionMutationAudit audit;
 private static final Set<String> TYPES=Set.of("SINGLE_CHOICE","MULTIPLE_CHOICE","TRUE_FALSE","FILL_BLANK","SHORT_ANSWER");
 private void validate(QuizQuestion q){if(q.getTitle()==null||q.getTitle().isBlank()||q.getContent()==null||q.getContent().isBlank())throw new BusinessException(400,"标题与题干不能为空");if(!TYPES.contains(q.getQuestionType()))throw new BusinessException(400,"请使用对应专业题型编辑器");if(q.getDifficulty()==null||q.getDifficulty()<1||q.getDifficulty()>3)throw new BusinessException(400,"难度必须为1–3");if(q.getStatus()!=null&&q.getStatus()!=0&&q.getStatus()!=1)throw new BusinessException(400,"发布状态无效");}
 private QuizQuestion require(Long id) {
  // A locking read must return the full current row, even in MySQL REPEATABLE READ.
  sessions.clearCache();
  var q=mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QuizQuestion>()
    .eq(QuizQuestion::getId,id).last("FOR UPDATE"));
  if(q==null)throw new BusinessException(404,"题目不存在");
  if(QuizQuestion.TYPE_CODE.equals(q.getQuestionType()))throw new BusinessException(400,"算法题请使用专业编辑器");
  return q;
 }
 @Transactional public QuizQuestion create(QuizQuestion q){if(q.getQuestionType()==null)q.setQuestionType("SINGLE_CHOICE");if(q.getDifficulty()==null)q.setDifficulty(2);validate(q);q.setId(null);q.setDeleted(0);q.setCreateTime(null);q.setUpdateTime(null);q.setCreatorId(SecurityUtils.getCurrentUserId());q.setVersion(1);q.setCanonicalKey(null);q.setSource("AI".equals(q.getSource())?"AI":"MANUAL");q.setReviewStatus("AI".equals(q.getSource())?"PENDING_REVIEW":null);q.setStatus("AI".equals(q.getSource())?0:(q.getStatus()==null?0:q.getStatus()));mapper.insert(q);audit.record("QUIZ",q.getId(),"CREATE",null,"status="+q.getStatus()+",review="+q.getReviewStatus());return q;}
 @Transactional public QuizQuestion update(Long id,QuizQuestion incoming){var old=require(id);String before="version="+old.getVersion()+",status="+old.getStatus();if(incoming.getQuestionType()==null)incoming.setQuestionType(old.getQuestionType());if(incoming.getDifficulty()==null)incoming.setDifficulty(old.getDifficulty());validate(incoming);incoming.setId(id);incoming.setSource(old.getSource());incoming.setCreatorId(old.getCreatorId());incoming.setCanonicalKey(old.getCanonicalKey());incoming.setCreateTime(old.getCreateTime());incoming.setDeleted(old.getDeleted());incoming.setVersion((old.getVersion()==null?0:old.getVersion())+1);incoming.setReviewStatus(("AI".equals(old.getSource())||old.getReviewStatus()!=null)?"PENDING_REVIEW":null);if("AI".equals(old.getSource())||old.getReviewStatus()!=null)incoming.setStatus(0);mapper.updateById(incoming);audit.record("QUIZ",id,"EDIT",before,"version="+incoming.getVersion()+",status="+incoming.getStatus());return mapper.selectById(id);}
 @Transactional public void publish(Long id,boolean published){var q=require(id);if(published&&q.getReviewStatus()!=null&&!"APPROVED".equals(q.getReviewStatus()))throw new BusinessException(409,"题目尚未审核通过");if(published&&"AI".equals(q.getSource())&&!"APPROVED".equals(q.getReviewStatus()))throw new BusinessException(409,"AI候选题必须先人工审核");Integer before=q.getStatus();q.setStatus(published?1:0);mapper.updateById(q);audit.record("QUIZ",id,published?"PUBLISH":"UNPUBLISH",String.valueOf(before),String.valueOf(q.getStatus()));}
 @Transactional public void review(Long id,String decision,String comment,Integer expectedVersion,String expectedToken){if(!Set.of("APPROVED","REJECTED").contains(decision==null?"":decision))throw new BusinessException(400,"审核决定必须为APPROVED或REJECTED");var q=require(id);if(!Objects.equals(q.getVersion(),expectedVersion)||!Objects.equals(QuestionEditToken.of(objectMapper,q),expectedToken))throw new BusinessException(409,"审核内容已变更，请重新查看题目后审核");String before=q.getReviewStatus();q.setReviewStatus(decision);q.setStatus(0);mapper.updateById(q);audit.record("QUIZ",id,"REVIEW",before,decision+":"+(comment==null?"":comment.substring(0,Math.min(comment.length(),400))));}
 @Transactional public void delete(Long id){var q=require(id);mapper.deleteById(id);audit.record("QUIZ",id,"DELETE","version="+q.getVersion(),"deleted=1");}
}
