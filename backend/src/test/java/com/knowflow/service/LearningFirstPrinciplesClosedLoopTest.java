package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.dto.FirstPrinciplesSubmitDTO;
import com.knowflow.entity.LearningEvent;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.LearningEventMapper;
import com.knowflow.vo.FirstPrinciplesRubricVO;
import com.knowflow.vo.FirstPrinciplesStepVO;
import com.knowflow.vo.FirstPrinciplesSubmitResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture-4.1 第一性原理推导集成测试（真实 H2，无 mock）。
 * <p>
 * 覆盖 Understanding 节点：六环节 rubric 确定性下发、结构完整度评估（只判写没写够，
 * 不判语义对错）、空/超长 400、FIRST_PRINCIPLES_DERIVED 事件按用户隔离落库。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("local")
class LearningFirstPrinciplesClosedLoopTest {

    /** 独立测试用户，避开 data.sql 种子与其它测试常量。 */
    private static final Long UID_A = 80011L;
    private static final Long UID_B = 80012L;

    @Autowired private LearningFirstPrinciplesService firstPrinciplesService;
    @Autowired private LearningEventMapper eventMapper;

    @BeforeEach
    void setUp() {
        eventMapper.delete(new LambdaQueryWrapper<LearningEvent>()
                .in(LearningEvent::getUserId, UID_A, UID_B)
                .eq(LearningEvent::getEventType, "FIRST_PRINCIPLES_DERIVED"));
    }

    private FirstPrinciplesSubmitDTO dto(String topic, String facts, String assumptions, String constraints,
                                         String mechanism, String causality, String derivation) {
        FirstPrinciplesSubmitDTO d = new FirstPrinciplesSubmitDTO();
        d.setTopic(topic);
        d.setFacts(facts);
        d.setAssumptions(assumptions);
        d.setConstraints(constraints);
        d.setMechanism(mechanism);
        d.setCausality(causality);
        d.setDerivation(derivation);
        d.setDurationSeconds(240);
        return d;
    }

    private FirstPrinciplesSubmitDTO fullDto(String topic) {
        return dto(topic,
                "对象创建后可能被回收，堆内存大小有限",
                "大多数对象朝生夕死，这个假设需要验证",
                "回收时必须处理并发，内存不能无限增长",
                "可达性分析 → 标记存活对象 → 回收不可达对象",
                "因为对象朝生夕死，所以分代，所以年轻代用复制算法",
                "从可达性加分代假设加停顿约束，重新推出需要分代收集器");
    }

    @Test
    @DisplayName("A4.1-1: rubric 确定性下发：六环节、顺序固定、含「只评结构不评对错」口径说明")
    void rubricShape() {
        FirstPrinciplesRubricVO rubric = firstPrinciplesService.rubric();
        assertEquals(List.of("facts", "assumptions", "constraints", "mechanism", "causality", "derivation"),
                rubric.getSteps().stream().map(FirstPrinciplesStepVO::getKey).collect(Collectors.toList()),
                "环节顺序固定：事实 → 假设 → 约束 → 机制 → 因果 → 重推");
        assertNotNull(rubric.getScoringNote());
        assertTrue(rubric.getScoringNote().contains("不衡量"), "口径必须如实说明不判断语义正确性");
        for (int i = 0; i < rubric.getSteps().size(); i++) {
            assertEquals(i + 1, rubric.getSteps().get(i).getIndex());
            assertFalse(rubric.getSteps().get(i).getTitle().isBlank());
            assertEquals(8, rubric.getSteps().get(i).getMinLength());
        }
    }

    @Test
    @DisplayName("A4.1-2: 六环节全填 → 完整度 100，无缺失")
    void submitAllFilled() {
        FirstPrinciplesSubmitResultVO r = firstPrinciplesService.submit(UID_A, fullDto("为什么 JVM 要分代回收"));
        assertEquals(100, r.getCompleteness());
        assertEquals(6, r.getFilled());
        assertEquals(6, r.getTotal());
        assertTrue(r.getMissing().isEmpty());
        assertTrue(Boolean.TRUE.equals(r.getRecorded()));
    }

    @Test
    @DisplayName("A4.1-3: 只填 4 个 → 完整度 67，缺失环节如实列出")
    void submitPartialFilled() {
        FirstPrinciplesSubmitDTO d = fullDto("JVM GC");
        d.setCausality("");
        d.setDerivation("   ");
        FirstPrinciplesSubmitResultVO r = firstPrinciplesService.submit(UID_A, d);
        assertEquals(67, r.getCompleteness());
        assertEquals(4, r.getFilled());
        assertEquals(List.of("causality", "derivation"), r.getMissing());
    }

