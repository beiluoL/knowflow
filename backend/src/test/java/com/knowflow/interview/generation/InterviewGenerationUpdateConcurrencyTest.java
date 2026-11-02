package com.knowflow.interview.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.capability.AiCapabilityProperties;
import com.knowflow.exception.BusinessException;
import com.knowflow.interview.skill.InterviewSkillRegistry;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.QuizQuestionMapper;
import com.knowflow.security.DocumentAccessPolicy;
import com.knowflow.service.AiService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 条件更新失败时不得继续修改正式题库或返回成功。 */
class InterviewGenerationUpdateConcurrencyTest {
    @Test
    void concurrentStateChangeStopsQuizWrite() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        QuizQuestionMapper questions = mock(QuizQuestionMapper.class);
        when(jdbc.queryForList(contains("WHERE id=? AND user_id=? AND deleted=0"), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", 1L, "status", "DRAFT", "difficulty", 2, "quiz_question_id", 10L)));
        when(jdbc.queryForList(contains("canonical_key=?"), any(Object[].class))).thenReturn(List.of());
        var service = new InterviewRagGenerationService(jdbc, new ObjectMapper(), mock(AiService.class),
                new AiCapabilityProperties(), mock(DocumentAccessPolicy.class), mock(DocDocumentMapper.class),
                questions, new InterviewSkillRegistry(), mock(PlatformTransactionManager.class));
        var request = new InterviewRagGenerationService.UpdateRequest("并发更新测试", "Java", 2, "有效参考答案",
                List.of("有效考察点"), Map.of("level1", "基础评分"), List.of("如何验证？"));
        try {
            assertThrows(BusinessException.class, () -> service.update(2L, 1L, request));
            verifyNoInteractions(questions);
        } finally {
            service.close();
        }
    }
}
