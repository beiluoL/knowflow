package com.knowflow.interview.v2;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 只用已校验评价派生报告；无证据不补分、不生成假章节。 */
class InterviewReportAggregatorTest {

    private Map<String, Object> evaluation(List<String> gaps, List<String> strengths, String suggestion,
                                           Map<String, Integer> dimensions) {
        Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("gaps", gaps);
        value.put("strengths", strengths);
        value.put("suggestion", suggestion);
        value.put("dimensions", dimensions);
        return value;
    }

    @Test
    void prioritiesComeFromRepeatedGapsAndLowDimensions() {
        Map<String, Object> turn1 = new java.util.LinkedHashMap<>(Map.of("questionId", "1"));
        turn1.put("evaluation", evaluation(List.of("线程池拒绝策略"), List.of("说明了容量检查"), "补充拒绝策略验证",
                Map.of("TECHNICAL_CORRECTNESS", 45, "KNOWLEDGE_COVERAGE", 60, "ENGINEERING_PRACTICE", 72)));
        Map<String, Object> turn2 = new java.util.LinkedHashMap<>(Map.of("questionId", "2"));
        turn2.put("evaluation", evaluation(List.of("线程池拒绝策略"), List.of(), "给出可复现的压测方案",
                Map.of("TECHNICAL_CORRECTNESS", 55, "KNOWLEDGE_COVERAGE", 58, "ENGINEERING_PRACTICE", 80)));

        InterviewReportAggregator.Result result = InterviewReportAggregator.aggregate(
                List.of(turn1, turn2),
                new java.util.LinkedHashMap<>(Map.of("TECHNICAL_CORRECTNESS", 45.0, "KNOWLEDGE_COVERAGE", 59.0, "ENGINEERING_PRACTICE", 76.0)));

        assertTrue(result.weaknesses().stream().anyMatch(w -> "P0".equals(w.get("priority"))));
        Map<String, Object> gap = result.weaknesses().stream().filter(w -> "KNOWLEDGE_GAP".equals(w.get("type"))).findFirst().orElseThrow();
        assertEquals("P1", gap.get("priority"));
        assertEquals(2, gap.get("occurrences"));
        assertTrue(result.missedKnowledgePoints().contains("线程池拒绝策略"));
        assertEquals(List.of("说明了容量检查"), result.strengths());
        assertTrue(result.improvements().contains("补充拒绝策略验证"));
        assertEquals(List.of(1L, 2L), result.recommendedPracticeQuestionIds());
        assertTrue(result.weaknesses().size() <= 12);
    }

    @Test
    void noEvidenceMeansNoFabricatedWeaknessOrPractice() {
        InterviewReportAggregator.Result empty = InterviewReportAggregator.aggregate(List.of(), Map.of());
        assertTrue(empty.weaknesses().isEmpty());
        assertTrue(empty.strengths().isEmpty());
        assertTrue(empty.improvements().isEmpty());
        assertTrue(empty.missedKnowledgePoints().isEmpty());
        assertTrue(empty.recommendedPracticeQuestionIds().isEmpty());

        Map<String, Object> turn = new java.util.LinkedHashMap<>(Map.of("questionId", "RADAR-41"));
        turn.put("evaluation", null);
        assertTrue(InterviewReportAggregator.aggregate(List.of(turn), Map.of()).weaknesses().isEmpty());
        assertEquals(List.of(), InterviewReportAggregator.aggregate(List.of(turn), Map.of()).recommendedPracticeQuestionIds());
    }
}
