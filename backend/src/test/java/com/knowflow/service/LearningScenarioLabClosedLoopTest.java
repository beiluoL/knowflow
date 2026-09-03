package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.dto.ScenarioSubmitDTO;
import com.knowflow.entity.LearningEvent;
import com.knowflow.entity.LearningScenario;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.LearningEventMapper;
import com.knowflow.mapper.LearningScenarioMapper;
import com.knowflow.vo.ScenarioDetailVO;
import com.knowflow.vo.ScenarioSubmitResultVO;
import com.knowflow.vo.ScenarioSummaryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture-4.0-4 场景练习闭环集成测试（真实 H2，无 mock）。
 * <p>
 * 覆盖 Practice 节点「Scenario Lab」侧：内容列表/详情（不含关键词泄露）、确定性要点覆盖评分
 * （全覆盖 / 部分覆盖 / 空答 400 / 不存在 404 / 超长 400）、SCENARIO_COMPLETED 事件按用户隔离落库。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("local")
class LearningScenarioLabClosedLoopTest {

    /** 独立测试用户，避开 data.sql 种子用户与其它测试常量。 */
    private static final Long UID_A = 80001L;
    private static final Long UID_B = 80002L;

    /** 每场景自增后缀，保证每次插入都是独立行（事件断言按 resourceId 精确计数）。 */
    private static final AtomicLong SEQ = new AtomicLong(1000);

    /** 测试用 Full GC 场景 4 要点 JSON（label 唯一、keywords 互不重叠，便于确定性断言）。 */
    private static final String KEY_POINTS_4 =
            "[{\"label\":\"现象确认与 GC 日志\",\"keywords\":[\"gc日志\",\"gclog\",\"fullgc次数\"]},"
            + "{\"label\":\"jstat 观察回收与堆\",\"keywords\":[\"jstat\",\"老年代\",\"ygc频次\"]},"
            + "{\"label\":\"jmap 导出堆转储\",\"keywords\":[\"jmap\",\"堆转储\"]},"
            + "{\"label\":\"MAT 分析引用链\",\"keywords\":[\"mat工具\",\"支配树\"]}]";

