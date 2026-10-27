package com.knowflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-Doc-Favorite：文档收藏 toggle 的<b>数据一致性</b>回归测试。
 *
 * <p><b>修复前缺陷</b>（真实 HTTP 已复现，见 {@code .dev-logs/p2-fav/pre-fix-matrix.txt}）：
 * {@code doc_favorite} 带 {@code @TableLogic deleted}，而唯一约束
 * {@code uk_fav_user_doc(user_id, doc_id)} <b>不含 deleted 列</b>。取消收藏只把 {@code deleted}
 * 置 1，物理行继续占用唯一键；再次收藏时 {@code toggleFavorite} 用
 * {@code SELECT ... WHERE deleted = 0} 查不到该行 → 走 {@code INSERT} → 撞唯一键 →
 * {@code DuplicateKeyException} → <b>HTTP 500「系统内部错误」</b>。
 * 更严重的是：该 ({@code user}, {@code doc}) 组合此后<b>永久无法再收藏</b>（每次尝试都 500）。</p>
 *
 * <p><b>修复语义</b>：再次收藏必须<b>复活</b>历史软删行（{@code UPDATE deleted = 0}），
 * 而不是插入新行；因此 {@code COUNT(*) = 1} 贯穿整个生命周期，且行主键不变。</p>
 *
 * <p><b>断言口径</b>：每个核心状态切换同时校验「HTTP status + body.code + 物理行数 +
 * deleted 标记 + 行主键 + 计数」，只判 {@code body.code} 不足以发现
 * 「HTTP 200 + code 200 但库里两行」这类假修复。数据库断言一律走 {@link JdbcTemplate}
 * 原生 SQL（不受 {@code @TableLogic} 过滤影响），与生产 Mapper 解耦。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 独立 H2 库名：避免与其它测试类共用同一 URL 时二次执行 schema.sql 触发 CREATE INDEX 冲突
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p2_fav;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class DocFavoriteToggleTest {

    /** 绝不存在的文档 id，用于 404 语义回归。 */
    private static final long MISSING_DOC_ID = 999999999L;

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private DocDocumentMapper docMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userA;

    private Long userB;

    /** 每个用例使用全新用户，避免计数与收藏行在用例之间互相污染。 */
    @BeforeEach
    void setUpUsers() {
        userA = createUser();
        userB = createUser();
    }

    // ============================ 造数 / 工具 ============================

    private String auth(Long userId) {
        return "Bearer " + jwtUtils.generateToken(userId, "p2fav-" + userId, "USER");
    }

    private Long createUser() {
        String key = UUID.randomUUID().toString().substring(0, 8);
        SysUser u = new SysUser();
        u.setUsername("p2fav-" + key);
        u.setEmail("p2fav-" + key + "@knowflow.test");
        u.setPassword("");
        u.setNickname("p2fav-" + key);
        u.setRole("USER");
        u.setFavoriteCount(0);
        userMapper.insert(u);
        assertNotNull(u.getId(), "测试用户必须落库成功");
        return u.getId();
    }

    private long newDoc(String title) {
        DocDocument doc = new DocDocument();
        doc.setTitle(title);
        doc.setContent("P2-Doc-Favorite 收藏 toggle 数据一致性测试用正文。");
        doc.setStatus(1);
        docMapper.insert(doc);
        assertNotNull(doc.getId(), "文档必须落库成功");
        return doc.getId();
    }

    /** 执行一次 toggle 并断言 HTTP status 与 body.code（成功为 200/200，防「HTTP 200 + code 500」假成功）。 */
    private void toggle(long docId, String token, int expectedHttp, int expectedCode) throws Exception {
        mockMvc.perform(post("/api/docs/" + docId + "/favorite").header("Authorization", token))
                .andExpect(status().is(expectedHttp))
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    // ============================ 数据库直读（原生 SQL，含逻辑删除行）============================

    /** 物理行数（含 {@code deleted = 1} 的残留行）——「一个 user + 一个 doc 恒为 1 行」的核心断言。 */
    private int rawRowCount(long userId, long docId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM doc_favorite WHERE user_id = ? AND doc_id = ?", Integer.class, userId, docId);
        return n == null ? 0 : n;
    }

    /** 是否处于「已收藏」态（{@code deleted = 0}）的行数。 */
    private int activeRowCount(long userId, long docId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM doc_favorite WHERE user_id = ? AND doc_id = ? AND deleted = 0",
                Integer.class, userId, docId);
        return n == null ? 0 : n;
    }

    /** 全表该用户的物理行数（零写入断言用）。 */
    private int rawRowCountByUser(long userId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM doc_favorite WHERE user_id = ?", Integer.class, userId);
        return n == null ? 0 : n;
    }

    /** 该文档全站活跃收藏行数（所有用户）。用于校验「文档 favorite_count == 活跃收藏行数」这一不变量。 */
    private int activeRowCountOfDoc(long docId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM doc_favorite WHERE doc_id = ? AND deleted = 0", Integer.class, docId);
        return n == null ? 0 : n;
    }

    /** deleted 标记；不存在行时返回 null。 */
    private Integer deletedFlag(long userId, long docId) {
        List<Integer> flags = jdbcTemplate.queryForList(
                "SELECT deleted FROM doc_favorite WHERE user_id = ? AND doc_id = ?", Integer.class, userId, docId);
        return flags.isEmpty() ? null : flags.get(0);
    }

    /** 收藏行主键；用于证明「再次收藏是复活原行」而非「新增一行」。 */
    private Long rowId(long userId, long docId) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM doc_favorite WHERE user_id = ? AND doc_id = ?", Long.class, userId, docId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Timestamp createTime(long rowId) {
        return jdbcTemplate.queryForObject(
                "SELECT create_time FROM doc_favorite WHERE id = ?", Timestamp.class, rowId);
    }

    private int docFavoriteCount(long docId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COALESCE(favorite_count, 0) FROM doc_document WHERE id = ?", Integer.class, docId);
        return n == null ? 0 : n;
    }

    private int userFavoriteCount(long userId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COALESCE(favorite_count, 0) FROM sys_user WHERE id = ?", Integer.class, userId);
        return n == null ? 0 : n;
    }

    // ============================ API 侧读取 ============================

    private boolean detailFavorite(long docId, String token) throws Exception {
        String body = mockMvc.perform(get("/api/docs/" + docId).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).path("data").path("favorite").asBoolean();
    }

    private List<Long> favoriteIds(String token) throws Exception {
        String body = mockMvc.perform(get("/api/docs/favorites").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Long> ids = new ArrayList<>();
        json.readTree(body).path("data").forEach(node -> ids.add(node.path("id").asLong()));
        return ids;
    }

    // ============================================================
    // 一、完整生命周期：全程恰好 1 行，deleted 正确翻转
    // ============================================================

    @Test
    @DisplayName("P2FAV-1: 收藏→取消→再收藏→再取消→第三次收藏，全程恰好 1 行且 deleted 正确翻转（修复前第 3 步为 500）")
    void fullLifecycleKeepsExactlyOneRow() throws Exception {
        long doc = newDoc("P2FAV-1 生命周期");
        String token = auth(userA);
        int baseCount = docFavoriteCount(doc);

        // ① 第一次收藏 → 200 + deleted=0 + 1 行
        toggle(doc, token, 200, 200);
        assertEquals(1, rawRowCount(userA, doc), "收藏后必须恰好 1 行");
        assertEquals(1, activeRowCount(userA, doc), "收藏后必须恰好 1 行处于 deleted=0");
        assertEquals(0, deletedFlag(userA, doc), "收藏后 deleted 必须为 0");
        Long rowId = rowId(userA, doc);
        assertNotNull(rowId, "收藏必须落库并生成主键");
        Timestamp createdAt = createTime(rowId);
        assertEquals(baseCount + 1, docFavoriteCount(doc), "文档 favorite_count 必须 +1");
        assertEquals(1, userFavoriteCount(userA), "用户 favorite_count 必须 +1");

        // ② 取消收藏 → 200 + deleted=1 + 物理行仍在（逻辑删除）
        toggle(doc, token, 200, 200);
        assertEquals(1, rawRowCount(userA, doc), "取消收藏是逻辑删除，物理行必须仍在");
        assertEquals(0, activeRowCount(userA, doc), "取消后不得有 deleted=0 的行");
        assertEquals(1, deletedFlag(userA, doc), "取消后 deleted 必须为 1");
        assertEquals(rowId, rowId(userA, doc), "取消不得改变行主键");
        assertEquals(baseCount, docFavoriteCount(doc), "文档 favorite_count 必须回落到原值");
        assertEquals(0, userFavoriteCount(userA), "用户 favorite_count 必须回落为 0");

        // ③ 再次收藏 → 修复点：修复前必为 HTTP 500（DuplicateKeyException / uk_fav_user_doc）
        toggle(doc, token, 200, 200);
        assertEquals(1, rawRowCount(userA, doc), "再次收藏必须复用原行，不得产生第 2 行");
        assertEquals(1, activeRowCount(userA, doc), "再次收藏必须恰好 1 行 deleted=0");
        assertEquals(0, deletedFlag(userA, doc), "再次收藏必须把 deleted 置回 0");
        assertEquals(rowId, rowId(userA, doc), "再次收藏必须是「复活原行」（主键不变），不是 INSERT");
        assertEquals(createdAt, createTime(rowId), "复活保留原 create_time，证明行身份未被重建");
        assertEquals(baseCount + 1, docFavoriteCount(doc), "再次收藏后文档 favorite_count 必须回到 +1");
        assertEquals(1, userFavoriteCount(userA));

        // ④ 再次取消
        toggle(doc, token, 200, 200);
        assertEquals(1, rawRowCount(userA, doc));
        assertEquals(1, deletedFlag(userA, doc));
        assertEquals(rowId, rowId(userA, doc));
        assertEquals(baseCount, docFavoriteCount(doc));

        // ⑤ 第三次收藏
        toggle(doc, token, 200, 200);
        assertEquals(1, rawRowCount(userA, doc), "多轮切换后仍必须只有 1 行");
        assertEquals(0, deletedFlag(userA, doc));
        assertEquals(rowId, rowId(userA, doc));
        assertEquals(baseCount + 1, docFavoriteCount(doc));
    }

    @Test
    @DisplayName("P2FAV-4: 预置 deleted=1 残留行（真实损坏态，如线上 doc16）→ 再次收藏必须 200 并复活原行")
    void recoversFromAlreadyDamagedState() throws Exception {
        long doc = newDoc("P2FAV-4 损坏态恢复");
        // 直接构造「取消收藏后残留」的真实库内状态：物理行存在、deleted=1、唯一键被占死
        jdbcTemplate.update("INSERT INTO doc_favorite (user_id, doc_id, create_time, update_time, deleted) "
                + "VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)", userA, doc);
        Long damagedRowId = rowId(userA, doc);
        assertNotNull(damagedRowId, "损坏态前提：必须存在 deleted=1 的残留行");
        assertEquals(1, deletedFlag(userA, doc), "损坏态前提：deleted 必须为 1");

        // 修复前：该调用 100% 抛 DuplicateKeyException → HTTP 500「系统内部错误」且永久无法恢复
        toggle(doc, auth(userA), 200, 200);

        assertEquals(1, rawRowCount(userA, doc), "恢复后仍必须只有 1 行");
        assertEquals(0, deletedFlag(userA, doc), "恢复后 deleted 必须为 0");
        assertEquals(damagedRowId, rowId(userA, doc), "必须复活那一条残留行（主键不变）");
    }

    // ============================================================
    // 二、用户隔离：不得互相顶替
    // ============================================================

    @Test
    @DisplayName("P2FAV-5: A、B 收藏同一文档各自成行，互不影响；B 的取消/再收藏不触碰 A 的行")
    void userIsolationOnSameDoc() throws Exception {
        long doc = newDoc("P2FAV-5 用户隔离");

        toggle(doc, auth(userA), 200, 200);
        Long aRowId = rowId(userA, doc);

        // B 收藏同一文档 → 必须生成 B 自己的行，且不得复用/覆盖 A 的行
        toggle(doc, auth(userB), 200, 200);
        assertEquals(1, rawRowCount(userB, doc), "B 必须生成自己的收藏行");
        assertEquals(0, deletedFlag(userB, doc));
        assertNotEquals(aRowId, rowId(userB, doc), "B 不得复用 A 的收藏行");
        assertEquals(1, rawRowCount(userA, doc), "A 的行数不受 B 影响");
        assertEquals(0, deletedFlag(userA, doc), "A 的收藏状态不受 B 影响");
        assertEquals(aRowId, rowId(userA, doc), "A 的行主键不受 B 影响");
        assertEquals(2, docFavoriteCount(doc), "该文档应有 2 个活跃收藏");

        // B 取消后再收藏 → 只复活 B 自己那行
        toggle(doc, auth(userB), 200, 200);
        assertEquals(1, deletedFlag(userB, doc), "B 取消后自己的行 deleted=1");
        assertEquals(0, deletedFlag(userA, doc), "A 仍处于已收藏态");
        toggle(doc, auth(userB), 200, 200);
        assertEquals(0, deletedFlag(userB, doc), "B 再次收藏必须复活自己的行");
        assertEquals(1, rawRowCount(userB, doc), "B 仍只有 1 行");
        assertEquals(aRowId, rowId(userA, doc), "A 的行始终未被触碰");
        assertEquals(1, rawRowCount(userA, doc));
    }

    // ============================================================
    // 三、既有语义保持：404 / 401 / 详情与收藏列表契约
    // ============================================================

    @Test
    @DisplayName("P2FAV-7: 文档不存在 → 404 + code 404「文档不存在」，且零写入（不产生 500，也不产生收藏行）")
    void missingDocKeeps404AndWritesNothing() throws Exception {
        mockMvc.perform(post("/api/docs/" + MISSING_DOC_ID + "/favorite").header("Authorization", auth(userA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("文档不存在"));
        assertEquals(0, rawRowCountByUser(userA), "文档不存在时不得写入任何收藏行");
        assertEquals(0, userFavoriteCount(userA), "文档不存在时不得改动用户收藏计数");
    }

    @Test
    @DisplayName("P2FAV-8: 匿名收藏 → 401 + code 401，且零写入（认证语义不被本轮改动破坏）")
    void anonymousToggleKeeps401() throws Exception {
        long doc = newDoc("P2FAV-8 匿名");
        mockMvc.perform(post("/api/docs/" + doc + "/favorite"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        assertEquals(0, rawRowCount(userA, doc), "匿名不得写入收藏行");
        assertEquals(0, docFavoriteCount(doc), "匿名不得改动文档收藏计数");
    }

    @Test
    @DisplayName("P2FAV-9: 详情 favorite 字段与「我的收藏」列表在三轮切换中始终与库内状态一致")
    void detailAndFavoritesListStayConsistentAcrossToggle() throws Exception {
        long doc = newDoc("P2FAV-9 契约一致");
        String token = auth(userA);

        assertFalse(detailFavorite(doc, token), "初始未收藏");
        assertFalse(favoriteIds(token).contains(doc), "初始不在收藏列表");

        toggle(doc, token, 200, 200);
        assertTrue(detailFavorite(doc, token), "收藏后详情 favorite 必须为 true");
        assertTrue(favoriteIds(token).contains(doc), "收藏后必须出现在收藏列表");

        toggle(doc, token, 200, 200);
        assertFalse(detailFavorite(doc, token), "取消后详情 favorite 必须为 false");
        assertFalse(favoriteIds(token).contains(doc), "取消后必须从收藏列表消失");

        // 关键一步：修复前此处 500，详情 favorite 仍为 false 且列表不含该文档
        toggle(doc, token, 200, 200);
        assertTrue(detailFavorite(doc, token), "再次收藏后详情 favorite 必须为 true");
        assertTrue(favoriteIds(token).contains(doc), "再次收藏后必须重新出现在收藏列表");
        assertEquals(0, deletedFlag(userA, doc));
        assertEquals(1, rawRowCount(userA, doc));
    }

    // ============================================================
    // 四、并发：并发重复收藏不得返回 5xx，也不得产生第 2 行 / 重复计数
    // ============================================================

    @Test
    @DisplayName("P2FAV-10: 并发 toggle 同一文档 → 无 5xx、无第 2 行、favorite_count 始终等于活跃收藏行数")
    void concurrentDuplicateFavoriteStaysConsistent() throws Exception {
        long doc = newDoc("P2FAV-10 并发");
        // 先让 B 收藏一次，使文档 favorite_count 处于非 0 状态 —— 这样「并发双删减穿计数」
        // 不会被 GREATEST(0, ...) 的兜底掩盖，不变量才有鉴别力。
        toggle(doc, auth(userB), 200, 200);
        toggle(doc, auth(userA), 200, 200);
        assertEquals(2, activeRowCountOfDoc(doc), "前提：A、B 各有一条活跃收藏");
        assertEquals(2, docFavoriteCount(doc), "前提：计数与活跃行数一致");

        String tokenA = auth(userA);
        for (int round = 0; round < 3; round++) {
            int threads = 2;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < threads; i++) {
                    Callable<Integer> task = () -> {
                        ready.countDown();
                        go.await(5, TimeUnit.SECONDS);
                        return mockMvc.perform(post("/api/docs/" + doc + "/favorite")
                                        .header("Authorization", tokenA))
                                .andReturn().getResponse().getStatus();
                    };
                    futures.add(pool.submit(task));
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS), "并发线程未就绪");
                go.countDown();
                for (Future<Integer> f : futures) {
                    int httpStatus = f.get(30, TimeUnit.SECONDS);
                    assertTrue(httpStatus < 500,
                            "并发重复收藏不得返回 5xx（修复前为 DuplicateKeyException → 500），实际=" + httpStatus);
                }
            } finally {
                pool.shutdownNow();
            }

            // 无论线程如何交错（双向 toggle 的方向本就不确定），两条不变量必须成立：
            assertEquals(1, rawRowCount(userA, doc),
                    "并发后 A 对该文档仍必须恰好 1 行（第 " + (round + 1) + " 轮）");
            assertEquals(activeRowCountOfDoc(doc), docFavoriteCount(doc),
                    "文档 favorite_count 必须恒等于活跃收藏行数（第 " + (round + 1) + " 轮）——"
                            + "并发双删/双增不得把计数减穿或重复累加");
            assertEquals(1, rawRowCount(userB, doc), "B 的行不受 A 的并发操作影响");
        }
    }
}
