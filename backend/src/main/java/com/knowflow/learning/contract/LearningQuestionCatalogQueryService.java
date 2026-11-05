package com.knowflow.learning.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.learning.mapper.LearningScenarioMapper;
import com.knowflow.learning.mapper.QuizQuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** 从学习题库读取既有管理端详情。 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
class LearningQuestionCatalogQueryService implements LearningQuestionCatalogQueryPort {

    private final QuizQuestionMapper quiz;
    private final LearningScenarioMapper scenarios;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode findQuiz(long questionId) {
        var question = quiz.selectById(questionId);
        return question == null ? null : objectMapper.valueToTree(question);
    }

    @Override
    public JsonNode findScenario(long scenarioId) {
        var scenario = scenarios.selectById(scenarioId);
        return scenario == null ? null : objectMapper.valueToTree(scenario);
    }
}