    @Autowired private LearningScenarioService scenarioService;
    @Autowired private LearningScenarioMapper scenarioMapper;
    @Autowired private LearningEventMapper eventMapper;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(UID_A, null, List.of()));
        // 隔离：清掉本测试可能残留的数据（每次 H2 全新加载，此处为幂等兜底）
        scenarioMapper.delete(new LambdaQueryWrapper<LearningScenario>().likeRight(LearningScenario::getTitle, "TEST-"));
        eventMapper.delete(new LambdaQueryWrapper<LearningEvent>()
                .in(LearningEvent::getUserId, UID_A, UID_B)
                .eq(LearningEvent::getEventType, "SCENARIO_COMPLETED"));
    }

    /** 插入一条测试场景（status=1 上架），返回实体。 */
    private LearningScenario insertScenario(String titleSuffix, int difficulty, String keyPointsJson, Integer status) {
        LearningScenario s = new LearningScenario();
        s.setTitle("TEST-" + titleSuffix + "-" + SEQ.incrementAndGet());
        s.setContextText("测试情景背景：系统出现告警，需要你作为当班工程师给出排查思路。");
        s.setQuestionText("请描述你的排查思路（覆盖要点关键词即可命中）。");
        s.setKeyPoints(keyPointsJson);
        s.setDifficulty(difficulty);
        s.setStatus(status != null ? status : 1);
        scenarioMapper.insert(s);
        return s;
    }

    private ScenarioSubmitDTO submitDto(String answer) {
        ScenarioSubmitDTO dto = new ScenarioSubmitDTO();
        dto.setAnswer(answer);
        dto.setDurationSeconds(120);
        return dto;
    }

    @Test
    @DisplayName("A4.0-4.1: 列表返回上架场景并可过滤；下架场景不出现")
    void listPublishedAndDetail() {
        LearningScenario mine = insertScenario("GC", 2, KEY_POINTS_4, 1);
        insertScenario("OFFSHELF", 1, KEY_POINTS_4, 0);

        List<ScenarioSummaryVO> all = scenarioService.listPublished(null, null);
        assertTrue(all.stream().anyMatch(v -> v.getId().equals(mine.getId())), "上架测试场景应出现在列表");
        assertTrue(all.stream().noneMatch(v -> v.getTitle().startsWith("TEST-OFFSHELF")), "下架场景不应出现");

        List<ScenarioSummaryVO> diff2 = scenarioService.listPublished(null, 2);
        assertTrue(diff2.stream().allMatch(v -> v.getDifficulty() == 2), "难度过滤只返回 difficulty=2");

        // 详情：返回要点名称但不泄露命中关键词
        ScenarioDetailVO detail = scenarioService.detail(mine.getId());
        assertEquals(4, detail.getPointLabels().size());
        assertEquals(List.of("现象确认与 GC 日志", "jstat 观察回收与堆", "jmap 导出堆转储", "MAT 分析引用链"),
                detail.getPointLabels(), "要点 label 原样返回");
        // 详情只下发 label；keywords 中的“专属同义词 token”（如 gclog / fullgc次数 这类
        // 与 label 无重叠、仅服务端评分口径的字符串）不得出现在返回里
        assertFalse(detail.getPointLabels().stream().anyMatch(l -> l.contains("gclog") || l.contains("fullgc次数")),
                "详情不得泄露 keywords 专属评分 token");
    }

    @Test
    @DisplayName("A4.0-4.2: 提交答案覆盖全部要点 → coverage=1.0 / score=100 / 无漏点")
    void submitFullCoverage() {
        LearningScenario s = insertScenario("GC", 2, KEY_POINTS_4, 1);
        String answer = "先开启 GC 日志确认 fullgc 次数；再用 jstat 观察老年代与 ygc 频次；"
                + "随后 jmap 导出堆转储；最后用 mat 工具分析支配树定位大对象。";
        ScenarioSubmitResultVO r = scenarioService.submit(UID_A, s.getId(), submitDto(answer));

        assertEquals(s.getId(), r.getScenarioId());
        assertEquals(4, r.getTotalPoints());
        assertEquals(4, r.getMatchedPoints());
        assertEquals(1.0, r.getCoverage(), 0.0001);
        assertEquals(100, r.getScore());
        assertTrue(r.getMissedLabels().isEmpty());
        assertEquals(4, r.getMatchedLabels().size());
        assertTrue(r.getComment().contains("全覆盖"));
        assertTrue(Boolean.TRUE.equals(r.getRecorded()));
    }

    @Test
    @DisplayName("A4.0-4.3: 部分覆盖 → coverage 按命中要点折算；漏点回传用于补强")
    void submitPartialCoverage() {
        LearningScenario s = insertScenario("GC", 2, KEY_POINTS_4, 1);
        String answer = "用 jstat 看老年代占用和 ygc 频次，观察是否持续增长。";
        ScenarioSubmitResultVO r = scenarioService.submit(UID_A, s.getId(), submitDto(answer));

        assertEquals(4, r.getTotalPoints());
        assertEquals(1, r.getMatchedPoints(), "只有 jstat 要点命中");
        assertEquals(0.25, r.getCoverage(), 0.0001);
        assertEquals(25, r.getScore());
        assertEquals(3, r.getMissedLabels().size());
        assertTrue(r.getMatchedLabels().contains("jstat 观察回收与堆"));
    }

    @Test
    @DisplayName("A4.0-4.4: 空答 400 / 超长 400 / 不存在与下架 404")
    void validationAndMissing() {
        LearningScenario s = insertScenario("GC", 2, KEY_POINTS_4, 1);

        BusinessException empty = assertThrows(BusinessException.class,
                () -> scenarioService.submit(UID_A, s.getId(), submitDto("   ")));
        assertEquals(400, empty.getCode());

        BusinessException tooLong = assertThrows(BusinessException.class,
                () -> scenarioService.submit(UID_A, s.getId(), submitDto("长".repeat(10001))));
        assertEquals(400, tooLong.getCode());

        BusinessException missing = assertThrows(BusinessException.class,
                () -> scenarioService.detail(99999999L));
        assertEquals(404, missing.getCode());

        LearningScenario off = insertScenario("OFFSHELF2", 1, KEY_POINTS_4, 0);
        BusinessException offShelf = assertThrows(BusinessException.class,
                () -> scenarioService.detail(off.getId()));
        assertEquals(404, offShelf.getCode());
    }

    @Test
    @DisplayName("A4.0-4.5: SCENARIO_COMPLETED 事件按用户隔离落库（内容共享、行为隔离）")
    void eventPerUserIsolation() {
        LearningScenario s = insertScenario("GC", 2, KEY_POINTS_4, 1);
        String answer = "开启 gc 日志看 fullgc 次数，jstat 查老年代 ygc 频次，jmap 堆转储，mat 工具看支配树。";

        // A、B 都练同一场景（内容共享）
        scenarioService.submit(UID_A, s.getId(), submitDto(answer));
        scenarioService.submit(UID_B, s.getId(), submitDto(answer));

        LearningEvent evA = eventMapper.selectOne(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID_A)
                .eq(LearningEvent::getEventType, "SCENARIO_COMPLETED")
                .eq(LearningEvent::getResourceId, s.getId()));
        LearningEvent evB = eventMapper.selectOne(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID_B)
                .eq(LearningEvent::getEventType, "SCENARIO_COMPLETED")
                .eq(LearningEvent::getResourceId, s.getId()));
        assertNotNull(evA, "A 提交后应有 SCENARIO_COMPLETED 事件");
        assertNotNull(evB, "B 提交后应有自己的 SCENARIO_COMPLETED 事件");
        assertEquals("SCENARIO", evA.getResourceType());
        assertTrue(evA.getMetadata() != null && evA.getMetadata().contains("\"coverage\":1.0"),
                "事件元数据应携带确定性评分 coverage");

        // A 只应有自己 1 条该场景事件（无 B 的串扰）
        assertEquals(1, eventMapper.selectCount(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, UID_A)
                .eq(LearningEvent::getEventType, "SCENARIO_COMPLETED")
                .eq(LearningEvent::getResourceId, s.getId())));
    }
}
