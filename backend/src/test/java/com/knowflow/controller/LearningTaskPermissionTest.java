package com.knowflow.controller;

import com.knowflow.entity.LearningTask;
import com.knowflow.service.LearningService;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import com.knowflow.vo.LearningTaskVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-C-2：学习任务「越权操作」的 HTTP 语义回归测试。
 *
 * <p><b>缺陷背景</b>：{@code LearningServiceImpl#updateTaskStatus / #deleteTask} 用
 * {@code new BusinessException("无权操作该任务")}（<b>单参</b>）拒绝越权，单参构造器默认
 * {@code code = 500}；而 {@code GlobalExceptionHandler#handleBusinessException} 只在
 * {@code code ∈ [400,500)} 时同步 HTTP 状态码，500 会回落到 {@code HttpStatus.OK} ——
 * 于是越权被表述为 <b>HTTP 200 + body.code 500</b>，前端据此会把它当成「系统内部错误」
 * 或「服务暂时不可用」并提示重试，既不准确也误导用户。</p>
 *
 * <p><b>修复语义</b>（本测试锁死的契约）：</p>
 * <ul>
 *   <li>本人正常操作 → HTTP 200 + body.code 200；</li>
 *   <li>他人越权改状态 / 删除 → <b>HTTP 403 + body.code 403</b>，且数据零副作用；</li>
 *   <li>匿名 → 401；无效 token → 401；</li>
 *   <li>既有 404 语义（任务不存在）不被破坏。</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 独立内存库：本项目 H2 url 默认固定 mem:knowflow（跨上下文共享），新增上下文会重跑 schema
// 导致 CREATE INDEX 重复而失败。本类给自身分配独立库，避免污染共享库。
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1c_task;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class LearningTaskPermissionTest {

    /** 本类专属用户；JwtAuthenticationFilter 只解析 token 不查库，故无需预先插入用户行。 */
    private static final Long OWNER = 97401L;
    private static final Long INTRUDER = 97402L;

    private static final String TASK_TITLE = "P1C2-归属任务";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private LearningService learningService;

    private String token(Long userId) {
        return jwtUtils.generateToken(userId, "p1c-task-" + userId, "USER");
    }

    /** 经真实 HTTP 端点创建任务并返回其 id。 */
    private Long createTaskViaApi(Long ownerId, String title) throws Exception {
        mockMvc.perform(post("/api/learning/tasks")
                        .header("Authorization", "Bearer " + token(ownerId))
                        .contentType("application/json")
                        .content("{\"title\":\"" + title + "\",\"description\":\"p1c2\",\"type\":\"doc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return findTaskId(ownerId, title);
    }

    private Long findTaskId(Long ownerId, String title) {
        return learningService.getTaskList(ownerId).stream()
                .filter(vo -> title.equals(vo.getTitle()))
                .findFirst().orElseThrow(() -> new AssertionError("任务未创建成功：" + title))
                .getId();
    }

    private LearningTaskVO readTask(Long ownerId, Long taskId) {
        List<LearningTaskVO> list = learningService.getTaskList(ownerId);
        return list.stream().filter(vo -> taskId.equals(vo.getId())).findFirst().orElse(null);
    }

    // ============================================================
    // 一、本人操作自己的任务 → 200（能力未被破坏）
    // ============================================================

    @Test
    @DisplayName("P1-C-2-1: 用户操作自己的任务 → HTTP 200 + code 200")
    void ownerCanUpdateOwnTask() throws Exception {
        Long taskId = createTaskViaApi(OWNER, TASK_TITLE + "-本人");

        mockMvc.perform(put("/api/learning/tasks/" + taskId + "/status")
                        .param("status", "1")
                        .header("Authorization", "Bearer " + token(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        LearningTaskVO own = readTask(OWNER, taskId);
        assertEquals(Integer.valueOf(1), own.getStatus(), "本人操作必须真实生效");

        // 本人删除自己的任务同样应成功
        mockMvc.perform(delete("/api/learning/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertNull(readTask(OWNER, taskId), "本人删除必须真实生效");
    }

    // ============================================================
    // 二、核心：越权 → 403（不是 200 + code 500）
    // ============================================================

    @Test
    @DisplayName("P1-C-2-2 核心: 他人改任务状态 → HTTP 403 + body.code 403（原为 200 + 500）")
    void cannotUpdateAnotherUsersTaskWith403() throws Exception {
        LearningTask task = new LearningTask();
        task.setTitle(TASK_TITLE + "-改状态");
        task.setType("doc");
        learningService.createTask(task, OWNER);
        Long taskId = findTaskId(OWNER, TASK_TITLE + "-改状态");

        mockMvc.perform(put("/api/learning/tasks/" + taskId + "/status")
                        .param("status", "1")
                        .header("Authorization", "Bearer " + token(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该任务"));
    }

    @Test
    @DisplayName("P1-C-2-3 核心: 他人删除任务 → HTTP 403 + body.code 403")
    void cannotDeleteAnotherUsersTaskWith403() throws Exception {
        LearningTask task = new LearningTask();
        task.setTitle(TASK_TITLE + "-删除");
        task.setType("doc");
        learningService.createTask(task, OWNER);
        Long taskId = findTaskId(OWNER, TASK_TITLE + "-删除");

        mockMvc.perform(delete("/api/learning/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权删除该任务"));
    }

    // ============================================================
    // 三、越权失败后数据零副作用
    // ============================================================

    @Test
    @DisplayName("P1-C-2-4 核心: 越权失败后数据完全不变（无副作用）")
    void failedIntrusionLeavesNoSideEffect() throws Exception {
        LearningTask task = new LearningTask();
        task.setTitle(TASK_TITLE + "-无副作用");
        task.setDescription("原始描述");
        task.setType("doc");
        learningService.createTask(task, OWNER);
        Long taskId = findTaskId(OWNER, TASK_TITLE + "-无副作用");

        LearningTaskVO before = readTask(OWNER, taskId);

        // 越权改状态 + 越权删除，两者都必须失败且不留痕
        mockMvc.perform(put("/api/learning/tasks/" + taskId + "/status")
                        .param("status", "1")
                        .header("Authorization", "Bearer " + token(INTRUDER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/learning/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token(INTRUDER)))
                .andExpect(status().isForbidden());

        LearningTaskVO after = readTask(OWNER, taskId);
        assertTrue(after != null, "越权删除不得生效，任务必须仍存在");
        assertEquals(before.getStatus(), after.getStatus(), "越权请求不得改写任务状态");
        assertEquals(Integer.valueOf(0), after.getStatus(), "状态应保持初始的待完成");
        assertEquals("原始描述", after.getDescription(), "越权请求不得改写任务内容");

        // 越权者自己的任务列表不得凭空多出他人任务
        assertTrue(learningService.getTaskList(INTRUDER).stream()
                        .noneMatch(vo -> taskId.equals(vo.getId())),
                "越权者不得获得他人任务的任何可读性");

        // 被越权用户名下该任务有且仅有一条
        assertEquals(1L, learningService.getTaskList(OWNER).stream()
                .filter(vo -> taskId.equals(vo.getId())).count(), "任务记录数不得变化");
    }

    // ============================================================
    // 四、匿名 / 无效 token → 401
    // ============================================================

    @Test
    @DisplayName("P1-C-2-5: 匿名改状态 / 删除 → HTTP 401 + code 401")
    void anonymousGets401() throws Exception {
        LearningTask task = new LearningTask();
        task.setTitle(TASK_TITLE + "-匿名");
        task.setType("doc");
        learningService.createTask(task, OWNER);
        Long taskId = findTaskId(OWNER, TASK_TITLE + "-匿名");

        mockMvc.perform(put("/api/learning/tasks/" + taskId + "/status").param("status", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(delete("/api/learning/tasks/" + taskId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        assertEquals(Integer.valueOf(0), readTask(OWNER, taskId).getStatus(),
                "匿名请求不得产生副作用");
    }

    @Test
    @DisplayName("P1-C-2-6: 无效 token → HTTP 401 + code 401")
    void invalidTokenGets401() throws Exception {
        mockMvc.perform(put("/api/learning/tasks/1/status")
                        .param("status", "1")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(delete("/api/learning/tasks/1")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    /** 回归：既有「任务不存在 → 404」语义不因 403 收敛而改变。 */
    @Test
    @DisplayName("P1-C-2-7 回归: 任务不存在仍为 404（不被 403 收敛影响）")
    void notFoundSemanticsPreserved() throws Exception {
        mockMvc.perform(put("/api/learning/tasks/999999/status")
                        .param("status", "1")
                        .header("Authorization", "Bearer " + token(OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("任务不存在"));

        mockMvc.perform(delete("/api/learning/tasks/999999")
                        .header("Authorization", "Bearer " + token(OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    /** 确保测试自身没有把「越权者能读到他人任务」当成理所当然。 */
    @Test
    @DisplayName("P1-C-2-8: 越权者任务列表不含他人任务（用户隔离前置条件）")
    void intruderCannotSeeOwnersTasks() throws Exception {
        LearningTask task = new LearningTask();
        task.setTitle(TASK_TITLE + "-隔离只读");
        task.setType("doc");
        learningService.createTask(task, OWNER);
        Long taskId = findTaskId(OWNER, TASK_TITLE + "-隔离只读");

        assertFalse(learningService.getTaskList(INTRUDER).stream()
                        .anyMatch(vo -> taskId.equals(vo.getId())),
                "越权者不应在列表中看到他人的任务");
    }
}