    @Test
    @DisplayName("A4.1-4: 只判结构不判对错——内容换成无意义但等长文本，完整度仍为 100")
    void structureOnlyNotSemantics() {
        FirstPrinciplesSubmitResultVO r = firstPrinciplesService.submit(UID_A,
                dto("任意主题", "aaaaaaaa", "bbbbbbbb", "cccccccc", "dddddddd", "eeeeeeee", "ffffffff"));
        assertEquals(100, r.getCompleteness(), "系统不判断语义正确性，结构完整即满分（§16 如实）");
    }

    @Test
    @DisplayName("A4.1-5: 短于 8 字（去空白后）不算填写合格")
    void shortTextNotFilled() {
        FirstPrinciplesSubmitDTO d = fullDto("JVM GC");
        d.setMechanism("机制");
        d.setCausality("   a b  ");
        FirstPrinciplesSubmitResultVO r = firstPrinciplesService.submit(UID_A, d);
        assertEquals(67, r.getCompleteness());
        assertTrue(r.getMissing().contains("mechanism"));
        assertTrue(r.getMissing().contains("causality"));
    }

    @Test
    @DisplayName("A4.1-6: 空主题 / 过短主题 / 超长 → 400")
    void validation() {
        BusinessException noTopic = assertThrows(BusinessException.class,
                () -> firstPrinciplesService.submit(UID_A, fullDto("  ")));
        assertEquals(400, noTopic.getCode());

        BusinessException shortTopic = assertThrows(BusinessException.class,
                () -> firstPrinciplesService.submit(UID_A, fullDto("J")));
        assertEquals(400, shortTopic.getCode());

        BusinessException longTopic = assertThrows(BusinessException.class,
                () -> firstPrinciplesService.submit(UID_A, fullDto("长".repeat(201))));
        assertEquals(400, longTopic.getCode());

        FirstPrinciplesSubmitDTO longStep = fullDto("JVM GC");
        longStep.setFacts("长".repeat(4001));
        BusinessException tooLong = assertThrows(BusinessException.class,
                () -> firstPrinciplesService.submit(UID_A, longStep));
        assertEquals(400, tooLong.getCode());
    }

    @Test
    @DisplayName("A4.1-7: FIRST_PRINCIPLES_DERIVED 事件按用户隔离（resourceType=FIRST_PRINCIPLES、resourceId=0、元数据含完整度）")
    void eventPerUserIsolation() {
        firstPrinciplesService.submit(UID_A, fullDto("为什么 JVM 要分代回收"));

        FirstPrinciplesSubmitDTO half = fullDto("为什么需要 TLS");
        half.setCausality("");
        half.setDerivation("");
        half.setConstraints("");
        firstPrinciplesService.submit(UID_B, half);

        LearningEvent evA = eventMapper.selectOne(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID_A)
                .eq(LearningEvent::getEventType, "FIRST_PRINCIPLES_DERIVED")
                .orderByDesc(LearningEvent::getId).last("LIMIT 1"));
        LearningEvent evB = eventMapper.selectOne(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID_B)
                .eq(LearningEvent::getEventType, "FIRST_PRINCIPLES_DERIVED")
                .orderByDesc(LearningEvent::getId).last("LIMIT 1"));
        assertNotNull(evA);
        assertNotNull(evB);
        assertEquals("FIRST_PRINCIPLES", evA.getResourceType());
        assertEquals(0L, evA.getResourceId().longValue());
        assertTrue(evA.getMetadata().contains("\"completeness\":100"), "A 全填完整度 100 应入事件元数据");
        assertTrue(evB.getMetadata().contains("\"completeness\":50"), "B 填 3 个完整度 50 应入事件元数据");
        assertTrue(evA.getMetadata().contains("\"topic\":\"为什么 JVM 要分代回收\""));
        assertTrue(evA.getMetadata().contains("\"total\":6"));

        assertEquals(1, eventMapper.selectCount(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID_A)
                .eq(LearningEvent::getEventType, "FIRST_PRINCIPLES_DERIVED")));
        assertEquals(1, eventMapper.selectCount(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID_B)
                .eq(LearningEvent::getEventType, "FIRST_PRINCIPLES_DERIVED")));
    }
}
