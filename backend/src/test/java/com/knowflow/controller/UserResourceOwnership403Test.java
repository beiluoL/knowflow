package com.knowflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.CommunityPost;
import com.knowflow.entity.LearningMistake;
import com.knowflow.entity.SysNotification;
import com.knowflow.mapper.CommunityPostMapper;
import com.knowflow.mapper.LearningMistakeMapper;
import com.knowflow.mapper.SysNotificationMapper;
import com.knowflow.service.CommunityService;
import com.knowflow.service.NotificationService;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-D2：用户私有资源的「越权访问」HTTP 语义回归测试（8 个模块 / 11 处越权点）。
 *
 * <p><b>缺陷背景</b>：这些模块统一用 {@code new BusinessException("无权…")}（<b>单参</b>）拒绝越权。
 * 单参构造器默认 {@code code = 500}（见 {@code BusinessException}），而
 * {@code GlobalExceptionHandler#handleBusinessException} 只在 {@code code ∈ [400,500)} 时同步 HTTP 状态码，
 * 500 会回落到 {@code HttpStatus.OK} —— 越权因此被表述为 <b>HTTP 200 + body.code 500</b>，
 * 前端据此会显示「服务暂时不可用 / 稍后重试」，而正确语义是 <b>403 无访问权限</b>。</p>
 *
 * <p><b>本测试锁死的契约</b>（每个模块均<b>同时</b>校验 HTTP status 与 body.code，缺一不可）：</p>
 * <ul>
 *   <li>本人操作自己的资源 → HTTP 200 + code 200（收敛 403 不得误伤本人）；</li>
 *   <li>他人访问 / 修改 / 删除 → <b>HTTP 403 + code 403</b>，文案保持原业务含义；</li>
 *   <li>越权失败必须零副作用（数据既没被改也没被删）；</li>
 *   <li>匿名 / 无效 token → 401；</li>
 *   <li>既有 404 语义（资源不存在）不被破坏。</li>
 * </ul>
 *
 * <p>覆盖的越权点：{@code MindMapServiceImpl:137}、{@code DrawingServiceImpl:142}、
 * {@code WorkbenchServiceImpl:902}、{@code CalendarMarkServiceImpl:390}、
 * {@code FocusSessionController:68/195/209}、{@code MistakeServiceImpl:64/76}、
 * {@code NotificationServiceImpl:46}、{@code CommunityServiceImpl:139}。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 独立内存库：本项目 H2 url 默认固定 mem:knowflow（跨上下文共享），新增上下文会重跑 schema
// 导致 CREATE INDEX 重复而失败。本类给自身分配独立库，避免污染共享库。
// 注：本属性值必须与 KnowledgeBaseMembership403Test 完全一致，以便复用同一个 Spring 上下文。
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1d2_403;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class UserResourceOwnership403Test {

    /** 资源所有者；JwtAuthenticationFilter 只解析 token 不查库，故无需预先插入用户行。 */
    private static final Long OWNER = 97511L;
    /** 越权者。 */
    private static final Long INTRUDER = 97512L;

    private static final String CAPTURE_TITLE = "P1D2-收集箱-越权";

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private CommunityPostMapper communityPostMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SysNotificationMapper notificationMapper;

    @Autowired
    private LearningMistakeMapper mistakeMapper;

    // ============================ 工具 ============================

    private String token(Long userId) {
        return jwtUtils.generateToken(userId, "p1d2-" + userId, "USER");
    }

    private String auth(Long userId) {
        return "Bearer " + token(userId);
    }

    /** 执行 POST 并返回 body.data 的字符串形式（创建类接口返回主键）。 */
    private String createAndGetId(String url, Long userId, String payload) throws Exception {
        String body = mockMvc.perform(post(url)
                        .header("Authorization", auth(userId))
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = json.readTree(body).get("data");
        assertNotNull(data, "创建接口必须返回主键：" + url);
        return data.asText();
    }

    // ============================================================
    // 一、思维导图（MindMapServiceImpl:137 越权点）
    // ============================================================

    @Test
    @DisplayName("P1-D2-1: 思维导图 本人 CRUD 全通 → 200（收敛 403 未误伤本人）")
    void mindMapOwnerCrudSucceeds() throws Exception {
        String id = createAndGetId("/api/mindmaps", OWNER, "{\"title\":\"P1D2-导图-本人\"}");

        mockMvc.perform(get("/api/mindmaps/" + id).header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("P1D2-导图-本人"));

        mockMvc.perform(put("/api/mindmaps/" + id)
                        .header("Authorization", auth(OWNER))
                        .contentType("application/json")
                        .content("{\"title\":\"P1D2-导图-本人改\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(delete("/api/mindmaps/" + id).header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("P1-D2-2 核心: 思维导图 越权读/改/删 → HTTP 403 + code 403（原为 200 + 500）且零副作用")
    void mindMapIntrusionYields403() throws Exception {
        String id = createAndGetId("/api/mindmaps", OWNER, "{\"title\":\"P1D2-导图-越权\"}");

        mockMvc.perform(get("/api/mindmaps/" + id).header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该记录"));

        mockMvc.perform(put("/api/mindmaps/" + id)
                        .header("Authorization", auth(INTRUDER))
                        .contentType("application/json")
                        .content("{\"title\":\"越权改名\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(delete("/api/mindmaps/" + id).header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 零副作用：标题未被改写、记录仍存在
        mockMvc.perform(get("/api/mindmaps/" + id).header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("P1D2-导图-越权"));
    }

    @Test
    @DisplayName("P1-D2-3: 思维导图 匿名 / 无效 token → HTTP 401 + code 401")
    void mindMapAnonymousYields401() throws Exception {
        String id = createAndGetId("/api/mindmaps", OWNER, "{\"title\":\"P1D2-导图-匿名\"}");

        mockMvc.perform(get("/api/mindmaps/" + id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(delete("/api/mindmaps/" + id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/api/mindmaps/" + id).header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================================================
    // 二、绘图（DrawingServiceImpl:142 越权点）
    // ============================================================

    @Test
    @DisplayName("P1-D2-4 核心: 绘图 越权读/删 → HTTP 403 + code 403 且零副作用")
    void drawingIntrusionYields403() throws Exception {
        String id = createAndGetId("/api/drawings", OWNER, "{\"title\":\"P1D2-绘图-越权\"}");

        mockMvc.perform(get("/api/drawings/" + id).header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("P1D2-绘图-越权"));

        mockMvc.perform(get("/api/drawings/" + id).header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该记录"));

        mockMvc.perform(delete("/api/drawings/" + id).header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/drawings/" + id).header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("P1D2-绘图-越权"));
    }

    // ============================================================
    // 三、工作台收集箱（WorkbenchServiceImpl:902 越权点）
    // ============================================================

    @Test
    @DisplayName("P1-D2-5 核心: 工作台收集箱 越权读 → HTTP 403 + code 403，匿名 → 401")
    void workbenchCaptureIntrusionYields403() throws Exception {
        String id = createAndGetId("/api/workbench/captures", OWNER,
                "{\"title\":\"" + CAPTURE_TITLE + "\",\"content\":\"原始内容\",\"sourceType\":\"MANUAL\"}");

        mockMvc.perform(get("/api/workbench/captures/" + id).header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value(CAPTURE_TITLE));

        mockMvc.perform(get("/api/workbench/captures/" + id).header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该记录"));

        mockMvc.perform(get("/api/workbench/captures/" + id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================================================
    // 四、日历纪念日（CalendarMarkServiceImpl:390 越权点）
    // ============================================================

    @Test
    @DisplayName("P1-D2-6 核心: 日历纪念日 越权改/删 → HTTP 403 + code 403 且零副作用")
    void calendarMemorialIntrusionYields403() throws Exception {
        String id = createAndGetId("/api/calendar/memorials", OWNER,
                "{\"name\":\"P1D2-纪念日\",\"type\":\"yearly\",\"monthDay\":\"01-02\"}");

        mockMvc.perform(put("/api/calendar/memorials/" + id)
                        .header("Authorization", auth(INTRUDER))
                        .contentType("application/json")
                        .content("{\"name\":\"越权改名\",\"type\":\"yearly\",\"monthDay\":\"09-09\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该记录"));

        mockMvc.perform(delete("/api/calendar/memorials/" + id).header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 零副作用：名称未变、记录仍在本人列表中
        String list = mockMvc.perform(get("/api/calendar/memorials").header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(list.contains("\"P1D2-纪念日\""), "越权改名不得生效");
        assertFalse(list.contains("越权改名"), "越权提交的名称不得落库");

        mockMvc.perform(delete("/api/calendar/memorials/" + id).header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ============================================================
    // 五、专注会话（FocusSessionController:68/195/209 越权点）
    // ============================================================

    @Test
    @DisplayName("P1-D2-7 核心: 专注会话 越权查看/结束/删除 → HTTP 403 + code 403 且零副作用")
    void focusSessionIntrusionYields403() throws Exception {
        String startBody = mockMvc.perform(post("/api/focus/sessions/start")
                        .param("mode", "POMODORO")
                        .header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = json.readTree(startBody).get("data").get("id").asText();

        mockMvc.perform(get("/api/focus/sessions/" + id).header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权查看该会话"));

        mockMvc.perform(post("/api/focus/sessions/" + id + "/end")
                        .header("Authorization", auth(INTRUDER))
                        .contentType("application/json")
                        .content("{\"durationMin\":25,\"completedPomodoros\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该会话"));

        mockMvc.perform(delete("/api/focus/sessions/" + id).header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权删除该会话"));

        // 零副作用：会话未被结束（endTime 仍为 null）、时长仍为 0、记录仍在
        String ownBody = mockMvc.perform(get("/api/focus/sessions/" + id).header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode session = json.readTree(ownBody).get("data");
        assertTrue(session.get("endTime") == null || session.get("endTime").isNull(),
                "越权结束不得生效，endTime 必须仍为空");
        assertTrue(session.get("durationMin") == null || session.get("durationMin").asInt() == 0,
                "越权结束不得写入专注时长");
    }

    // ============================================================
    // 六、错题本（MistakeServiceImpl:64/76 越权点）
    // ============================================================

    @Test
    @DisplayName("P1-D2-8 核心: 错题 越权读/标记已掌握 → HTTP 403 + code 403 且零副作用")
    void mistakeIntrusionYields403() throws Exception {
        String question = "P1D2-错题-越权-" + System.nanoTime();
        mockMvc.perform(post("/api/mistakes")
                        .header("Authorization", auth(OWNER))
                        .contentType("application/json")
                        .content("{\"question\":\"" + question + "\",\"wrongAnswer\":\"A\",\"correctAnswer\":\"B\","
                                + "\"category\":\"P1D2\",\"difficulty\":2,\"source\":\"QUIZ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        LearningMistake saved = mistakeMapper.selectOne(new LambdaQueryWrapper<LearningMistake>()
                .eq(LearningMistake::getQuestion, question).last("LIMIT 1"));
        assertNotNull(saved, "错题必须已创建成功");
        String id = String.valueOf(saved.getId());

        mockMvc.perform(get("/api/mistakes/" + id).header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问该错题"));

        mockMvc.perform(put("/api/mistakes/" + id + "/mastered").header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该错题"));

        // 零副作用：本人视角仍为未掌握
        mockMvc.perform(get("/api/mistakes/" + id).header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mastered").value(0));

        // 本人可以标记已掌握（能力未被破坏）
        mockMvc.perform(put("/api/mistakes/" + id + "/mastered").header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("P1-D2-9 回归: 错题不存在仍为 404（不被 403 收敛影响）")
    void mistakeNotFoundSemanticsPreserved() throws Exception {
        mockMvc.perform(get("/api/mistakes/999999").header("Authorization", auth(OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("错题不存在"));
    }

    // ============================================================
    // 七、消息通知（NotificationServiceImpl:46 越权点）
    // ============================================================

    @Test
    @DisplayName("P1-D2-10 核心: 通知 越权标记已读 → HTTP 403 + code 403，本人标记 → 200")
    void notificationIntrusionYields403() throws Exception {
        String title = "P1D2-通知-" + System.nanoTime();
        notificationService.createNotification(OWNER, "system", title, "越权测试", null, null);

        SysNotification saved = notificationMapper.selectOne(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, OWNER)
                .eq(SysNotification::getTitle, title)
                .last("LIMIT 1"));
        assertNotNull(saved, "通知必须已创建成功");
        String id = String.valueOf(saved.getId());

        mockMvc.perform(put("/api/notifications/" + id + "/read").header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该通知"));

        // 零副作用：仍为未读
        assertTrue(notificationMapper.selectById(Long.valueOf(id)).getIsRead() == 0,
                "越权标记不得改写已读状态");

        mockMvc.perform(put("/api/notifications/" + id + "/read").header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertTrue(notificationMapper.selectById(Long.valueOf(id)).getIsRead() == 1,
                "本人标记必须真实生效");
    }

    @Test
    @DisplayName("P1-D2-11 回归: 通知不存在仍为 404（不被 403 收敛影响）")
    void notificationNotFoundSemanticsPreserved() throws Exception {
        mockMvc.perform(put("/api/notifications/999999/read").header("Authorization", auth(OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("通知不存在"));
    }

    // ============================================================
    // 八、社区帖子（CommunityServiceImpl:139 越权点）
    // ============================================================

    @Test
    @DisplayName("P1-D2-12 核心: 社区 越权删帖 → HTTP 403 + code 403 且帖子未被删除")
    void communityPostIntrusionYields403() throws Exception {
        String title = "P1D2-帖子-越权-" + System.nanoTime();
        CommunityPost post = new CommunityPost();
        post.setTitle(title);
        post.setContent("内容");
        post.setCategory("技术");
        communityService.createPost(post, OWNER);

        CommunityPost saved = communityPostMapper.selectOne(new LambdaQueryWrapper<CommunityPost>()
                .eq(CommunityPost::getTitle, title).last("LIMIT 1"));
        assertNotNull(saved, "帖子必须已创建成功");
        String id = String.valueOf(saved.getId());

        mockMvc.perform(delete("/api/community/posts/" + id).header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权删除他人帖子"));

        // 零副作用：帖子仍存在且可读
        assertNotNull(communityPostMapper.selectById(Long.valueOf(id)), "越权删除不得生效");
        mockMvc.perform(get("/api/community/posts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value(title));

        // 匿名删帖 → 401
        mockMvc.perform(delete("/api/community/posts/" + id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        // 本人可以删除
        mockMvc.perform(delete("/api/community/posts/" + id).header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("P1-D2-13 回归: 帖子不存在仍为 404（不被 403 收敛影响）")
    void communityNotFoundSemanticsPreserved() throws Exception {
        mockMvc.perform(delete("/api/community/posts/999999").header("Authorization", auth(OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("帖子不存在"));
    }

    // ============================================================
    // 九、跨模块不变量
    // ============================================================

    @Test
    @DisplayName("P1-D2-14: 越权响应必须同时满足 HTTP 403 与 body.code 403（跨模块一致性）")
    void forbiddenResponsesCarryBothHttpAndBodyCode() throws Exception {
        String mindMapId = createAndGetId("/api/mindmaps", OWNER, "{\"title\":\"P1D2-不变量-导图\"}");
        String drawingId = createAndGetId("/api/drawings", OWNER, "{\"title\":\"P1D2-不变量-绘图\"}");
        String captureId = createAndGetId("/api/workbench/captures", OWNER,
                "{\"title\":\"P1D2-不变量-收集箱\",\"sourceType\":\"MANUAL\"}");

        // 只查 body.code 会漏掉「HTTP 200 + code 403」这类错配，故两者必须同时断言
        mockMvc.perform(get("/api/mindmaps/" + mindMapId).header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/drawings/" + drawingId).header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/api/workbench/captures/" + captureId).header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("P1-D2-15: 越权者列表中始终不含他人资源（用户隔离）")
    void intruderNeverSeesOwnersResources() throws Exception {
        createAndGetId("/api/mindmaps", OWNER, "{\"title\":\"P1D2-隔离-导图\"}");
        createAndGetId("/api/drawings", OWNER, "{\"title\":\"P1D2-隔离-绘图\"}");

        String intruderMindMaps = mockMvc.perform(get("/api/mindmaps").header("Authorization", auth(INTRUDER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(intruderMindMaps.contains("P1D2-隔离-导图"), "越权者不得看到他人思维导图");

        String intruderDrawings = mockMvc.perform(get("/api/drawings").header("Authorization", auth(INTRUDER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(intruderDrawings.contains("P1D2-隔离-绘图"), "越权者不得看到他人绘图");

        String intruderCaptures = mockMvc.perform(get("/api/workbench/captures")
                        .header("Authorization", auth(INTRUDER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(intruderCaptures.contains(CAPTURE_TITLE), "越权者不得看到他人收集箱条目");
    }

    @Test
    @DisplayName("P1-D2-16: 越权失败后资源记录数不变（无隐式删除）")
    void intrusionLeavesRowCountUnchanged() throws Exception {
        String id = createAndGetId("/api/mindmaps", OWNER, "{\"title\":\"P1D2-计数不变\"}");

        int before = json.readTree(mockMvc.perform(get("/api/mindmaps").header("Authorization", auth(OWNER)))
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data").size();

        mockMvc.perform(delete("/api/mindmaps/" + id).header("Authorization", auth(INTRUDER)))
                .andExpect(status().isForbidden());

        int after = json.readTree(mockMvc.perform(get("/api/mindmaps").header("Authorization", auth(OWNER)))
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data").size();
        assertTrue(after == before, "越权删除不得改变记录数");

        mockMvc.perform(get("/api/mindmaps/" + id).header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("P1D2-计数不变"));
    }
}
