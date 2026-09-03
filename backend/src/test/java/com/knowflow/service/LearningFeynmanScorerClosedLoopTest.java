package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.dto.FeynmanSubmitDTO;
import com.knowflow.entity.LearningEvent;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.LearningEventMapper;
import com.knowflow.vo.FeynmanCriterionVO;
import com.knowflow.vo.FeynmanDimensionResultVO;
import com.knowflow.vo.FeynmanDimensionVO;
import com.knowflow.vo.FeynmanRubricVO;
import com.knowflow.vo.FeynmanSubmitResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture-4.0-7 费曼四维真实评分器集成测试（真实 H2，无 mock）。
 * <p>
 * 覆盖 Expression 节点：rubric 确定性下发（4 维 × 4 准则、id 稳定）、
 * 全勾 / 部分勾 / 未知 id 容错与重复去重封顶的确定性计分、空/超长 400、
 * FEYNMAN_EXPRESSED 事件按用户隔离落库（resourceType=FEYNMAN、resourceId=0）。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("local")
class LearningFeynmanScorerClosedLoopTest {

    /** 独立测试用户，避开 data.sql 种子与其它测试常量。 */
    private static final Long UID_A = 80003L;
    private static final Long UID_B = 80004L;

    @Autowired private LearningFeynmanService feynmanService;
    @Autowired private LearningEventMapper eventMapper;

    @BeforeEach
    void setUp() {
        eventMapper.delete(new LambdaQueryWrapper<LearningEvent>()
                .in(LearningEvent::getUserId, UID_A, UID_B)
                .eq(LearningEvent::getEventType, "FEYNMAN_EXPRESSED"));
    }

    private FeynmanSubmitDTO dto(String topic, String explanation, List<String> checked) {
        FeynmanSubmitDTO dto = new FeynmanSubmitDTO();
        dto.setTopic(topic);
        dto.setExplanation(explanation);
        dto.setCheckedCriterionIds(checked);
        dto.setDurationSeconds(180);
        return dto;
    }

    private String sampleExplanation() {
        return "年轻代 GC 负责回收生命周期短的对象。新对象先放 Eden，"
                + "Minor GC 时把还活着的对象经复制算法挪进 Survivor 并累加年龄，"
                + "年龄够大或放不下就晋升老年代，避免长命对象在年轻代反复搬运。";
    }

    @Test
    @DisplayName("A4.0-7.1: rubric 确定性下发：4 维 × 4 条稳定 id 自检准则，含计分口径说明")
    void rubricShape() {
        FeynmanRubricVO rubric = feynmanService.rubric();
        assertEquals(List.of("ACCURACY", "CLARITY", "COMPLETENESS", "STRUCTURE"),
                rubric.getDimensions().stream().map(FeynmanDimensionVO::getKey).collect(Collectors.toList()),
                "维度顺序固定：准确性 → 清晰度 → 完整度 → 结构");
        assertNotNull(rubric.getScoringNote());
        Set<String> allIds = rubric.getDimensions().stream()
                .flatMap(d -> d.getItems().stream()).map(FeynmanCriterionVO::getId)
                .collect(Collectors.toSet());
        assertEquals(16, allIds.size(), "准则 id 全局唯一（16 个）");
        for (FeynmanDimensionVO dim : rubric.getDimensions()) {
            assertEquals(4, dim.getItems().size(), dim.getKey() + " 应有 4 条自检准则");
            assertTrue(dim.getItems().stream().allMatch(i -> i.getText() != null && !i.getText().isBlank()),
                    dim.getKey() + " 准则文本非空");
        }
    }

    @Test
    @DisplayName("A4.0-7.2: 全勾 16 条 → 四维 100 / 总分 100 / 点评含全绿")
    void submitAllChecked() {
        FeynmanRubricVO rubric = feynmanService.rubric();
        List<String> all = rubric.getDimensions().stream()
                .flatMap(d -> d.getItems().stream()).map(FeynmanCriterionVO::getId)
                .collect(Collectors.toList());
        FeynmanSubmitResultVO r = feynmanService.submit(UID_A, dto("JVM 年轻代 GC", sampleExplanation(), all));

        assertEquals(100, r.getOverall());
        assertEquals(4, r.getDimensions().size());
        assertTrue(r.getDimensions().stream().allMatch(d -> d.getScore() == 100 && d.getChecked() == 4));
        assertTrue(r.getComment().contains("全绿"));
        assertTrue(Boolean.TRUE.equals(r.getRecorded()));
    }

