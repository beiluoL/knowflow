package com.knowflow.interview.generation;

import com.knowflow.learning.entity.QuizQuestion;
import com.knowflow.learning.mapper.QuizQuestionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 不使用测试事务；经真实 Spring Service 代理提交和回滚隔离 H2 数据。 */
@SpringBootTest(properties={
 "knowflow.datasource.h2.url=jdbc:h2:mem:interview_tx_review;DB_CLOSE_DELAY=-1;MODE=MySQL",
 "knowflow.datasource.config-file=/tmp/interview-tx-review-no-overrides.json",
 "knowflow.seed.development-account.enabled=false",
 "knowflow.datasource.allow-runtime-switch=false"
})
@ActiveProfiles("local")
class InterviewGenerationTransactionTest {
 @Autowired InterviewRagGenerationService service;
 @Autowired JdbcTemplate jdbc;
 @SpyBean QuizQuestionMapper quizzes;

 @Test void secondWriteFailureRollsBackDraftAndNormalUpdateCommits() {
  QuizQuestion quiz = new QuizQuestion();
  quiz.setTitle("原题目"); quiz.setContent("原题目"); quiz.setAnswer("原答案");
  quiz.setCreatorId(2L); quiz.setQuestionType("SHORT_ANSWER"); quiz.setDifficulty(2);
  quizzes.insert(quiz);
  String key=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO interview_generated_question(task_id,user_id,category_id,question_id,quiz_question_id,title,category,difficulty,question_type,reference_answer,reference_points,rubric,followups,source_document_ids,source_chunks,prompt_version,canonical_key) VALUES(1,2,1,?,?, '原题目','Java',2,'SHORT_ANSWER','原答案','[]','{}','[]','[]','[]','test',?)",key,quiz.getId(),key);
  long id=jdbc.queryForObject("SELECT id FROM interview_generated_question WHERE question_id=?",Long.class,key);
  var request=new InterviewRagGenerationService.UpdateRequest("新题目"+key,"Java",2,"新答案",List.of("有效考察点"),Map.of("level1","基础评分"),List.of("如何验证？"));
  doThrow(new IllegalStateException("isolated second-write failure")).when(quizzes).updateById(any(QuizQuestion.class));
  try {
   assertThrows(IllegalStateException.class,()->service.update(2L,id,request));
   assertEquals("原题目",jdbc.queryForObject("SELECT title FROM interview_generated_question WHERE id=?",String.class,id));
   assertEquals("原题目",quizzes.selectById(quiz.getId()).getTitle());
  } finally { reset(quizzes); }
  service.update(2L,id,request);
  assertEquals(request.title(),jdbc.queryForObject("SELECT title FROM interview_generated_question WHERE id=?",String.class,id));
  assertEquals(request.title(),quizzes.selectById(quiz.getId()).getTitle());
  assertThrows(com.knowflow.exception.BusinessException.class,()->service.update(3L,id,request));
  String canonical=jdbc.queryForObject("SELECT canonical_key FROM interview_generated_question WHERE id=?",String.class,id);
  service.remove(2L,id);
  assertNotEquals(canonical,jdbc.queryForObject("SELECT canonical_key FROM interview_generated_question WHERE id=?",String.class,id));
  assertThrows(com.knowflow.exception.BusinessException.class,()->service.update(2L,id,request));
 }
}
