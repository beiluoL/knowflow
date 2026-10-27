package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.dto.FeynmanSubmitDTO;
import com.knowflow.dto.FirstPrinciplesSubmitDTO;
import com.knowflow.dto.ScenarioSubmitDTO;
import com.knowflow.entity.LearningEvent;
import com.knowflow.entity.LearningScenario;
import com.knowflow.mapper.LearningEventMapper;
import com.knowflow.mapper.LearningScenarioMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture-4.2 §9~§11：学习行为事件按真实知识点上下文落库（无 mock，真实 H2）。
 * <p>
 * 验证三类 Lab 在页面透传 / 场景自带真实 knowledgeId 时，确实写入事件 metadata，
 * 供 Evidence 层后续绑定到具体知识点；未提供时一律不写（禁止回退猜测）。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("local")
class KnowledgeContextMetadataTest {

    private static final Long UID_A = 82001L;
    private static final Long UID_B = 82002L;
    private static final AtomicLong SEQ = new AtomicLong(2000);

    private static final String KEY_POINTS =
            "[{\"label\":\"现象确认\",\"keywords\":[\"gc日志\"]},"
            + "{\"label\":\"jstat 观察\",\"keywords\":[\"jstat\"]},"
            + "{\"label\":\"jmap 导出\",\"keywords\":[\"jmap\"]},"
            + "{\"label\":\"MAT 分析\",\"keywords\":[\"mat工具\"]}]";

    @Autowired private LearningFeynmanService feynmanService;
    @Autowired private LearningFirstPrinciplesService firstPrinciplesService;
    @Autowired private LearningScenarioService scenarioService;
    @Autowired private LearningScenarioMapper scenarioMapper;
    @Autowired private LearningEventMapper eventMapper;

    @BeforeEach
    void setUp() {
        eventMapper.delete(new LambdaQueryWrapper<LearningEvent>()
                .in(LearningEvent::getUserId, UID_A, UID_B)
                .in(LearningEvent::getEventType, "FEYNMAN_EXPRESSED", "FIRST_PRINCIPLES_DERIVED", "SCENARIO_COMPLETED"));
        scenarioMapper.delete(new LambdaQueryWrapper<LearningScenario>().likeRight(LearningScenario::getTitle, "TEST-KCM-"));
    }

    private LearningScenario insertScenario(String suffix, Long knowledgeId) {
        LearningScenario s = new LearningScenario();
        s.setTitle("TEST-KCM-" + suffix + "-" + SEQ.incrementAndGet());
        s.setContextText("排查思路测试");
        s.setQuestionText("请描述排查思路。");
        s.setKeyPoints(KEY_POINTS);
        s.setDifficulty(2);
        s.setStatus(1);
        s.setKnowledgeId(knowledgeId);
        scenarioMapper.insert(s);
        return s;
    }

    private LearningEvent lastEvent(String eventType) {
        return eventMapper.selectOne(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID_A)
                .eq(LearningEvent::getEventType, eventType)
                .orderByDesc(LearningEvent::getId).last("LIMIT 1"));
    }

    @Test
    @DisplayName("A4.2-4: 费曼提交透传 knowledgeId → 事件 metadata 含真实 knowledgeId")
    void feynmanWritesKnowledgeId() {
        FeynmanSubmitDTO dto = new FeynmanSubmitDTO();
        dto.setTopic("JVM GC 是什么");
        dto.setExplanation("年轻代负责回收短命对象；新对象先放 Eden，Minor GC 用复制算法晋升老年代。");
        dto.setCheckedCriterionIds(List.of());
        dto.setDurationSeconds(180);
        dto.setKnowledgeId(42L);
        feynmanService.submit(UID_A, dto);

        LearningEvent ev = lastEvent("FEYNMAN_EXPRESSED");
        assertNotNull(ev);
        assertNotNull(ev.getMetadata());
        assertTrue(ev.getMetadata().contains("\"knowledgeId\":42"), "费曼事件应写入透传的 knowledgeId=42");
    }

    @Test
    @DisplayName("A4.2-5: 第一性原理提交透传 knowledgeId → 事件 metadata 含真实 knowledgeId")
    void firstPrinciplesWritesKnowledgeId() {
        FirstPrinciplesSubmitDTO dto = new FirstPrinciplesSubmitDTO();
        dto.setTopic("为什么 JVM 要分代回收");
        dto.setFacts("对象创建后可能被回收，堆内存有限");
        dto.setAssumptions("大多数对象朝生夕死");
        dto.setConstraints("回收需处理并发");
        dto.setMechanism("可达性分析标记存活对象");
        dto.setCausality("因为对象朝生夕死，所以分代");
        dto.setDerivation("从可达性加分代假设重推需要分代收集器");
        dto.setDurationSeconds(240);
        dto.setKnowledgeId(42L);
        firstPrinciplesService.submit(UID_A, dto);

        LearningEvent ev = lastEvent("FIRST_PRINCIPLES_DERIVED");
        assertNotNull(ev);
        assertTrue(ev.getMetadata().contains("\"knowledgeId\":42"), "第一性原理事件应写入透传的 knowledgeId=42");
    }

    @Test
    @DisplayName("A4.2-6: 场景自带 knowledgeId → 提交事件 metadata 含该知识点（场景无透参也绑定）")
    void scenarioOwnKnowledgeId() {
        LearningScenario s = insertScenario("OWN", 77L);
        ScenarioSubmitDTO dto = new ScenarioSubmitDTO();
        dto.setAnswer("开启 gc 日志看 fullgc 次数，jstat 查老年代，jmap 堆转储，mat 工具看支配树。");
        dto.setDurationSeconds(120);
        scenarioService.submit(UID_A, s.getId(), dto);

        LearningEvent ev = lastEvent("SCENARIO_COMPLETED");
        assertNotNull(ev);
        assertTrue(ev.getMetadata().contains("\"knowledgeId\":77"), "场景自带的 knowledgeId=77 应写入事件");
    }

    @Test
    @DisplayName("A4.2-7: 场景未配置 knowledgeId → 事件 metadata 不包含 knowledgeId（禁止猜测）")
    void scenarioNoKnowledgeId() {
        LearningScenario s = insertScenario("NONE", null);
        ScenarioSubmitDTO dto = new ScenarioSubmitDTO();
        dto.setAnswer("开启 gc 日志，jstat 观察，jmap 导出堆转储，mat 分析。");
        dto.setDurationSeconds(120);
        scenarioService.submit(UID_A, s.getId(), dto);

        LearningEvent ev = lastEvent("SCENARIO_COMPLETED");
        assertNotNull(ev);
        assertFalse(ev.getMetadata().contains("knowledgeId"), "未配置知识点时不得写入 knowledgeId");
    }
}
