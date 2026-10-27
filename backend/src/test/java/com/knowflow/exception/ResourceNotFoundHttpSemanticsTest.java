package com.knowflow.exception;

import com.knowflow.entity.CodeChallenge;
import com.knowflow.mapper.CodeChallengeMapper;
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
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-D3：「资源不存在」的 HTTP 语义收敛回归测试（22 处异常构造 / 12 类资源）。
 *
 * <p><b>缺陷背景</b>：这些位置统一用 {@code new BusinessException("…不存在")}（<b>单参</b>）报告资源缺失。
 * 单参构造器默认 {@code code = 500}（见 {@code BusinessException}），而
 * {@code GlobalExceptionHandler#handleBusinessException} 只在 {@code code ∈ [400,500)} 时同步 HTTP 状态码，
 * 500 会回落到 {@code HttpStatus.OK} —— 「资源不存在」因此被表述为 <b>HTTP 200 + body.code 500</b>。
 * 前端 {@code utils/error-message.ts} 据此分类为 {@code server} → 用户看到
 * 「服务暂时不可用 / 稍后重试」，而正确语义是 <b>404 + 「内容不存在或已被删除」</b>（{@code retryable: false}）。</p>
 *
 * <p><b>§十一 硬约束：本类每个 404 用例都<b>同时</b>断言 {@code status().isNotFound()} 与
 * {@code $.code == 404}，缺一不可 —— 只测 body 会让「HTTP 200 + body 404」这种半修复漏网。</b></p>
 *
 * <p><b>同时锁死的边界（防止过度收敛）</b>：</p>
 * <ul>
 *   <li>本人资源仍 200；他人资源仍 <b>403</b>（不得被误改成 404）；匿名仍 401；</li>
 *   <li>正常空结果（列表为空 / 搜索无命中）必须是 200，<b>不得</b>被改成 404；</li>
 *   <li>400 / 405 等既有协议语义不回退。</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 独立内存库：本项目 H2 url 默认固定 mem:knowflow（跨 Spring 上下文共享同库），
// 新增上下文会重跑 schema 导致 CREATE INDEX 重复而失败。本类给自身分配独立库，避免污染共享库。
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1d3_404;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class ResourceNotFoundHttpSemanticsTest {

    /** 资源所有者；JwtAuthenticationFilter 只解析 token 不查库，故普通用户无需预先插入用户行。 */
    private static final Long OWNER = 97611L;
    /** 另一个用户（用于「他人资源仍 403」的回归）。 */
    private static final Long OTHER = 97612L;
    /** 系统管理员（{@code SecurityConfig} 的 {@code /api/admin/**} 角色门禁 + {@code isAdminUser()} 均按 token 角色判定）。 */
    private static final Long ADMIN = 97613L;

    /** 全局不可能存在的资源 ID。 */
    private static final long MISSING_ID = 999999L;

    private static final String JSON = "application/json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private CodeChallengeMapper codeChallengeMapper;

    // ============================ 工具 ============================

    private String auth(Long userId) {
        return "Bearer " + jwtUtils.generateToken(userId, "p1d3-" + userId, "USER");
    }

    private String adminAuth() {
        return "Bearer " + jwtUtils.generateToken(ADMIN, "p1d3-admin", "ADMIN");
    }

    /**
     * §十一 核心断言：<b>HTTP status 与 body.code 必须同时为 404</b>，且保留原业务文案。
     * <p>历史缺陷形态正是「HTTP 200 + body.code 500」，只断言其一无法证明修复。</p>
     */
    private void expect404(ResultActions actions, String message) throws Exception {
        actions.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(message));
    }

    /** 对照：正常成功响应必须是 200 + code 200。 */
    private void expect200(ResultActions actions) throws Exception {
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ============================================================
    // 一、专注会话（FocusSessionController:65/192/206）
    // ============================================================

    @Test
    @DisplayName("P1-D3-1: 专注会话 读/删/结束 均不存在 → HTTP 404 + code 404（原为 HTTP 200 + code 500）")
    void focusSessionNotFoundYields404() throws Exception {
        expect404(mockMvc.perform(get("/api/focus/sessions/" + MISSING_ID).header("Authorization", auth(OWNER))),
                "会话不存在");
        expect404(mockMvc.perform(delete("/api/focus/sessions/" + MISSING_ID).header("Authorization", auth(OWNER))),
                "会话不存在");
        expect404(mockMvc.perform(post("/api/focus/sessions/" + MISSING_ID + "/end")
                        .header("Authorization", auth(OWNER))
                        .contentType(JSON)
                        .content("{\"durationMin\":25}")),
                "会话不存在");
    }

    // ============================================================
    // 二、工作台四模块（WorkbenchServiceImpl:898 requireOwn 通用守卫）
    // ============================================================

    @Test
    @DisplayName("P1-D3-2 核心: 工作台记录不存在 → HTTP 404 + code 404（P2-0 审计实证的原始缺陷点）")
    void workbenchRecordNotFoundYields404() throws Exception {
        expect404(mockMvc.perform(get("/api/workbench/notes/" + MISSING_ID).header("Authorization", auth(OWNER))),
                "记录不存在");
        expect404(mockMvc.perform(get("/api/workbench/captures/" + MISSING_ID).header("Authorization", auth(OWNER))),
                "记录不存在");
        expect404(mockMvc.perform(get("/api/workbench/stories/" + MISSING_ID).header("Authorization", auth(OWNER))),
                "记录不存在");
        expect404(mockMvc.perform(get("/api/workbench/palaces/" + MISSING_ID).header("Authorization", auth(OWNER))),
                "记录不存在");
    }

    // ============================================================
    // 三、用户私有工具类资源（requireOwn 家族）
    // ============================================================

    @Test
    @DisplayName("P1-D3-3 核心: 思维导图不存在 → HTTP 404 + code 404（P2-0 审计实证的原始缺陷点）")
    void mindMapNotFoundYields404() throws Exception {
        expect404(mockMvc.perform(get("/api/mindmaps/" + MISSING_ID).header("Authorization", auth(OWNER))),
                "思维导图不存在");
        expect404(mockMvc.perform(delete("/api/mindmaps/" + MISSING_ID).header("Authorization", auth(OWNER))),
                "思维导图不存在");
    }

    @Test
    @DisplayName("P1-D3-4: 绘图不存在 → HTTP 404 + code 404")
    void drawingNotFoundYields404() throws Exception {
        expect404(mockMvc.perform(get("/api/drawings/" + MISSING_ID).header("Authorization", auth(OWNER))),
                "绘图不存在");
    }

    @Test
    @DisplayName("P1-D3-5: 习惯不存在 → HTTP 404 + code 404")
    void habitNotFoundYields404() throws Exception {
        expect404(mockMvc.perform(get("/api/habits/" + MISSING_ID).header("Authorization", auth(OWNER))),
                "习惯不存在");
    }

    @Test
    @DisplayName("P1-D3-6: 日历纪念日不存在 → HTTP 404 + code 404")
    void calendarMemorialNotFoundYields404() throws Exception {
        expect404(mockMvc.perform(delete("/api/calendar/memorials/" + MISSING_ID)
                        .header("Authorization", auth(OWNER))),
                "纪念日不存在");
    }

    @Test
    @DisplayName("P1-D3-7: 任务 / 清单 / 标签不存在 → HTTP 404 + code 404")
    void taskFamilyNotFoundYields404() throws Exception {
        expect404(mockMvc.perform(put("/api/tasks/" + MISSING_ID)
                        .header("Authorization", auth(OWNER)).contentType(JSON).content("{}")),
                "任务不存在");
        expect404(mockMvc.perform(delete("/api/task-lists/" + MISSING_ID).header("Authorization", auth(OWNER))),
                "清单不存在");
        expect404(mockMvc.perform(delete("/api/task-tags/" + MISSING_ID).header("Authorization", auth(OWNER))),
                "标签不存在");
    }

    // ============================================================
    // 四、编程挑战（CodeChallengeServiceImpl:128/207/212）
    // ============================================================

    @Test
    @DisplayName("P1-D3-8: 挑战不存在 → HTTP 404 + code 404")
    void challengeNotFoundYields404() throws Exception {
        expect404(mockMvc.perform(get("/api/challenges/" + MISSING_ID).header("Authorization", auth(OWNER))),
                "挑战不存在或未发布");
    }

    @Test
    @DisplayName("P1-D3-9: 挑战存在但关卡不存在 → HTTP 404 + code 404（原为 HTTP 200 + code 500）")
    void challengeLevelNotFoundYields404() throws Exception {
        CodeChallenge challenge = new CodeChallenge();
        challenge.setTitle("P1D3-已发布挑战-" + System.nanoTime());
        challenge.setStatus(1);
        codeChallengeMapper.insert(challenge);
        Long challengeId = challenge.getId();

        // 对照：挑战本身存在 → 200（证明 404 只作用于真正缺失的关卡）
        expect200(mockMvc.perform(get("/api/challenges/" + challengeId).header("Authorization", auth(OWNER))));

        expect404(mockMvc.perform(post("/api/challenges/" + challengeId + "/levels/" + MISSING_ID + "/submit")
                        .header("Authorization", auth(OWNER)).contentType(JSON).content("{}")),
                "关卡不存在或未发布");
    }

    // ============================================================
    // 五、错题记录（QuizServiceImpl:309）
    // ============================================================

    @Test
    @DisplayName("P1-D3-10: 错题记录不存在 → HTTP 404 + code 404")
    void mistakeRecordNotFoundYields404() throws Exception {
        expect404(mockMvc.perform(post("/api/quiz/mistake-review")
                        .param("mistakeId", String.valueOf(MISSING_ID))
                        .param("correct", "true")
                        .header("Authorization", auth(OWNER))),
                "错题记录不存在");
    }

    // ============================================================
    // 六、知识库 / 成员 / 父分类（管理端）
    // ============================================================

    @Test
    @DisplayName("P1-D3-11: 知识库不存在（改/导出）→ HTTP 404 + code 404")
    void categoryNotFoundYields404() throws Exception {
        expect404(mockMvc.perform(put("/api/admin/categories/" + MISSING_ID)
                        .header("Authorization", adminAuth()).contentType(JSON)
                        .content("{\"name\":\"P1D3-不存在\",\"parentId\":0}")),
                "知识库不存在");
        expect404(mockMvc.perform(get("/api/admin/categories/" + MISSING_ID + "/export")
                        .header("Authorization", adminAuth())),
                "知识库不存在");
    }

    @Test
    @DisplayName("P1-D3-12: 成员记录不存在（改角色/移除）→ HTTP 404 + code 404")
    void kbMemberRecordNotFoundYields404() throws Exception {
        expect404(mockMvc.perform(put("/api/admin/kb-members/" + MISSING_ID + "/role")
                        .header("Authorization", adminAuth()).contentType(JSON).content("{\"role\":\"READER\"}")),
                "成员记录不存在");
        expect404(mockMvc.perform(delete("/api/admin/kb-members/" + MISSING_ID)
                        .header("Authorization", adminAuth())),
                "成员记录不存在");
    }

    @Test
    @DisplayName("P1-D3-13: 被添加的用户不存在 → HTTP 404 + code 404")
    void addMemberWithMissingUserYields404() throws Exception {
        expect404(mockMvc.perform(post("/api/admin/kb-members")
                        .header("Authorization", adminAuth()).contentType(JSON)
                        .content("{\"categoryId\":1,\"userId\":" + MISSING_ID + ",\"role\":\"READER\"}")),
                "用户不存在");
    }

    @Test
    @DisplayName("P1-D3-14: 指定的父分类不存在 → HTTP 404 + code 404")
    void parentCategoryNotFoundYields404() throws Exception {
        expect404(mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", adminAuth()).contentType(JSON)
                        .content("{\"name\":\"P1D3-子分类\",\"parentId\":" + MISSING_ID + "}")),
                "指定的父分类不存在");
    }

    // ============================================================
    // 七、边界回归：不得过度收敛
    // ============================================================

    @Test
    @DisplayName("P1-D3-15 回归: 本人资源仍 200（404 收敛未误伤存在资源）")
    void existingOwnResourceStillReturns200() throws Exception {
        String body = mockMvc.perform(post("/api/mindmaps")
                        .header("Authorization", auth(OWNER)).contentType(JSON)
                        .content("{\"title\":\"P1D3-本人存在-导图\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("data").asText();

        expect200(mockMvc.perform(get("/api/mindmaps/" + id).header("Authorization", auth(OWNER))));
        expect200(mockMvc.perform(delete("/api/mindmaps/" + id).header("Authorization", auth(OWNER))));
    }

    @Test
    @DisplayName("P1-D3-16 回归: 他人资源仍 403（不得被误改成 404）")
    void foreignResourceStillReturns403() throws Exception {
        String body = mockMvc.perform(post("/api/mindmaps")
                        .header("Authorization", auth(OTHER)).contentType(JSON)
                        .content("{\"title\":\"P1D3-他人资源-导图\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("data").asText();

        mockMvc.perform(get("/api/mindmaps/" + id).header("Authorization", auth(OWNER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该记录"));

        // 清理
        mockMvc.perform(delete("/api/mindmaps/" + id).header("Authorization", auth(OTHER)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("P1-D3-17 回归: 匿名访问受保护资源 → 401（不是 404，也不是 500）")
    void anonymousStillReturns401() throws Exception {
        mockMvc.perform(get("/api/mindmaps/" + MISSING_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/api/focus/sessions/" + MISSING_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("P1-D3-18 回归: 正常空结果（列表为空 / 搜索无命中）必须是 200，不得被改成 404")
    void emptyResultIsNotNotFound() throws Exception {
        // 列表为空属正常业务空结果
        expect200(mockMvc.perform(get("/api/workbench/notes").header("Authorization", auth(OWNER))));
        expect200(mockMvc.perform(get("/api/mindmaps").header("Authorization", auth(OWNER))));
        // 搜索无命中同样不是「资源不存在」
        expect200(mockMvc.perform(get("/api/workbench/notes")
                .param("keyword", "P1D3-NO-SUCH-KEYWORD-" + System.nanoTime())
                .header("Authorization", auth(OWNER))));
    }

    @Test
    @DisplayName("P1-D3-19 回归: 400 / 405 既有协议语义不回退")
    void protocolStatusesUnchanged() throws Exception {
        mockMvc.perform(get("/api/docs/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405));
    }
}
