package com.knowflow.interview.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.capability.AiCapabilityProperties;
import com.knowflow.interview.skill.InterviewSkillRegistry;
import com.knowflow.knowledge.mapper.DocDocumentMapper;
import com.knowflow.learning.mapper.QuizQuestionMapper;
import com.knowflow.security.DocumentAccessPolicy;
import com.knowflow.ai.service.AiService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 结构、范围、追问与引用原文校验必须失败关闭；测试不触发数据库与模型。 */
class InterviewRagGenerationServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final InterviewRagGenerationService service = new InterviewRagGenerationService(
            mock(JdbcTemplate.class), json, mock(AiService.class), new AiCapabilityProperties(),
            mock(DocumentAccessPolicy.class), mock(DocDocumentMapper.class), mock(QuizQuestionMapper.class),
            new InterviewSkillRegistry(), mock(PlatformTransactionManager.class));

    private final InterviewRagGenerationService.Evidence evidence = new InterviewRagGenerationService.Evidence(
            1, 10L, "HashMap 笔记", 0, "HashMap 扩容时会重新计算索引，并把旧桶中的元素拆分到新数组。");

    private Map<String, Object> question() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", "HashMap 扩容时为什么需要重新计算索引？");
        item.put("category", "Java 集合");
        item.put("difficulty", 2);
        item.put("questionType", "SHORT_ANSWER");
        item.put("referenceAnswer", "扩容后数组长度变化，原索引不再适用，需要按新容量重新分布元素。");
        item.put("referencePoints", List.of("扩容会改变数组长度", "旧桶元素需要重新分布"));
        item.put("rubric", Map.of("level1", "只能说扩容", "level3", "能说明索引变化", "level5", "能结合源码说明元素拆分"));
        item.put("followUps", List.of("扩容后元素如何重新分布？"));
        item.put("sourceChunkIds", List.of(1));
        item.put("citations", List.of("扩容时会重新计算索引"));
        return item;
    }

    private String output(Object... questions) throws Exception {
        return json.writeValueAsString(Map.of("questions", List.of(questions)));
    }

    @Test
    void validEvidenceBackedDraftIsAccepted() throws Exception {
        var drafts = service.parse(output(question()), List.of(evidence), 2, 2, "HashMap");
        assertEquals(1, drafts.size());
    }

    @Test
    void fabricatedCitationIsRejectedEvenWhenStructureIsValid() throws Exception {
        Map<String, Object> item = question();
        item.put("citations", List.of("我已实现百万 QPS 的 HashMap 优化"));
        assertThrows(IllegalArgumentException.class, () -> service.parse(output(item), List.of(evidence), 2, 2, "HashMap"));
    }

    @Test
    void unknownSourceChunkAndQuestionCountAreRejected() throws Exception {
        Map<String, Object> item = question();
        item.put("sourceChunkIds", List.of(99));
        assertThrows(IllegalArgumentException.class, () -> service.parse(output(item), List.of(evidence), 2, 2, "HashMap"));
        assertThrows(IllegalArgumentException.class, () -> service.parse(output(question(), question()), List.of(evidence), 1, 2, "HashMap"));
        assertThrows(IllegalArgumentException.class, () -> service.parse("{\"questions\":[]}", List.of(evidence), 2, 2, "HashMap"));
    }

    @Test
    void extraRootFieldAndInvalidRubricAreRejected() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("questions", List.of(question()));
        root.put("answer", "泄露标准答案");
        assertThrows(IllegalArgumentException.class, () -> service.parse(json.writeValueAsString(root), List.of(evidence), 2, 2, "HashMap"));

        Map<String, Object> item = question();
        item.put("rubric", Map.of("level1", "只有一级"));
        assertThrows(IllegalArgumentException.class, () -> service.parse(output(item), List.of(evidence), 2, 2, "HashMap"));
    }

    @Test
    void followUpsMustBeQuestionsAndUniqueWithinBatch() throws Exception {
        Map<String, Object> item = question();
        item.put("followUps", List.of("扩容后元素如何重新分布"));
        assertThrows(IllegalArgumentException.class, () -> service.parse(output(item), List.of(evidence), 2, 2, "HashMap"));

        item.put("followUps", List.of("扩容后元素如何重新分布？", "扩容后元素如何重新分布？"));
        assertThrows(IllegalArgumentException.class, () -> service.parse(output(item), List.of(evidence), 2, 2, "HashMap"));

        Map<String, Object> duplicate = question();
        duplicate.put("title", "HashMap 扩容时为什么需要重新计算索引");
        assertThrows(IllegalArgumentException.class, () -> service.parse(output(question(), duplicate), List.of(evidence), 2, 2, "HashMap"));
    }

    @Test
    void difficultyOutsideRangeIsRejected() throws Exception {
        Map<String, Object> item = question();
        item.put("difficulty", 5);
        assertThrows(IllegalArgumentException.class, () -> service.parse(output(item), List.of(evidence), 2, 2, "HashMap"));
    }
}
