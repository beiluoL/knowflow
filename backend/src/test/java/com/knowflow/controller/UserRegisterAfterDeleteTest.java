package com.knowflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-User-Register-After-Delete：管理员逻辑删除用户后，原 username / email 必须可以再次注册。
 *
 * <p><b>修复前缺陷</b>（真实 HTTP 已复现，见 {@code .dev-logs/p2-user-reg/pre-fix-matrix.txt}）：
 * {@code sys_user.username} 是列级 {@code UNIQUE}、{@code email} 有 {@code uk_user_email}，
 * 两个唯一键都<b>不含 deleted 列</b>；而 {@code @TableLogic} 会把注册查重的 {@code selectOne}
 * 过滤成空。于是删除只把 {@code deleted} 置 1，历史行继续<b>永久占死</b>这两个键，
 * 再注册时直接 {@code INSERT} 撞唯一索引 → {@code DuplicateKeyException} → <b>HTTP 500「系统内部错误」</b>。
 * 该端点 {@code POST /api/auth/register} 无需鉴权，任何匿名访客都能触发。</p>
 *
 * <p><b>修复语义（不可回退的三条不变量）</b>：</p>
 * <ol>
 *   <li>活跃用户之间 username / email 仍然互斥（DB 唯一索引 + 409 业务错误）；</li>
 *   <li>被删除用户的历史行<b>保留</b>（{@code deleted = 1}），仅<b>交还唯一键槽位</b>；</li>
 *   <li>新注册者一定拿到<b>全新 userId</b>，绝不复活旧行，因而不会继承旧身份的任何业务数据。</li>
 * </ol>
 *
 * <p><b>断言口径</b>：每个用例同时校验「HTTP status + body.code」；数据库断言一律走
 * {@link JdbcTemplate} 原生 SQL（不受 {@code @TableLogic} 过滤影响），避免「HTTP 200 + code 200
 * 但库里两行/键位仍被占用」这类假修复。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 独立 H2 库名：避免与其它测试类共用同一 URL 时二次执行 schema.sql 触发 CREATE INDEX 冲突
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p2_user_reg;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class UserRegisterAfterDeleteTest {

    /** 种子管理员（data.sql 首行）主键，用于生成 ADMIN 令牌。 */
    private static final long SEED_ADMIN_ID = 1L;

    /** 删除时的唯一键归档前缀（与 UserServiceImpl 保持一致，测试独立断言其不变量）。 */
    private static final String ARCHIVE_PREFIX = "#__deleted_user__#";

    private static final String PASSWORD = "Passw0rd123";

    /** 绝不存在的用户 id，用于「删除不存在用户」语义回归。 */
    private static final long MISSING_USER_ID = 999999999L;

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private JdbcTemplate jdbc;

    // ============================ 造数 / 工具 ============================

    private String adminAuth() {
        return "Bearer " + jwtUtils.generateToken(SEED_ADMIN_ID, "admin", "ADMIN");
    }

    private String auth(long userId) {
        return "Bearer " + jwtUtils.generateToken(userId, "p2urd-" + userId, "USER");
    }

    /** 生成本次用例专用的短用户名（3~20 字符，符合 RegisterDTO 校验）。 */
    private String uniqueName() {
        return "p2urd" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String uniqueEmail() {
        return "p2urd-" + UUID.randomUUID().toString().substring(0, 8) + "@knowflow.test";
    }

    /** 注册（匿名）：返回 ResultActions，交由用例断言 status + code。 */
    private ResultActions register(String username, String email, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        if (email != null) {
            body.put("email", email);
        }
        body.put("password", password);
        return mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    /** 注册并断言成功（HTTP 200 + code 200），返回新 userId。 */
    private long registerOk(String username, String email) throws Exception {
        MvcResult result = register(username, email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode node = json.readTree(result.getResponse().getContentAsString());
        long id = node.path("data").path("user").path("id").asLong();
        assertTrue(id > 0, "注册成功必须返回落库后的 userId");
        return id;
    }

    /** 注册并断言失败：HTTP + code 双断言。 */
    private void registerExpecting(int httpStatus, int code, String message, String username, String email)
            throws Exception {
        register(username, email, PASSWORD)
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message));
    }

    /** 管理员逻辑删除用户。 */
    private void deleteUser(long userId) throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", userId).header("Authorization", adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private Map<String, Object> userRow(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, username, email, deleted FROM sys_user WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? -1 : n;
    }

    private int activeCountByUsername(String username) {
        return count("SELECT COUNT(*) FROM sys_user WHERE username = ? AND deleted = 0", username);
    }

    private int activeCountByEmail(String email) {
        return count("SELECT COUNT(*) FROM sys_user WHERE email = ? AND deleted = 0", email);
    }

    /** 断言某行已释放唯一键：username 变成归档值且长度超出注册上限（公开注册无法构造同名）。 */
    private void assertKeysReleased(long userId) {
        Map<String, Object> row = userRow(userId);
        assertNotNull(row, "被删除的历史行必须保留（逻辑删除语义不变）");
        assertEquals(1, ((Number) row.get("deleted")).intValue(), "历史行必须仍是 deleted = 1");
        String username = (String) row.get("username");
        String email = (String) row.get("email");
        assertTrue(username.startsWith(ARCHIVE_PREFIX),
                "被删用户的 username 必须归档释放，实际=" + username);
        assertTrue(username.length() > 20,
                "归档用户名必须超出注册上限 20 字符，实际长度=" + username.length());
        assertTrue(email == null || email.startsWith(ARCHIVE_PREFIX),
                "被删用户的 email 必须归档释放或置 NULL，实际=" + email);
    }

    // ============================ 1. 正常注册 ============================

    @Test
    @DisplayName("正常注册：新 username + 新 email → 200 / code 200，并落库为新用户")
    void registerCreatesNewUser() throws Exception {
        String u = uniqueName();
        String e = uniqueEmail();

        long id = registerOk(u, e);

        Map<String, Object> row = userRow(id);
        assertNotNull(row);
        assertEquals(u, row.get("username"));
        assertEquals(e, row.get("email"));
        assertEquals(0, ((Number) row.get("deleted")).intValue());
        assertEquals(1, activeCountByUsername(u));
    }

    @Test
    @DisplayName("匿名注册仍可用；email 可省略（允许多个 NULL email）")
    void anonymousAndNullEmailRegistration() throws Exception {
        long a = registerOk(uniqueName(), null);
        long b = registerOk(uniqueName(), null);

        assertNotEquals(a, b);
        assertEquals(0, count("SELECT COUNT(*) FROM sys_user WHERE id IN (?, ?) AND email IS NOT NULL", a, b),
                "未传 email 的用户必须落 NULL，不得占用唯一键槽位");
    }

    // ============================ 2~5. 删除后重注册三组合 ============================

    @Test
    @DisplayName("③ 删除后：同 email + 新 username 注册成功，且拿到全新 userId")
    void reRegisterWithSameEmailAfterDelete() throws Exception {
        String u = uniqueName();
        String e = uniqueEmail();
        long deletedId = registerOk(u, e);

        deleteUser(deletedId);

        String newUsername = uniqueName();
        long newId = registerOk(newUsername, e);

        assertNotEquals(deletedId, newId, "新注册必须是全新 userId（禁止复活旧行）");
        assertEquals(1, activeCountByEmail(e), "活跃行中该 email 只能有 1 个");
        assertKeysReleased(deletedId);
        assertEquals(e, userRow(newId).get("email"));
    }

    @Test
    @DisplayName("④ 删除后：同 username + 新 email 注册成功，且拿到全新 userId")
    void reRegisterWithSameUsernameAfterDelete() throws Exception {
        String u = uniqueName();
        String e = uniqueEmail();
        long deletedId = registerOk(u, e);

        deleteUser(deletedId);

        String newEmail = uniqueEmail();
        long newId = registerOk(u, newEmail);

        assertNotEquals(deletedId, newId, "新注册必须是全新 userId（禁止复活旧行）");
        assertEquals(1, activeCountByUsername(u), "活跃行中该 username 只能有 1 个");
        assertKeysReleased(deletedId);
        assertEquals(u, userRow(newId).get("username"));
    }

    @Test
    @DisplayName("⑤ 删除后：同 username + 同 email 注册成功（完全释放）")
    void reRegisterWithSameUsernameAndEmailAfterDelete() throws Exception {
        String u = uniqueName();
        String e = uniqueEmail();
        long deletedId = registerOk(u, e);

        deleteUser(deletedId);

        long newId = registerOk(u, e);

        assertNotEquals(deletedId, newId);
        assertEquals(1, activeCountByUsername(u));
        assertEquals(1, activeCountByEmail(e));
        assertKeysReleased(deletedId);
        assertEquals(u, userRow(newId).get("username"));
        assertEquals(e, userRow(newId).get("email"));
        // 历史行仍在（逻辑删除语义未被破坏）：username = u 的精确匹配只剩新活跃行 1 个
        assertNotNull(userRow(deletedId));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user WHERE username = ?", u));
    }

    // ============================ 6~7. 数据隔离 ============================

    @Test
    @DisplayName("⑦ 新用户拥有全新 userId，且查询不到旧用户的收藏 / 任务 / 通知")
    void newUserDoesNotInheritDeletedUserData() throws Exception {
        String u = uniqueName();
        String e = uniqueEmail();
        long oldId = registerOk(u, e);

        // 真实关联数据：收藏（doc_favorite）、任务（learning_task）、通知（sys_notification）
        String taskMarker = "旧用户任务-" + UUID.randomUUID().toString().substring(0, 6);
        String notifyMarker = "旧用户通知-" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update("INSERT INTO doc_favorite (user_id, doc_id) VALUES (?, ?)", oldId, 1L);
        jdbc.update("INSERT INTO learning_task (user_id, title) VALUES (?, ?)", oldId, taskMarker);
        jdbc.update("INSERT INTO sys_notification (user_id, type, title) VALUES (?, 'SYSTEM', ?)",
                oldId, notifyMarker);

        deleteUser(oldId);
        long newId = registerOk(u, e);

        assertNotEquals(oldId, newId, "必须创建全新 userId");

        // ① 真实 HTTP：新用户看不到旧用户数据（无继承）
        mockMvc.perform(get("/api/docs/favorites").header("Authorization", auth(newId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/api/learning/tasks").header("Authorization", auth(newId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/api/notifications").param("pageNum", "1").param("pageSize", "10")
                        .header("Authorization", auth(newId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isEmpty());

        // ② 直读库：旧数据仍绑定旧 userId，未被迁移、未被复用
        assertEquals(1, count("SELECT COUNT(*) FROM doc_favorite WHERE user_id = ? AND deleted = 0", oldId));
        assertEquals(1, count("SELECT COUNT(*) FROM learning_task WHERE user_id = ? AND deleted = 0 AND title = ?",
                oldId, taskMarker));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_notification WHERE user_id = ? AND deleted = 0 AND title = ?",
                oldId, notifyMarker));
        assertEquals(0, count("SELECT COUNT(*) FROM learning_task WHERE user_id = ?", newId));
    }

    // ============================ 8. 活跃冲突仍必须失败 ============================

    @Test
    @DisplayName("⑧ 活跃用户冲突仍然失败（username → 409，email → 409），唯一性未被削弱")
    void activeUserConflictStillRejected() throws Exception {
        String u = uniqueName();
        String e = uniqueEmail();
        registerOk(u, e);

        registerExpecting(409, 409, "用户名已存在", u, uniqueEmail());
        registerExpecting(409, 409, "邮箱已被注册", uniqueName(), e);

        assertEquals(1, activeCountByUsername(u));
        assertEquals(1, activeCountByEmail(e));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user WHERE username = ? OR email = ?", u, e),
                "两次失败的注册不得在库里留下任何行（只应存在那 1 个活跃用户）");
    }

    // ============================ 9~10. 删除语义回归 ============================

    @Test
    @DisplayName("⑨ 管理员删除不存在用户：保持 404；重复删除同一用户同样 404")
    void deleteMissingUserKeepsSemantics() throws Exception {
        mockMvc.perform(delete("/api/admin/users/{id}", MISSING_USER_ID).header("Authorization", adminAuth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("用户不存在"));

        long id = registerOk(uniqueName(), uniqueEmail());
        deleteUser(id);
        mockMvc.perform(delete("/api/admin/users/{id}", id).header("Authorization", adminAuth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ============================ 11. 历史遗留行（修复前删除） ============================

    @Test
    @DisplayName("⑪ 本轮修复前就已删除的历史行（username/email 仍是原值）不得再阻塞注册")
    void legacyDeletedRowDoesNotBlockRegistration() throws Exception {
        String u = uniqueName();
        String e = uniqueEmail();
        long legacyId = registerOk(u, e);

        // 模拟旧版本删除行为：只置 deleted = 1，不释放唯一键（绕过 service，直接改库）
        jdbc.update("UPDATE sys_user SET deleted = 1 WHERE id = ?", legacyId);

        // 修复前此处必为 HTTP 500（撞 uk_user_email / 唯一约束）
        long newId = registerOk(u, e);

        assertNotEquals(legacyId, newId);
        assertEquals(1, activeCountByUsername(u));
        assertKeysReleased(legacyId);
    }

    // ============================ 12. 资料邮箱更新（同一唯一键） ============================

    @Test
    @DisplayName("⑫ 更换资料邮箱时，历史删除行不得再导致 500；命中活跃行必须 409")
    void updateProfileEmailHandlesDeletedHolder() throws Exception {
        String legacyEmail = uniqueEmail();
        long legacyId = registerOk(uniqueName(), legacyEmail);
        jdbc.update("UPDATE sys_user SET deleted = 1 WHERE id = ?", legacyId);

        long userId = registerOk(uniqueName(), uniqueEmail());
        // 修复前此处必为 HTTP 500（UPDATE 撞 uk_user_email）
        mockMvc.perform(put("/api/user/profile").header("Authorization", auth(userId))
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", legacyEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.email").value(legacyEmail));
        assertKeysReleased(legacyId);
        assertEquals(1, activeCountByEmail(legacyEmail));

        // 命中活跃行 → 409（不能把别人的邮箱抢走）
        String activeEmail = uniqueEmail();
        registerOk(uniqueName(), activeEmail);
        mockMvc.perform(put("/api/user/profile").header("Authorization", auth(userId))
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", activeEmail))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("邮箱已被使用"));
    }

    // ============================ 13. 完整生命周期（三轮） ============================

    @Test
    @DisplayName("⑬ 生命周期：注册 → 删除 → 复用同名注册 → 同名再注册失败 → 再删除 → 再次成功")
    void lifecycleRecyclesUniqueKeys() throws Exception {
        String u = uniqueName();
        String e = uniqueEmail();

        long a = registerOk(u, e);
        deleteUser(a);

        long b = registerOk(u, e);
        assertNotEquals(a, b, "B 必须是全新 userId");

        // B 活跃期间：同名注册必须失败（活跃行仍互斥）
        registerExpecting(409, 409, "用户名已存在", u, uniqueEmail());

        deleteUser(b);
        long c = registerOk(u, uniqueEmail());
        assertNotEquals(b, c, "C 必须是全新 userId");
        assertNotEquals(a, c, "C 不得复用更早的 userId");

        assertEquals(1, activeCountByUsername(u), "任意时刻活跃行最多 1 个");
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user WHERE username = ?", u),
                "该 username 只应被 1 行占用（新活跃行）");
        assertKeysReleased(a);
        assertKeysReleased(b);
        assertNotEquals(userRow(a).get("username"), userRow(b).get("username"),
                "归档值内含各自 id，两个历史行必须互不相同（否则第二行会撞唯一约束）");
    }

    // ============================ 14. 归档前缀碰撞 ============================

    @Test
    @DisplayName("⑭ 合法用户名以内部归档前缀开头：删除后仍释放唯一键并可重新注册")
    void userSuppliedArchivePrefixDoesNotBypassKeyRelease() throws Exception {
        String u = ARCHIVE_PREFIX + "x";
        String e = uniqueEmail();
        long deletedId = registerOk(u, e);

        deleteUser(deletedId);

        long newId = registerOk(u, e);
        assertNotEquals(deletedId, newId, "重新注册必须创建新身份");
        assertKeysReleased(deletedId);
        assertEquals(1, activeCountByUsername(u));
    }

    @Test
    @DisplayName("⑮ 单数字 userId 的完整归档头仍是合法用户名：删除时必须继续归档")
    void exactArchiveHeadAtPublicUsernameLimitIsNotTreatedAsArchived() throws Exception {
        long fixedId = 0L;
        String u = ARCHIVE_PREFIX + fixedId + "#";
        String e = uniqueEmail();
        jdbc.update("INSERT INTO sys_user (id, username, email, password, nickname, role, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, 'USER', 0)",
                fixedId, u, e, "test-only", u);

        deleteUser(fixedId);

        long newId = registerOk(u, e);
        assertNotEquals(fixedId, newId);
        assertKeysReleased(fixedId);
        assertEquals(1, activeCountByUsername(u));
    }

    @Test
    @DisplayName("⑯ 公开注册者预占旧格式归档邮箱时，不得阻止管理员删除目标用户")
    void publicUserCannotPreclaimInternalArchivedEmail() throws Exception {
        String victimName = uniqueName();
        String victimEmail = uniqueEmail();
        long victimId = registerOk(victimName, victimEmail);
        String oldArchiveShape = ARCHIVE_PREFIX + victimId + "#" + victimEmail;
        long attackerId = registerOk(uniqueName(), oldArchiveShape);

        deleteUser(victimId);

        assertKeysReleased(victimId);
        assertEquals(oldArchiveShape, userRow(attackerId).get("email"),
                "删除目标用户不得覆盖或删除预先注册的另一身份");
        assertEquals(0, ((Number) userRow(attackerId).get("deleted")).intValue());
    }

    // ============================ 17. 并发注册 ============================

    @Test
    @DisplayName("⑰ 并发注册同一 username/email：恰好 1 个成功，其余明确业务失败，无 5xx")
    void concurrentRegistrationKeepsSingleActiveUser() throws Exception {
        String u = uniqueName();
        String e = uniqueEmail();
        int threads = 8;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<Integer> task = () -> {
                    start.await();
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("username", u);
                    body.put("email", e);
                    body.put("password", PASSWORD);
                    MvcResult r = mockMvc.perform(post("/api/auth/register")
                                    .contentType(APPLICATION_JSON)
                                    .content(json.writeValueAsString(body)))
                            .andReturn();
                    return r.getResponse().getStatus();
                };
                futures.add(pool.submit(task));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(30, TimeUnit.SECONDS));
            }

            long ok = statuses.stream().filter(s -> s == 200).count();
            long conflict = statuses.stream().filter(s -> s == 409).count();
            assertEquals(1, ok, "并发注册必须只有 1 个成功，实际 statuses=" + statuses);
            assertEquals(threads - 1, conflict, "其余必须返回明确 409，实际 statuses=" + statuses);
            assertTrue(statuses.stream().noneMatch(s -> s >= 500), "不得出现 5xx，实际 statuses=" + statuses);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, activeCountByUsername(u), "库中活跃行只能有 1 个");
        assertEquals(1, activeCountByEmail(e));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_user WHERE username = ? AND deleted = 1", u),
                "并发冲突不得留下 deleted = 1 的半成品行");
    }
}
