package com.knowflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.LearningEventType;
import com.knowflow.entity.LearningEvent;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningPath;
import com.knowflow.mapper.LearningEventMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.LearningPathMapper;
import com.knowflow.service.LearningService;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import com.knowflow.vo.FlashcardVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-1：闪卡（{@code learning_flashcard}）用户数据隔离与 IDOR 写越权回归测试。
 *
 * <p><b>缺陷背景</b>（P2-0 全仓审计实证，非推测）：</p>
 * <ol>
 *   <li>{@code SecurityConfig} 把 {@code GET /api/learning/flashcards} 列入 {@code permitAll}；</li>
 *   <li>{@code LearningController#flashcards(pathId, chapterId)} 签名里<b>没有 userId</b>；</li>
 *   <li>{@code LearningServiceImpl#getFlashcardList} 的 {@code LambdaQueryWrapper} 只在
 *       {@code pathId}/{@code chapterId} 非空时加条件 —— 两者皆空时 {@code selectList(空 wrapper)}
 *       <b>返回全表所有用户的闪卡</b>。</li>
 * </ol>
 * <p>后果：匿名可读全站闪卡；登录用户在自己「我的闪卡 = 0」的情况下，闪卡页显示的是<b>别人</b>的卡，
 * 并被 {@code ReviewPlan} / {@code SmartQuiz} / {@code FlashCardsHub} / {@code LearningMode} 消费。</p>
 *
 * <p><b>次生 IDOR 写</b>：{@code POST /api/learning/flashcards/{id}/review} 原先只判「卡存在」不判归属，
 * 任何已登录用户都能改写他人闪卡的 SM-2 状态（reviewCount / interval / easeFactor / nextReviewTime）。</p>
 *
 * <p><b>本测试锁死的契约</b>（同时校验 HTTP status 与 body.code，缺一不可）：</p>
 * <ul>
 *   <li>读取：只能看到<b>自己</b>的卡（Service 层查询恒定带 {@code user_id}）；匿名 → 401；</li>
 *   <li>复习：本人 → 200 且 SRS 真实推进 + 事件照常落库（学习闭环不回归）；他人 → <b>403 + code 403</b>；</li>
 *   <li>越权复习必须<b>零副作用</b>：SM-2 全部字段逐项比对不变，且<b>不产生</b> FLASHCARD_REVIEWED 事件；</li>
 *   <li>详情：{@code /my/flashcards/{id}} 本人 200 / 他人 403；不存在 → 404（不被误写为 403/401）；</li>
 *   <li>回归：{@code pathId} 过滤仍然生效，且过滤结果同样限定在本人范围内。</li>
 * </ul>
 *
 * <p>语义澄清（§五 产品判定）：{@code learning_flashcard.user_id} 为 {@code NOT NULL}，
 * 表注释即「所属用户ID」，全仓不存在 visibility/public 字段 —— 该表按设计就是<b>用户私有闪卡</b>，
 * 没有「公共闪卡池」概念；管理端全站视图由独立的 ADMIN 端点
 * {@code GET /api/admin/learning/flashcards} 承担，因此本测试无需任何「公共卡」分支。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 独立内存库：本项目 H2 url 默认固定 mem:knowflow（跨上下文共享），新增上下文会重跑 schema
// 导致 CREATE INDEX 重复而失败。本属性值与 UserResourceOwnership403Test /
// KnowledgeBaseMembership403Test 完全一致，以便复用同一个 Spring 上下文。
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1d2_403;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class FlashcardIsolationTest {

    /**
     * 两个真实用户 A/B。取高位 id 避开 db/h2/data.sql 种子用户（admin=1 / user1=2 / user2=3）
     * 与其它测试类常量（97511/97512、70001 等）。
     * JwtAuthenticationFilter 只解析 token 不查库，故无需预先插入 sys_user 行。
     */
    private static final Long USER_A = 97601L;
    private static final Long USER_B = 97602L;
    private static final Long USER_C = 97603L;
    private static final Long USER_D = 97604L;

    private static final String MARK_A = "P0-1-A-私有闪卡";
    private static final String MARK_B = "P0-1-B-私有闪卡";

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private LearningService learningService;

    @Autowired
    private LearningFlashcardMapper flashcardMapper;

    @Autowired
    private LearningPathMapper pathMapper;

    @Autowired
    private LearningEventMapper eventMapper;

    // ============================ 工具 ============================

    private String auth(Long userId) {
        return "Bearer " + jwtUtils.generateToken(userId, "p0-1-" + userId, "USER");
    }

    private String adminAuth() {
        return "Bearer " + jwtUtils.generateToken(1L, "admin", "ADMIN");
    }

    /** 以 owner 身份创建一张私有闪卡，返回其主键。 */
    private long createCard(Long owner, String front) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/learning/my/flashcards")
                        .header("Authorization", auth(owner))
                        .contentType("application/json")
                        .content("{\"front\":\"" + front + "\",\"back\":\"答案\",\"difficulty\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
        assertNotNull(data, "创建闪卡必须返回主键");
        return data.path("id").asLong();
    }

    /** 以 userId 身份 GET 一个列表端点，返回 body.data 数组。 */
    private JsonNode listData(String url, Long userId) throws Exception {
        MvcResult r = mockMvc.perform(get(url).header("Authorization", auth(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private List<String> frontsOf(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(n -> out.add(n.path("front").asText()));
        return out;
    }

    private Set<Long> userIdsOf(JsonNode arr) {
        Set<Long> out = new HashSet<>();
        arr.forEach(n -> out.add(n.path("userId").asLong()));
        return out;
    }

    private long countReviewEvents(Long userId, long cardId) {
        return eventMapper.selectCount(new LambdaQueryWrapper<LearningEvent>()
                .eq(LearningEvent::getUserId, userId)
                .eq(LearningEvent::getEventType, LearningEventType.FLASHCARD_REVIEWED.name())
                .eq(LearningEvent::getResourceId, cardId));
    }

    // ============================================================
    // 一、读取隔离（§六 / §十四 1-5）
    // ============================================================

    @Test
    @DisplayName("P0-1-1: 本人读 /learning/flashcards → 200，只含自己的卡")
    void ownerListsOnlyOwnCards() throws Exception {
        long mine = createCard(USER_A, MARK_A + "-本人");

        JsonNode data = listData("/api/learning/flashcards", USER_A);
        List<String> fronts = frontsOf(data);

        assertTrue(fronts.contains(MARK_A + "-本人"), "本人的卡必须出现在自己的列表里");
        // 最强不变式：返回集合里不允许出现任何非本人的 userId
        assertEquals(Set.of(USER_A), userIdsOf(data), "闪卡列表只能包含当前用户的卡");
        assertNotNull(mine);
    }

    @Test
    @DisplayName("P0-1-2 核心: A 的私有闪卡对 B 不可见（双向隔离）")
    void crossUserListIsolationBothWays() throws Exception {
        createCard(USER_A, MARK_A + "-A侧");
        createCard(USER_B, MARK_B + "-B侧");

        List<String> seenByA = frontsOf(listData("/api/learning/flashcards", USER_A));
        List<String> seenByB = frontsOf(listData("/api/learning/flashcards", USER_B));

        assertTrue(seenByA.contains(MARK_A + "-A侧"), "A 应看到自己的卡");
        assertFalse(seenByA.contains(MARK_B + "-B侧"), "A 不得看到 B 的私有卡");

        assertTrue(seenByB.contains(MARK_B + "-B侧"), "B 应看到自己的卡");
        assertFalse(seenByB.contains(MARK_A + "-A侧"), "B 不得看到 A 的私有卡");
    }

    @Test
    @DisplayName("P0-1-3 核心: 匿名读 /learning/flashcards → HTTP 401 + code 401（原为匿名 200 全表）")
    void anonymousListYields401() throws Exception {
        createCard(USER_A, MARK_A + "-匿名");

        mockMvc.perform(get("/api/learning/flashcards"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        // 无效 token 同样 401（不得降级为 500 或匿名可见）
        mockMvc.perform(get("/api/learning/flashcards").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("P0-1-4 核心(§十六 查询层): Service 查询无条件带 user_id —— 种子里他人的卡一张都读不到")
    void serviceQueryIsScopedAtSqlLevel() throws Exception {
        createCard(USER_A, MARK_A + "-查询层");

        // 直接调用 Service（绕过 Controller 的 currentUserId()），证明约束落在数据查询层：
        // 仓库里 data.sql 种子存在 user_id=1（admin）的 6 张卡，若 wrapper 未带 user_id，
        // 这里必然返回它们 —— 因此「非本人 userId 计数为 0」即是对 SQL 条件的直接证明。
        List<FlashcardVO> viaService = learningService.getFlashcardList(USER_A, null, null);

        assertFalse(viaService.isEmpty(), "真实存在的本人卡不应被过滤掉（防止过度修复）");
        for (FlashcardVO vo : viaService) {
            assertEquals(USER_A, vo.getUserId(), "Service 返回的每条闪卡都必须属于当前用户");
        }
    }

    @Test
    @DisplayName("P0-1-5: 越权者调用 Service 也拿不到他人卡（userId 为入参，非前端可控）")
    void serviceQueryForOtherUserNeverLeaksMine() throws Exception {
        createCard(USER_A, MARK_A + "-跨用户查询");

        List<FlashcardVO> asB = learningService.getFlashcardList(USER_B, null, null);
        for (FlashcardVO vo : asB) {
            assertEquals(USER_B, vo.getUserId(), "以 B 身份查询只能得到 B 的卡");
        }
        assertTrue(asB.stream().noneMatch(v -> MARK_A.equals(v.getFront())),
                "以 B 身份查询不得出现 A 的卡");
    }

    // ============================================================
    // 二、review 写越权 / IDOR（§八 / §十四 6-10）
    // ============================================================

    @Test
    @DisplayName("P0-1-6: 本人复习自己的卡 → 200，SRS 真实推进 + 事件照常落库（闭环不回归）")
    void ownerReviewSucceedsAndEmitsEvent() throws Exception {
        long cardId = createCard(USER_A, MARK_A + "-复习本人");
        long eventsBefore = countReviewEvents(USER_A, cardId);

        mockMvc.perform(post("/api/learning/flashcards/" + cardId + "/review?quality=5")
                        .header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        LearningFlashcard after = flashcardMapper.selectById(cardId);
        assertNotNull(after);
        assertEquals(1, after.getReviewCount(), "SM-2：复习后 reviewCount 应推进");
        assertEquals(1, after.getReviewInterval(), "SM-2：复习后 interval 应推进");
        assertNotNull(after.getLastReviewTime(), "SM-2：应写入 lastReviewTime");
        assertNotNull(after.getNextReviewTime(), "SM-2：应写入 nextReviewTime");

        // §十三：安全修复不得打断 LearningEvent → Evidence 链路
        assertEquals(eventsBefore + 1, countReviewEvents(USER_A, cardId),
                "本人复习必须恰好新增 1 条 FLASHCARD_REVIEWED 事件");
    }

    @Test
    @DisplayName("P0-1-7 核心: B 复习 A 的卡 → HTTP 403 + code 403（原为 200 且改写 SRS）且零副作用")
    void crossUserReviewYields403WithZeroSideEffect() throws Exception {
        long cardId = createCard(USER_A, MARK_A + "-越权复习");
        LearningFlashcard before = flashcardMapper.selectById(cardId);
        long eventsBefore = countReviewEvents(USER_B, cardId);

        mockMvc.perform(post("/api/learning/flashcards/" + cardId + "/review?quality=5")
                        .header("Authorization", auth(USER_B)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 零副作用：SM-2 全字段逐项比对
        LearningFlashcard after = flashcardMapper.selectById(cardId);
        assertNotNull(after);
        assertEquals(before.getReviewCount(), after.getReviewCount(), "越权复习不得改动 reviewCount");
        assertEquals(before.getReviewInterval(), after.getReviewInterval(), "越权复习不得改动 interval");
        assertEquals(before.getEaseFactor(), after.getEaseFactor(), "越权复习不得改动 easeFactor");
        assertEquals(before.getLastReviewTime(), after.getLastReviewTime(), "越权复习不得改动 lastReviewTime");
        assertEquals(before.getNextReviewTime(), after.getNextReviewTime(), "越权复习不得改动 nextReviewTime");
        assertEquals(before.getUserId(), after.getUserId(), "归属者不得被改写");

        // 越权失败也不得污染学习事件流（否则 Evidence 会被伪造）
        assertEquals(eventsBefore, countReviewEvents(USER_B, cardId), "越权复习不得产生事件");

        // 归属者视角：这张卡仍然是「未复习」状态
        assertEquals(0, after.getReviewCount() == null ? 0 : after.getReviewCount().intValue(),
                "越权复习后卡片应仍为未复习（reviewCount=0）");
    }

    @Test
    @DisplayName("P0-1-8: 匿名复习（写操作）→ HTTP 401 + code 401")
    void anonymousReviewYields401() throws Exception {
        long cardId = createCard(USER_A, MARK_A + "-匿名复习");

        mockMvc.perform(post("/api/learning/flashcards/" + cardId + "/review?quality=5"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        LearningFlashcard after = flashcardMapper.selectById(cardId);
        assertEquals(0, after.getReviewCount() == null ? 0 : after.getReviewCount().intValue(), "匿名复习零副作用");
    }

    @Test
    @DisplayName("P0-1-9: 复习不存在的卡 → HTTP 404 + code 404（未被误写成 403/401）")
    void reviewNonexistentYields404() throws Exception {
        mockMvc.perform(post("/api/learning/flashcards/999999999/review?quality=5")
                        .header("Authorization", auth(USER_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ============================================================
    // 三、详情端点（§九 / §十四 11-12）
    // ============================================================

    @Test
    @DisplayName("P0-1-10: /my/flashcards/{id} 本人 200 / 他人 403 / 匿名 401 / 不存在 404")
    void myFlashcardDetailIsolation() throws Exception {
        long cardId = createCard(USER_A, MARK_A + "-详情");

        mockMvc.perform(get("/api/learning/my/flashcards/" + cardId).header("Authorization", auth(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.front").value(MARK_A + "-详情"));

        mockMvc.perform(get("/api/learning/my/flashcards/" + cardId).header("Authorization", auth(USER_B)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/learning/my/flashcards/" + cardId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(get("/api/learning/my/flashcards/999999999").header("Authorization", auth(USER_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("P0-1-11: 不存在「GET /learning/flashcards/{id}」裸详情端点（无同族未隔离读取面）")
    void noUnscopedDetailEndpoint() throws Exception {
        long cardId = createCard(USER_A, MARK_A + "-无裸详情");

        // 该路径不存在 → Spring 返回 404/405；关键断言是「绝不返回 200 + 他人卡片内容」
        MvcResult r = mockMvc.perform(get("/api/learning/flashcards/" + cardId)
                        .header("Authorization", auth(USER_B)))
                .andReturn();
        int sc = r.getResponse().getStatus();
        assertTrue(sc == 404 || sc == 405,
                "不应存在未做归属校验的裸详情端点，实际 HTTP=" + sc);

        String body = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains(MARK_A + "-无裸详情"), "响应体不得泄露他人闪卡内容");
    }

    @Test
    @DisplayName("P1-DUE-1: 到期队列只含本人新卡与已到期卡，排除未来卡和他人卡")
    void dueFlashcardsAreScopedAndTimeFiltered() throws Exception {
        createCard(USER_A, MARK_A + "-到期-新卡");
        long due = createCard(USER_A, MARK_A + "-到期-逾期");
        long future = createCard(USER_A, MARK_A + "-到期-未来");
        long other = createCard(USER_B, MARK_B + "-到期-他人");

        LearningFlashcard dueCard = flashcardMapper.selectById(due);
        dueCard.setNextReviewTime(LocalDateTime.now().minusHours(1));
        flashcardMapper.updateById(dueCard);
        LearningFlashcard futureCard = flashcardMapper.selectById(future);
        futureCard.setNextReviewTime(LocalDateTime.now().plusHours(1));
        flashcardMapper.updateById(futureCard);
        LearningFlashcard otherCard = flashcardMapper.selectById(other);
        otherCard.setNextReviewTime(LocalDateTime.now().minusHours(1));
        flashcardMapper.updateById(otherCard);

        JsonNode data = listData("/api/learning/flashcards/due", USER_A);
        List<String> fronts = frontsOf(data);

        assertTrue(fronts.contains(MARK_A + "-到期-新卡"), "未进入复习周期的新卡应进入队列");
        assertTrue(fronts.contains(MARK_A + "-到期-逾期"), "已到期卡应进入队列");
        assertFalse(fronts.contains(MARK_A + "-到期-未来"), "未来到期卡不得提前进入队列");
        assertFalse(fronts.contains(MARK_B + "-到期-他人"), "不得泄漏其他用户闪卡");
        assertTrue(data.size() <= 100, "到期队列最多返回 100 张");
    }

    @Test
    @DisplayName("P1-DUE-2: 空到期队列返回 200 空数组；匿名请求返回 HTTP/body 401")
    void dueFlashcardsEmptyAndAnonymousSemantics() throws Exception {
        mockMvc.perform(get("/api/learning/flashcards/due").header("Authorization", auth(USER_C)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/learning/flashcards/due"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("P1-DUE-3: 超过100张新卡时仍优先保留真正逾期卡")
    void overdueCardsAreNotStarvedByNewCards() throws Exception {
        LearningFlashcard overdue = new LearningFlashcard();
        overdue.setUserId(USER_D);
        overdue.setFront("P1-DUE-逾期优先");
        overdue.setBack("答案");
        overdue.setSourceType("MANUAL");
        overdue.setReviewCount(1);
        overdue.setReviewInterval(1);
        overdue.setNextReviewTime(LocalDateTime.now().minusDays(2));
        flashcardMapper.insert(overdue);
        for (int i = 0; i < 100; i++) {
            LearningFlashcard fresh = new LearningFlashcard();
            fresh.setUserId(USER_D);
            fresh.setFront("P1-DUE-新卡-" + i);
            fresh.setBack("答案");
            fresh.setSourceType("MANUAL");
            fresh.setReviewCount(0);
            fresh.setReviewInterval(0);
            flashcardMapper.insert(fresh);
        }

        JsonNode data = listData("/api/learning/flashcards/due", USER_D);

        assertEquals(100, data.size(), "端点必须严格限制为100张");
        assertTrue(frontsOf(data).contains("P1-DUE-逾期优先"), "逾期卡不能被大量新卡挤出队列");
    }

    // ============================================================
    // 四、回归：pathId 过滤与既有语义（§二十一 不误伤）
    // ============================================================

    @Test
    @DisplayName("P0-1-12: pathId 过滤仍生效，且过滤结果限定在本人范围内")
    void pathFilterStillWorksAndStaysScoped() throws Exception {
        LearningPath path = new LearningPath();
        path.setTitle("P0-1-隔离回归路径");
        path.setStatus(1);
        path.setOwnerUserId(0L);
        pathMapper.insert(path);
        long pathId = path.getId();
        assertTrue(pathId > 0);

        // A 在该路径下建卡；B 在「同一路径」下也建卡
        mockMvc.perform(post("/api/learning/my/flashcards")
                        .header("Authorization", auth(USER_A))
                        .contentType("application/json")
                        .content("{\"front\":\"" + MARK_A + "-路径\",\"back\":\"答案\",\"difficulty\":1,\"pathId\":" + pathId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/learning/my/flashcards")
                        .header("Authorization", auth(USER_B))
                        .contentType("application/json")
                        .content("{\"front\":\"" + MARK_B + "-路径\",\"back\":\"答案\",\"difficulty\":1,\"pathId\":" + pathId + "}"))
                .andExpect(status().isOk());

        JsonNode seenByA = listData("/api/learning/flashcards?pathId=" + pathId, USER_A);
        List<String> fronts = frontsOf(seenByA);
        assertTrue(fronts.contains(MARK_A + "-路径"), "pathId 过滤应保留本人在该路径下的卡");
        assertFalse(fronts.contains(MARK_B + "-路径"), "同一 pathId 下也不得串出他人的卡");
        assertEquals(Set.of(USER_A), userIdsOf(seenByA));
    }

    @Test
    @DisplayName("P0-1-13: 不存在的学习路径仍返回 404（既有语义未被破坏）")
    void nonexistentPathStillYields404() throws Exception {
        mockMvc.perform(get("/api/learning/flashcards?pathId=999999999").header("Authorization", auth(USER_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("P0-1-14: 公开只读端点不受影响（/learning/paths 匿名仍 200）")
    void publicEndpointsUnaffected() throws Exception {
        mockMvc.perform(get("/api/learning/paths"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("P0-1-15: 管理端全站视图走独立 ADMIN 端点（用户端点收敛不影响管理能力）")
    void adminGlobalListStillAvailable() throws Exception {
        // 先造一张属于 USER_A 的卡，作为「管理端能看到他人闪卡」的确定性证据
        // （ADMIN 端点按 create_time 倒序分页，刚建的卡必在第一页）
        createCard(USER_A, MARK_A + "-管理端可见");

        // 用户端点：管理员登录后同样只看自己的卡（证明收敛是「对所有人一致」的）
        JsonNode ownView = listData("/api/learning/flashcards", 1L);
        for (Long uid : userIdsOf(ownView)) {
            assertEquals(1L, uid, "管理员在用户端点同样只应看到自己的卡");
        }

        // 管理端点：全站分页视图，能看见他人闪卡（这是管理能力的正确归属）
        MvcResult r = mockMvc.perform(get("/api/admin/learning/flashcards?pageNum=1&pageSize=50")
                        .header("Authorization", adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode page = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
        assertNotNull(page);
        Set<Long> owners = userIdsOf(page.path("records"));
        assertTrue(owners.contains(USER_A),
                "管理端全站视图必须能看到普通用户的闪卡（否则管理能力被安全修复误伤）");
    }

    @Test
    @DisplayName("P0-1-16: 无权限用户访问 ADMIN 闪卡端点 → 403（角色门禁未被削弱）")
    void nonAdminCannotUseAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/learning/flashcards").header("Authorization", auth(USER_A)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/learning/flashcards"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("P0-1-17: 他人删除我的卡 → 403 且卡仍在")
    void crossUserDeleteIsRejected() throws Exception {
        long cardId = createCard(USER_A, MARK_A + "-删除");

        mockMvc.perform(delete("/api/learning/my/flashcards/" + cardId)
                        .header("Authorization", auth(USER_B)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        assertNotNull(flashcardMapper.selectById(cardId), "越权删除失败后闪卡必须仍然存在");
    }
}
