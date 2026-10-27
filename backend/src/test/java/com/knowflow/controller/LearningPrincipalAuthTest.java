package com.knowflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.LearningPath;
import com.knowflow.entity.LearningTask;
import com.knowflow.service.LearningService;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-A 第二批：{@link LearningController} 的 principal 安全收敛测试
 * （MockMvc 打真实端点 + 真实安全过滤链 + 真实 Service/DB）。
 *
 * <p>缺陷背景：{@code LearningController} 原先有 <b>31 处</b>内联
 * {@code (Long) authentication.getPrincipal()}。对已登录请求该强转成立，
 * 但匿名 principal 是字符串 {@code "anonymousUser"}（配置未 {@code anonymous().disable()}），
 * 强转会抛 {@code ClassCastException} → 落进 {@code GlobalExceptionHandler} 兜底 →
 * <b>HTTP 500「系统内部错误」</b>。</p>
 *
 * <p><b>本批最重要的一处不是「防御」而是「活缺陷」</b>：{@code GET /api/learning/paths/{id}}
 * 被 {@code SecurityConfig} 显式列入 {@code permitAll}（与 {@code /api/learning/paths} 同组），
 * 且 {@code LearningServiceImpl#getPathDetail} 已显式支持 {@code userId == null}
 * （仅 {@code enrolled} 恒为 false）—— 即设计语义就是「匿名可读」，
 * 却因内联强转而对匿名返回 500。故该端点的正确修复是 <b>500 → 200</b>（恢复被破坏的能力），
 * 而不是 401；其余 30 处位于 {@code authenticated()} 之后，语义为「要求登录 → 401」。</p>
 *
 * <p>修复方式：收敛到既有 {@link com.knowflow.common.SecurityUtils}，
 * 复用既有 {@code BusinessException(401, ...)} 业务码机制，不新建鉴权机制。
 * 实现模式与 P1-A 第一批 {@code ChatPrincipalAuthTest} 完全一致。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 独立内存库名：本项目 H2 url 固定 mem:knowflow + DB_CLOSE_DELAY=-1（跨 Spring 上下文共享同库），
// 新增上下文会重跑 schema 导致 CREATE INDEX 重复而失败。本类给自身分配独立库，避免污染共享库。
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1a_learning;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class LearningPrincipalAuthTest {

    /**
     * 本类专属用户，避免与共享库中的种子数据（admin/user1/user2）相互干扰。
     * {@code JwtAuthenticationFilter} 只解析 token 不查库，故无需预先插入用户行。
     */
    private static final Long USER_A = 97201L;
    private static final Long USER_B = 97202L;

    private static final String FORGED_TASK_TITLE = "P1A2-forged-owner-task";
    private static final String ISOLATION_TASK_TITLE = "P1A2-isolation-task";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private LearningService learningService;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenA() {
        return jwtUtils.generateToken(USER_A, "p1a-learning-a", "USER");
    }

    private String tokenB() {
        return jwtUtils.generateToken(USER_B, "p1a-learning-b", "USER");
    }

    /** 造一条已发布（status=1）的公开路径，避免依赖种子数据的具体 id。 */
    private Long newPublishedPath(String title) {
        LearningPath path = new LearningPath();
        path.setTitle(title);
        path.setDescription("P1-A 第二批测试路径");
        path.setLevel("beginner");
        path.setChapterCount(0);
        path.setTotalDuration(0);
        path.setEnrolledCount(0);
        path.setSortOrder(9999);
        path.setStatus(1);
        path.setOwnerUserId(0L);
        learningService.save(path);
        return path.getId();
    }

    /** 经真实 HTTP 端点读取任务列表，解析出 title 列表（用于隔离断言）。 */
    private List<String> taskTitlesViaApi(String token) throws Exception {
        String body = mockMvc.perform(get("/api/learning/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").findValuesAsText("title");
    }

    // ============================================================
    // 一、匿名访问被拒绝（§九-1）
    // ============================================================

    @Test
    @DisplayName("P1-A2-1: 匿名访问受保护读接口 → 401（过滤链语义不变）")
    void anonymousProtectedReadReturns401() throws Exception {
        String[] urls = {
                "/api/learning/tasks",
                "/api/learning/my/flashcards",
                "/api/learning/certificates",
                "/api/learning/personalized-paths",
                "/api/learning/stats/mastery",
                "/api/learning/category-mastery"
        };
        for (String url : urls) {
            mockMvc.perform(get(url))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.message").value("未登录或登录已失效"));
        }
    }

    @Test
    @DisplayName("P1-A2-2: 匿名访问受保护写接口 → 401（不会被表述为 500）")
    void anonymousProtectedWriteReturns401() throws Exception {
        mockMvc.perform(post("/api/learning/paths/1/enroll"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================================================
    // 二、无效 token 被拒绝（§九-3）
    // ============================================================

    @Test
    @DisplayName("P1-A2-3: 无效 token → 401")
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/learning/tasks")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================================================
    // 三、本批核心：permitAll 端点匿名可读 —— 500 → 200（允许匿名，语义与 401 不同）
    // ============================================================

    @Test
    @DisplayName("P1-A2-4 核心: 匿名 GET /api/learning/paths/{id} → 200（原为 500，端点本就 permitAll）")
    void anonymousPathDetailReturns200NotServerError() throws Exception {
        Long pathId = newPublishedPath("P1A2-匿名可读路径");

        mockMvc.perform(get("/api/learning/paths/" + pathId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // 匿名无报名信息 → enrolled 恒为 false（Service 对 userId == null 的既定契约）
                .andExpect(jsonPath("$.data.enrolled").value(false));

        // 对照：同为 permitAll 的列表接口行为不变
        mockMvc.perform(get("/api/learning/paths"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ============================================================
    // 四、有效 token 正常（§九-2）
    // ============================================================

    @Test
    @DisplayName("P1-A2-5: 有效 token 访问读接口 → 200，行为不变")
    void validTokenReadEndpointsWork() throws Exception {
        String[] urls = {
                "/api/learning/tasks",
                "/api/learning/my/flashcards",
                "/api/learning/certificates",
                "/api/learning/personalized-paths",
                "/api/learning/stats/mastery",
                "/api/learning/stats/daily-activity",
                "/api/learning/category-mastery"
        };
        for (String url : urls) {
            mockMvc.perform(get(url).header("Authorization", "Bearer " + tokenA()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ============================================================
    // 五、写操作使用当前登录用户 + 伪造 userId 不生效（§九-5 / §九-6 / §七）
    // ============================================================

    @Test
    @DisplayName("P1-A2-6 核心: 请求体伪造 userId 不生效 —— 归属仍为 token 中的用户")
    void forgedUserIdInBodyIsIgnored() throws Exception {
        // 请求体显式声明 userId = USER_B，但 token 是 USER_A
        String forgedBody = "{\"title\":\"" + FORGED_TASK_TITLE + "\",\"description\":\"x\",\"userId\":" + USER_B + "}";

        mockMvc.perform(post("/api/learning/tasks")
                        .header("Authorization", "Bearer " + tokenA())
                        .contentType("application/json")
                        .content(forgedBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 任务必须落在 USER_A 名下
        assertTrue(taskTitlesViaApi(tokenA()).contains(FORGED_TASK_TITLE),
                "任务应归属于 token 解析出的当前用户");
        // 且绝不出现在 USER_B 名下
        assertFalse(taskTitlesViaApi(tokenB()).contains(FORGED_TASK_TITLE),
                "请求体里的 userId 必须被忽略，不得造成越权归属");
    }

    @Test
    @DisplayName("P1-A2-7 核心: 报名写操作绑定当前登录用户（enrolled 归属正确）")
    void enrollBindsCurrentUser() throws Exception {
        Long pathId = newPublishedPath("P1A2-报名归属路径");

        // 匿名读：未报名
        mockMvc.perform(get("/api/learning/paths/" + pathId))
                .andExpect(jsonPath("$.data.enrolled").value(false));

        // USER_A 报名
        mockMvc.perform(post("/api/learning/paths/" + pathId + "/enroll")
                        .header("Authorization", "Bearer " + tokenA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // USER_A 视角：已报名
        mockMvc.perform(get("/api/learning/paths/" + pathId)
                        .header("Authorization", "Bearer " + tokenA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolled").value(true));

        // 匿名视角：仍未报名（报名状态按当前用户隔离）
        mockMvc.perform(get("/api/learning/paths/" + pathId))
                .andExpect(jsonPath("$.data.enrolled").value(false));
    }

    // ============================================================
    // 六、用户隔离：只能访问自己的学习数据（§九-4 / §七）
    // ============================================================

    @Test
    @DisplayName("P1-A2-8 核心: 用户 B 读不到用户 A 的任务（用户隔离）")
    void taskListIsolatedPerUser() throws Exception {
        LearningTask task = new LearningTask();
        task.setTitle(ISOLATION_TASK_TITLE);
        task.setType("doc");
        learningService.createTask(task, USER_A);

        assertTrue(taskTitlesViaApi(tokenA()).contains(ISOLATION_TASK_TITLE),
                "USER_A 应能读到自己的任务");
        assertFalse(taskTitlesViaApi(tokenB()).contains(ISOLATION_TASK_TITLE),
                "USER_B 不得读到 USER_A 的任务");
    }

    @Test
    @DisplayName("P1-A2-9 核心: 用户 B 不能改用户 A 的任务状态（隔离且无副作用）")
    void cannotModifyAnotherUsersTask() throws Exception {
        LearningTask task = new LearningTask();
        task.setTitle("P1A2-他人任务");
        task.setType("doc");
        learningService.createTask(task, USER_A);
        Long taskId = learningService.getTaskList(USER_A).stream()
                .filter(vo -> "P1A2-他人任务".equals(vo.getTitle()))
                .findFirst().orElseThrow().getId();

        // USER_B 尝试把 USER_A 的任务置为已完成：必须失败，且为 403 语义（P1-C-2 收敛）
        mockMvc.perform(put("/api/learning/tasks/" + taskId + "/status")
                        .param("status", "1")
                        .header("Authorization", "Bearer " + tokenB()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 关键：不得产生任何副作用
        Integer status = learningService.getTaskList(USER_A).stream()
                .filter(vo -> taskId.equals(vo.getId()))
                .findFirst().orElseThrow().getStatus();
        assertEquals(Integer.valueOf(0), status, "越权请求不得改写他人任务状态");

        // 用户 B 也不能删除他人任务
        mockMvc.perform(delete("/api/learning/tasks/" + taskId)
                        .header("Authorization", "Bearer " + tokenB()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        assertEquals(1L, learningService.getTaskList(USER_A).stream()
                        .filter(vo -> taskId.equals(vo.getId())).count(),
                "越权删除不得生效");
    }

    // ============================================================
    // 七、既有业务异常行为保持（§九-7）
    // ============================================================

    @Test
    @DisplayName("P1-A2-10: 既有业务语义保持 —— 路径不存在 404、证书核验 404（匿名）")
    void existingBusinessSemanticsPreserved() throws Exception {
        // 路径不存在：permitAll + Service 抛 404（修复前这里是 500 ClassCastException，现在应正常 404）
        mockMvc.perform(get("/api/learning/paths/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("学习路径不存在"));

        // P1-B 回归：证书核验（permitAll）业务 404 不受本批影响
        mockMvc.perform(get("/api/learning/certificates/verify").param("certNo", "P1A2-NO-SUCH-CERT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    /** 回归：P1-B 的协议异常映射仍生效（缺必需参数 → 400，而非 500）。 */
    @Test
    @DisplayName("P1-A2-11 回归: P1-B 协议异常映射未被破坏（缺参 → 400）")
    void protocolExceptionMappingStillWorks() throws Exception {
        mockMvc.perform(get("/api/learning/certificates/verify").param("code", "ABC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