    @Test
    @DisplayName("A4.0-7.3: 部分勾选 → 各维按勾选数折算，总分 = round(勾选/16×100)")
    void submitPartialChecked() {
        // 只勾 Accuracy 4 条 → accuracy=100、其余 0、总分 25
        FeynmanSubmitResultVO accOnly = feynmanService.submit(UID_A,
                dto("主题A", sampleExplanation(),
                        List.of("accuracy-1", "accuracy-2", "accuracy-3", "accuracy-4")));
        assertEquals(25, accOnly.getOverall());
        FeynmanDimensionResultVO acc = accOnly.getDimensions().get(0);
        assertEquals("ACCURACY", acc.getKey());
        assertEquals(100, acc.getScore());
        assertEquals(0, accOnly.getDimensions().get(1).getScore());

        // 勾 6 条（accuracy 3 + structure 3）→ 总分 round(37.5) = 38
        FeynmanSubmitResultVO six = feynmanService.submit(UID_A,
                dto("主题B", sampleExplanation(),
                        List.of("accuracy-1", "accuracy-2", "accuracy-3",
                                "structure-1", "structure-2", "structure-3")));
        assertEquals(38, six.getOverall());
        assertEquals(75, six.getDimensions().get(0).getScore());
        assertEquals(75, six.getDimensions().get(3).getScore());
        assertEquals(0, six.getDimensions().get(2).getScore());
    }

    @Test
    @DisplayName("A4.0-7.4: 空主题 / 空正文 / 超长 → 400")
    void validation() {
        BusinessException noTopic = assertThrows(BusinessException.class,
                () -> feynmanService.submit(UID_A, dto("   ", sampleExplanation(), List.of())));
        assertEquals(400, noTopic.getCode());

        BusinessException noBody = assertThrows(BusinessException.class,
                () -> feynmanService.submit(UID_A, dto("主题", "  ", List.of())));
        assertEquals(400, noBody.getCode());

        BusinessException longTopic = assertThrows(BusinessException.class,
                () -> feynmanService.submit(UID_A, dto("长".repeat(201), sampleExplanation(), List.of())));
        assertEquals(400, longTopic.getCode());

        BusinessException longBody = assertThrows(BusinessException.class,
                () -> feynmanService.submit(UID_A, dto("主题", "长".repeat(10001), List.of())));
        assertEquals(400, longBody.getCode());
    }

    @Test
    @DisplayName("A4.0-7.5: 未知准则 id 忽略 + 重复勾选封顶（确定性容错不 500）")
    void unknownAndDuplicateIds() {
        FeynmanSubmitResultVO r = feynmanService.submit(UID_A,
                dto("主题C", sampleExplanation(),
                        List.of("unknown-x", "accuracy-1", "accuracy-1", "accuracy-2")));
        FeynmanDimensionResultVO acc = r.getDimensions().get(0);
        assertEquals(2, acc.getChecked(), "重复 accuracy-1 只计 1 次，accuracy-1/2 各一次");
        assertEquals(50, acc.getScore());
        assertEquals(0, r.getDimensions().get(1).getChecked(), "未知 id 不落任何维度");
        // overall = round(2/16*100) = 13
        assertEquals(13, r.getOverall());
    }

    @Test
    @DisplayName("A4.0-7.6: FEYNMAN_EXPRESSED 事件按用户隔离（resourceType=FEYNMAN、resourceId=0、元数据含四维分）")
    void eventPerUserIsolation() {
        List<String> all = feynmanService.rubric().getDimensions().stream()
                .flatMap(d -> d.getItems().stream()).map(FeynmanCriterionVO::getId)
                .collect(Collectors.toList());
        feynmanService.submit(UID_A, dto("JVM 年轻代 GC", sampleExplanation(), all));
        feynmanService.submit(UID_B, dto("JVM 年轻代 GC", sampleExplanation(),
                List.of("clarity-1", "clarity-2", "clarity-3", "clarity-4")));

        LearningEvent evA = eventMapper.selectOne(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID_A)
                .eq(LearningEvent::getEventType, "FEYNMAN_EXPRESSED")
                .orderByDesc(LearningEvent::getId).last("LIMIT 1"));
        LearningEvent evB = eventMapper.selectOne(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID_B)
                .eq(LearningEvent::getEventType, "FEYNMAN_EXPRESSED")
                .orderByDesc(LearningEvent::getId).last("LIMIT 1"));
        assertNotNull(evA);
        assertNotNull(evB);
        assertEquals("FEYNMAN", evA.getResourceType());
        assertEquals(0L, evA.getResourceId().longValue());
        assertTrue(evA.getMetadata().contains("\"overall\":100"), "A 全勾总分 100 应入事件元数据");
        assertTrue(evB.getMetadata().contains("\"overall\":25"), "B 只勾清晰度总分 25 应入事件元数据");
        assertTrue(evA.getMetadata().contains("\"topic\":\"JVM 年轻代 GC\""));
        assertTrue(evA.getMetadata().contains("\"clarity\":100"), "四维分应写入元数据");

        // 各用户仅自己 1 条
        assertEquals(1, eventMapper.selectCount(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID_A)
                .eq(LearningEvent::getEventType, "FEYNMAN_EXPRESSED")));
        assertEquals(1, eventMapper.selectCount(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID_B)
                .eq(LearningEvent::getEventType, "FEYNMAN_EXPRESSED")));
    }
}
