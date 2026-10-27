package com.knowflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.dto.ScenarioAdminDTO;
import com.knowflow.dto.ScenarioKeyPoint;
import com.knowflow.entity.LearningScenario;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.LearningScenarioMapper;
import com.knowflow.vo.ScenarioAdminVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture-4.0-6 场景内容管理端 CRUD 集成测试（真实 H2，无 mock）。
 * <p>
 * 覆盖后台 ADMIN 的确定性运维能力：列表（含下架 / 关键字 / 状态过滤、排除逻辑删除）、
 * 详情（含服务端私有 keywords）、新建（默认值与 JSON 往返）、校验 400 族、覆盖式更新、
 * 删除（逻辑删除后列表与详情均不可见）。方法与既有 4.0-4 测试一致在 Service 层直测；
 * 角色门禁由 SecurityConfig /api/admin/** + Controller @PreAuthorize 双保险承担（HTTP 冒烟另验）。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("local")
class LearningScenarioAdminClosedLoopTest {

    /** 每场景自增后缀，保证插入行标题唯一、断言精确。 */
    private static final AtomicLong SEQ = new AtomicLong(9000);

    /** 测试用 2 要点 JSON：keywords 与服务端评分口径一致，便于详情断言。 */
    private static final String KEY_POINTS_2 =
            "[{\"label\":\"GC 日志确认\",\"keywords\":[\"gc日志\",\"fullgc次数\"]},"
            + "{\"label\":\"堆转储分析\",\"keywords\":[\"jmap\",\"mat工具\"]}]";

    @Autowired private LearningScenarioService scenarioService;
    @Autowired private LearningScenarioMapper scenarioMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 隔离：清掉本测试可能残留的数据（H2 每次全新加载，此处为幂等兜底）
        scenarioMapper.delete(new LambdaQueryWrapper<LearningScenario>().likeRight(LearningScenario::getTitle, "ADM-"));
    }

    private LearningScenario insertScenario(String titleSuffix, Integer status) {
        LearningScenario s = new LearningScenario();
        s.setTitle("ADM-" + titleSuffix + "-" + SEQ.incrementAndGet());
        s.setContextText("情景：生产环境出现告警。");
        s.setQuestionText("请给出排查思路。");
        s.setKeyPoints(KEY_POINTS_2);
        s.setDifficulty(2);
        s.setStatus(status);
        scenarioMapper.insert(s);
        return s;
    }

    private ScenarioAdminDTO dto(String title, String questionText, List<ScenarioKeyPoint> points,
                                  Integer difficulty, Integer status) {
        ScenarioAdminDTO dto = new ScenarioAdminDTO();
        dto.setTitle(title);
        dto.setContextText("背景：" + title);
        dto.setQuestionText(questionText);
        dto.setKeyPoints(points);
        dto.setDifficulty(difficulty);
        dto.setStatus(status);
        return dto;
    }

    private List<ScenarioKeyPoint> points(String... entries) {
        List<ScenarioKeyPoint> list = new java.util.ArrayList<>();
        for (String e : entries) {
            String[] parts = e.split(":", 2);
            ScenarioKeyPoint p = new ScenarioKeyPoint();
            p.setLabel(parts[0]);
            p.setKeywords(List.of(parts[1].split(",")));
            list.add(p);
        }
        return list;
    }

    @Test
    @DisplayName("A4.0-6.1: 管理列表含上/下架场景、支持 keyword/status 过滤且排除逻辑删除")
    void adminListFilterAndExclusion() {
        LearningScenario on = insertScenario("ON", 1);
        LearningScenario off = insertScenario("OFF", 0);

        // 含下架：两条都出现
        List<ScenarioAdminVO> all = scenarioService.adminList(null, null);
        assertTrue(all.stream().anyMatch(v -> v.getId().equals(on.getId())), "上架场景应在管理列表");
        assertTrue(all.stream().anyMatch(v -> v.getId().equals(off.getId())), "下架场景也应在管理列表");

        // keyword 命中标题（精确唯一后缀）
        List<ScenarioAdminVO> byKw = scenarioService.adminList(off.getTitle(), null);
        assertEquals(1, byKw.size());
        assertEquals(off.getId(), byKw.get(0).getId());

        // status 过滤
        List<ScenarioAdminVO> publishedOnly = scenarioService.adminList(null, 1);
        assertTrue(publishedOnly.stream().allMatch(v -> v.getStatus() == 1));
        assertTrue(publishedOnly.stream().anyMatch(v -> v.getId().equals(on.getId())));
        assertTrue(publishedOnly.stream().noneMatch(v -> v.getId().equals(off.getId())));

        // 逻辑删除后不出现在列表
        scenarioService.adminRemove(on.getId());
        List<ScenarioAdminVO> after = scenarioService.adminList(null, null);
        assertTrue(after.stream().noneMatch(v -> v.getId().equals(on.getId())), "逻辑删除场景不应出现");
    }

    @Test
    @DisplayName("A4.0-6.2: 管理详情含 keywords 与状态；不存在/已删除 → 404")
    void adminDetailShowsKeywordsAndMissing() {
        LearningScenario s = insertScenario("DETAIL", 1);
        ScenarioAdminVO vo = scenarioService.adminDetail(s.getId());
        assertEquals(s.getTitle(), vo.getTitle());
        assertEquals(1, vo.getStatus().intValue());
        assertEquals(2, vo.getKeyPoints().size());
        // 管理端可见 keywords（仅后台唯一读入口）
        assertEquals(List.of("gc日志", "fullgc次数"), vo.getKeyPoints().get(0).getKeywords());
        assertNotNull(vo.getCreateTime(), "管理 VO 应回传 createTime");

        BusinessException missing = assertThrows(BusinessException.class,
                () -> scenarioService.adminDetail(99999999L));
        assertEquals(404, missing.getCode());

        // 删除后再查详情 → 404（@TableLogic 已过滤 deleted=1）
        scenarioService.adminRemove(s.getId());
        BusinessException removed = assertThrows(BusinessException.class,
                () -> scenarioService.adminDetail(s.getId()));
        assertEquals(404, removed.getCode());
    }

    @Test
    @DisplayName("A4.0-6.3: 新建场景持久化成功，difficulty/status 默认值生效，keyPoints JSON 往返一致")
    void adminCreateRoundTrip() {
        String title = "ADM-CREATE-" + SEQ.incrementAndGet();
        ScenarioAdminVO created = scenarioService.adminCreate(
                dto(title, "请描述缓存故障排查步骤。", points("缓存穿透确认:hystrix,熔断", "回源保护:互斥锁,空值缓存"), null, null));

        assertNotNull(created.getId(), "新建应回填 id");
        assertEquals(1, created.getDifficulty().intValue(), "difficulty 缺省应为 1");
        assertEquals(1, created.getStatus().intValue(), "status 缺省应为 1");
        assertEquals(2, created.getKeyPoints().size(), "keyPoints 应序列化并往返还原");
        assertEquals("缓存穿透确认", created.getKeyPoints().get(0).getLabel());
        assertEquals(List.of("hystrix", "熔断"), created.getKeyPoints().get(0).getKeywords());

        // 落库校验：重新查询实体，JSON 可再次被学习端解析
        ScenarioAdminVO again = scenarioService.adminDetail(created.getId());
        assertEquals(created.getTitle(), again.getTitle());
        assertEquals(created.getKeyPoints().size(), again.getKeyPoints().size());
    }

    @Test
    @DisplayName("A4.0-6.4: 新建/更新校验 400 族（空标题/空问题/空要点/无关键词/难度与状态越界/不存在更新）")
    void adminValidationAndUpdate() {
        String title = "ADM-VALID-" + SEQ.incrementAndGet();
        // 新建：标题空
        BusinessException noTitle = assertThrows(BusinessException.class,
                () -> scenarioService.adminCreate(dto("  ", "问题", points("P1:k1"), 1, 1)));
        assertEquals(400, noTitle.getCode());
        // 问题空
        BusinessException noQuestion = assertThrows(BusinessException.class,
                () -> scenarioService.adminCreate(dto("T", "  ", points("P1:k1"), 1, 1)));
        assertEquals(400, noQuestion.getCode());
        // 要点空清单
        BusinessException noPoints = assertThrows(BusinessException.class,
                () -> scenarioService.adminCreate(dto("T", "问题", null, 1, 1)));
        assertEquals(400, noPoints.getCode());
        // 某条要点无关键词（只有空白）
        BusinessException noKw = assertThrows(BusinessException.class,
                () -> scenarioService.adminCreate(dto("T", "问题", points("P1:   "), 1, 1)));
        assertEquals(400, noKw.getCode());
        // 难度越界
        BusinessException badDiff = assertThrows(BusinessException.class,
                () -> scenarioService.adminCreate(dto("T", "问题", points("P1:k1"), 9, 1)));
        assertEquals(400, badDiff.getCode());
        // 状态越界
        BusinessException badStatus = assertThrows(BusinessException.class,
                () -> scenarioService.adminCreate(dto("T", "问题", points("P1:k1"), 2, 2)));
        assertEquals(400, badStatus.getCode());

        // 更新：覆盖式改标题/难度/状态/要点
        LearningScenario s = insertScenario("UPD", 1);
        ScenarioAdminVO updated = scenarioService.adminUpdate(s.getId(),
                dto(title, "请描述慢 SQL 治理思路。", points("执行计划:explain", "索引优化:联合索引,覆盖索引"), 3, 0));
        assertEquals(title, updated.getTitle());
        assertEquals(3, updated.getDifficulty().intValue());
        assertEquals(0, updated.getStatus().intValue());
        assertEquals(2, updated.getKeyPoints().size());
        assertEquals(List.of("explain"), updated.getKeyPoints().get(0).getKeywords());

        // 更新：问题不可置空
        BusinessException blankQ = assertThrows(BusinessException.class,
                () -> scenarioService.adminUpdate(s.getId(), dto("T2", "  ", points("P1:k1"), 1, 1)));
        assertEquals(400, blankQ.getCode());
        // 更新：目标不存在 → 404
        BusinessException missing = assertThrows(BusinessException.class,
                () -> scenarioService.adminUpdate(99999999L, dto("T", "问题", points("P1:k1"), 1, 1)));
        assertEquals(404, missing.getCode());
    }

    @Test
    @DisplayName("A4.0-6.5: 删除为逻辑删除（物理行保留 deleted=1，@TableLogic 行为层不可见）")
    void adminRemoveIsLogical() {
        LearningScenario s = insertScenario("REMOVE", 1);
        scenarioService.adminRemove(s.getId());

        // 行为层：详情 404、列表不出现（@TableLogic 生效）
        assertThrows(BusinessException.class, () -> scenarioService.adminDetail(s.getId()));
        assertTrue(scenarioService.adminList(null, null).stream()
                .noneMatch(v -> v.getId().equals(s.getId())), "逻辑删除场景不应出现在列表");
        assertNull(scenarioMapper.selectById(s.getId()), "@TableLogic：逻辑删除后 selectById 返回 null");

        // 物理行核对：行仍在库且 deleted=1（绕过 MP 逻辑删除保护直查库）
        Integer physical = jdbcTemplate.queryForObject(
                "select count(*) from learning_scenario where id = ? and deleted = 1",
                Integer.class, s.getId());
        assertEquals(1, physical.intValue(), "物理行应保留且 deleted=1（逻辑删除而非物理删除）");
    }
}
