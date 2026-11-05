package com.knowflow.controller;

import com.knowflow.learning.controller.ChapterFavoriteController;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.learning.entity.LearningChapter;
import com.knowflow.learning.entity.LearningPath;
import com.knowflow.identity.entity.SysUser;
import com.knowflow.learning.mapper.LearningChapterMapper;
import com.knowflow.learning.mapper.LearningPathMapper;
import com.knowflow.identity.mapper.SysUserMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 章节收藏 toggle 的<b>分层与数据一致性</b>回归测试。
 *
 * <p><b>本次治理前的真实缺陷</b>：{@code ChapterFavoriteController} 直接注入
 * {@code ChapterFavoriteMapper} 并在 Controller 内完成「查询 → 判断 → 写入」的完整业务；
 * 取消收藏走 {@code deleteById}（逻辑删除）后，再次收藏用默认查询（被 {@code @TableLogic}
 * 追加 {@code deleted = 0}）查不到历史行，于是重新 INSERT。
 * 结果是每轮「收藏 → 取消 → 收藏」都新增一条物理行，表内数据随使用时长无限增长。</p>
 *
 * <p><b>修复语义</b>：再次收藏必须<b>复活</b>历史软删行（{@code UPDATE deleted = 0}），
 * 因此单个 {@code (user, chapter)} 组合的物理行数恒为 1，且行主键不变。</p>
 *
 * <p><b>断言口径</b>：同时校验 HTTP status + body.code + 物理行数 + {@code deleted} 标记 +
 * 行主键 + 列表契约。数据库断言一律走 {@link JdbcTemplate} 原生 SQL，
 * 不受 {@code @TableLogic} 过滤影响。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 独立 H2 库名：避免与其它测试类共用 URL 时重复执行 schema.sql
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_chapter_fav;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class ChapterFavoriteToggleTest {

    /** 绝不存在的章节 id，用于 404 语义回归。 */
    private static final long MISSING_CHAPTER_ID = 999999999L;

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private LearningPathMapper pathMapper;

    @Autowired
    private LearningChapterMapper chapterMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userA;

    private Long userB;

    @BeforeEach
    void setUpUsers() {
        userA = createUser();
        userB = createUser();
    }

    // ============================ 造数 / 工具 ============================

    private String auth(Long userId) {
        return "Bearer " + jwtUtils.generateToken(userId, "cfav-" + userId, "USER");
    }

    private Long createUser() {
        String key = UUID.randomUUID().toString().substring(0, 8);
        SysUser user = new SysUser();
        user.setUsername("cfav-" + key);
        user.setEmail("cfav-" + key + "@knowflow.test");
        user.setPassword("");
        user.setNickname("cfav-" + key);
        user.setRole("USER");
        user.setFavoriteCount(0);
        userMapper.insert(user);
        assertNotNull(user.getId(), "测试用户必须落库成功");
        return user.getId();
    }

    /** 建一条公开学习路径（ownerUserId=0 表示平台公开，任何登录用户可读）。 */
    private long newPublicPath() {
        LearningPath path = new LearningPath();
        path.setTitle("章节收藏测试路径");
        path.setDescription("ChapterFavoriteToggleTest 专用");
        path.setLevel("入门");
        path.setChapterCount(0);
        path.setTotalDuration(0);
        path.setEnrolledCount(0);
        path.setSortOrder(0);
        path.setStatus(1);
        path.setOwnerUserId(0L);
        pathMapper.insert(path);
        assertNotNull(path.getId(), "学习路径必须落库成功");
        return path.getId();
    }

    private long newChapter(long pathId, String title) {
        LearningChapter chapter = new LearningChapter();
        chapter.setPathId(pathId);
        chapter.setTitle(title);
        chapter.setContent("章节收藏 toggle 测试正文。");
        chapter.setSortOrder(1);
        chapter.setDuration(10);
        chapterMapper.insert(chapter);
        assertNotNull(chapter.getId(), "章节必须落库成功");
        return chapter.getId();
    }

    /** 执行一次 toggle 并断言 HTTP status 与 body.code（防「HTTP 200 + code 500」假成功）。 */
    private boolean toggle(long chapterId, String token) throws Exception {
        String body = mockMvc.perform(post("/api/learning/chapters/" + chapterId + "/favorite")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).path("data").path("favorited").asBoolean();
    }

    // ============================ 数据库直读（原生 SQL）============================

    /** 物理行数（含 deleted = 1 的残留行）——「一个 user + 一个 chapter 恒为 1 行」的核心断言。 */
    private int rawRowCount(long userId, long chapterId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chapter_favorite WHERE user_id = ? AND chapter_id = ?",
                Integer.class, userId, chapterId);
        return n == null ? 0 : n;
    }

    private int activeRowCount(long userId, long chapterId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chapter_favorite WHERE user_id = ? AND chapter_id = ? AND deleted = 0",
                Integer.class, userId, chapterId);
        return n == null ? 0 : n;
    }

    private Integer deletedFlag(long userId, long chapterId) {
        List<Integer> flags = jdbcTemplate.queryForList(
                "SELECT deleted FROM chapter_favorite WHERE user_id = ? AND chapter_id = ?",
                Integer.class, userId, chapterId);
        return flags.isEmpty() ? null : flags.get(0);
    }

    private Long rowId(long userId, long chapterId) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM chapter_favorite WHERE user_id = ? AND chapter_id = ?",
                Long.class, userId, chapterId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private List<Long> favoriteIds(String token) throws Exception {
        String body = mockMvc.perform(get("/api/learning/chapters/favorites").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Long> ids = new ArrayList<>();
        json.readTree(body).path("data").forEach(node -> ids.add(node.asLong()));
        return ids;
    }

    private boolean isFavorited(long chapterId, String token) throws Exception {
        String body = mockMvc.perform(get("/api/learning/chapters/" + chapterId + "/favorite")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).path("data").path("favorited").asBoolean();
    }

    // ============================================================
    // 一、完整生命周期：全程恰好 1 行，deleted 正确翻转
    // ============================================================

    @Test
    @DisplayName("CFAV-1: 收藏→取消→再收藏→再取消→第三次收藏，全程恰好 1 行且主键不变（修复前每轮新增一行）")
    void fullLifecycleKeepsExactlyOneRow() throws Exception {
        long path = newPublicPath();
        long chapter = newChapter(path, "CFAV-1 生命周期");
        String token = auth(userA);

        // ① 首次收藏 → favorited=true + 1 行 deleted=0
        assertTrue(toggle(chapter, token), "首次调用后应为已收藏");
        assertEquals(1, rawRowCount(userA, chapter), "收藏后必须恰好 1 行");
        assertEquals(1, activeRowCount(userA, chapter), "收藏后必须有 1 行 deleted=0");
        assertEquals(0, deletedFlag(userA, chapter), "收藏后 deleted 必须为 0");
        Long firstRowId = rowId(userA, chapter);
        assertNotNull(firstRowId, "收藏必须落库并生成主键");

        // ② 取消 → favorited=false + 物理行仍在（逻辑删除）
        assertFalse(toggle(chapter, token), "第二次调用后应为未收藏");
        assertEquals(1, rawRowCount(userA, chapter), "取消收藏是逻辑删除，物理行必须仍在");
        assertEquals(0, activeRowCount(userA, chapter), "取消后不得有 deleted=0 的行");
        assertEquals(1, deletedFlag(userA, chapter), "取消后 deleted 必须为 1");
        assertEquals(firstRowId, rowId(userA, chapter), "取消不得改变行主键");

        // ③ 再次收藏 → 修复点：必须复活原行，不能新增第 2 行
        assertTrue(toggle(chapter, token), "第三次调用后应为已收藏");
        assertEquals(1, rawRowCount(userA, chapter), "再次收藏必须复用原行，不得产生第 2 行");
        assertEquals(1, activeRowCount(userA, chapter), "再次收藏必须恰好 1 行 deleted=0");
        assertEquals(0, deletedFlag(userA, chapter), "再次收藏必须把 deleted 置回 0");
        assertEquals(firstRowId, rowId(userA, chapter), "再次收藏必须是「复活原行」（主键不变）");

        // ④⑤ 多轮切换后仍然只有 1 行
        assertFalse(toggle(chapter, token));
        assertTrue(toggle(chapter, token));
        assertEquals(1, rawRowCount(userA, chapter), "多轮切换后仍必须只有 1 行");
        assertEquals(0, deletedFlag(userA, chapter));
        assertEquals(firstRowId, rowId(userA, chapter), "多轮切换不得重建行身份");
    }

    @Test
    @DisplayName("CFAV-2: 预置 deleted=1 残留行（真实损坏态）→ 再次收藏必须 200 并复活原行")
    void recoversFromAlreadyDamagedState() throws Exception {
        long path = newPublicPath();
        long chapter = newChapter(path, "CFAV-2 损坏态恢复");
        // 直接构造「取消收藏后残留」的真实库内状态
        jdbcTemplate.update("INSERT INTO chapter_favorite (user_id, chapter_id, create_time, update_time, deleted) "
                + "VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)", userA, chapter);
        Long damagedRowId = rowId(userA, chapter);
        assertNotNull(damagedRowId, "损坏态前提：必须存在 deleted=1 的残留行");
        assertEquals(1, deletedFlag(userA, chapter), "损坏态前提：deleted 必须为 1");

        assertTrue(toggle(chapter, auth(userA)), "损坏态下再次收藏必须成功");

        assertEquals(1, rawRowCount(userA, chapter), "恢复后仍必须只有 1 行");
        assertEquals(0, deletedFlag(userA, chapter), "恢复后 deleted 必须为 0");
        assertEquals(damagedRowId, rowId(userA, chapter), "必须复活那一条残留行（主键不变）");
    }

    // ============================================================
    // 二、用户隔离
    // ============================================================

    @Test
    @DisplayName("CFAV-3: A、B 收藏同一章节各自成行，互不影响")
    void userIsolationOnSameChapter() throws Exception {
        long path = newPublicPath();
        long chapter = newChapter(path, "CFAV-3 用户隔离");

        assertTrue(toggle(chapter, auth(userA)));
        Long aRowId = rowId(userA, chapter);

        assertTrue(toggle(chapter, auth(userB)));
        assertEquals(1, rawRowCount(userB, chapter), "B 必须生成自己的收藏行");
        assertEquals(0, deletedFlag(userB, chapter));
        assertNotEquals(aRowId, rowId(userB, chapter), "B 不得复用 A 的收藏行");
        assertEquals(aRowId, rowId(userA, chapter), "A 的行主键不受 B 影响");
        assertEquals(0, deletedFlag(userA, chapter), "A 的收藏状态不受 B 影响");

        // B 取消后再收藏 → 只复活 B 自己那行
        assertFalse(toggle(chapter, auth(userB)));
        assertEquals(1, deletedFlag(userB, chapter), "B 取消后自己的行 deleted=1");
        assertEquals(0, deletedFlag(userA, chapter), "A 仍处于已收藏态");
        assertTrue(toggle(chapter, auth(userB)));
        assertEquals(0, deletedFlag(userB, chapter), "B 再次收藏必须复活自己的行");
        assertEquals(1, rawRowCount(userB, chapter), "B 仍只有 1 行");
        assertEquals(aRowId, rowId(userA, chapter), "A 的行始终未被触碰");
    }

    // ============================================================
    // 三、既有语义保持：404 / 401 / 列表契约
    // ============================================================

    @Test
    @DisplayName("CFAV-4: 章节不存在 → 404 + code 404「章节不存在」，且零写入")
    void missingChapterKeeps404AndWritesNothing() throws Exception {
        mockMvc.perform(post("/api/learning/chapters/" + MISSING_CHAPTER_ID + "/favorite")
                        .header("Authorization", auth(userA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("章节不存在"));
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chapter_favorite WHERE user_id = ?", Integer.class, userA);
        assertEquals(0, n == null ? 0 : n, "章节不存在时不得写入任何收藏行");
    }

    @Test
    @DisplayName("CFAV-5: 匿名收藏 → 401 + code 401，且零写入")
    void anonymousToggleKeeps401() throws Exception {
        long path = newPublicPath();
        long chapter = newChapter(path, "CFAV-5 匿名");
        mockMvc.perform(post("/api/learning/chapters/" + chapter + "/favorite"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        assertEquals(0, rawRowCount(userA, chapter), "匿名不得写入收藏行");
    }

    @Test
    @DisplayName("CFAV-6: 详情状态、收藏列表在三轮切换中始终与库内状态一致")
    void detailAndListStayConsistentAcrossToggle() throws Exception {
        long path = newPublicPath();
        long chapter = newChapter(path, "CFAV-6 契约一致");
        String token = auth(userA);

        assertFalse(isFavorited(chapter, token), "初始未收藏");
        assertFalse(favoriteIds(token).contains(chapter), "初始不在收藏列表");

        assertTrue(toggle(chapter, token));
        assertTrue(isFavorited(chapter, token), "收藏后详情必须为 true");
        assertTrue(favoriteIds(token).contains(chapter), "收藏后必须出现在列表");

        assertFalse(toggle(chapter, token));
        assertFalse(isFavorited(chapter, token), "取消后详情必须为 false");
        assertFalse(favoriteIds(token).contains(chapter), "取消后必须从列表消失");

        assertTrue(toggle(chapter, token));
        assertTrue(isFavorited(chapter, token), "再次收藏后详情必须为 true");
        assertTrue(favoriteIds(token).contains(chapter), "再次收藏后必须重新出现在列表");
        assertEquals(0, deletedFlag(userA, chapter));
        assertEquals(1, rawRowCount(userA, chapter));
    }

    @Test
    void concurrentTogglesKeepOnePhysicalRow() throws Exception {
        long chapter = newChapter(newPublicPath(), "concurrent");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        var ready = new java.util.concurrent.CountDownLatch(8);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int index = 0; index < 8; index++) jobs.add(pool.submit(() -> {
                ready.countDown(); start.await(); return toggle(chapter, auth(userA));
            }));
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            for (var job : jobs) job.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1, rawRowCount(userA, chapter));
            assertEquals(0, activeRowCount(userA, chapter));
        } finally { pool.shutdownNow(); }
    }
}
