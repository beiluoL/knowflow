package com.knowflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.LearningChapter;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.LearningPath;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.LearningChapterMapper;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.LearningPathMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-1b：「AI 生成闪卡」写路径的 <b>owner 落库</b> 回归测试。
 *
 * <p><b>缺陷背景</b>（静态确认 + 本类 {@code P0-1b-0} 实证）：{@code learning_flashcard.user_id}
 * 在 H2/MySQL 两套 schema 中均为 {@code BIGINT NOT NULL}，而全仓 7 处写路径中有 <b>4 处</b>
 * 从不写 owner，命中即 {@code NOT NULL} 约束失败：</p>
 * <ol>
 *   <li>{@code DocController#generateFlashcards} —— 已取出 {@code userId} 却未透传，
 *       {@code DocServiceImpl#parseFlashcards} 从不 {@code setUserId}；</li>
 *   <li>{@code AdminLearningController#aiGenerateFlashcards}（路径批量）；
 *       {@code userId} 仅用于 AI 记账，未落到闪卡上；</li>
 *   <li>{@code AdminLearningController#aiGenerateFlashcardsFromKb}（知识库/文档）；同上；</li>
 *   <li>{@code AdminLearningController#addFlashcard}（管理端手建）；
 *       直接 insert 请求体实体，而前端表单不含 {@code userId}。</li>
 * </ol>
 *
 * <p><b>本测试锁死的契约</b>：</p>
 * <ul>
 *   <li>约束前提：无 owner 写入确实抛异常（{@code P0-1b-0}，证明缺陷前提成立而非推测）；</li>
 *   <li>主路径：{@code POST /api/docs/{id}/ai-flashcards} 成功落库，{@code user_id} = 当前登录用户，
 *       且 {@code sourceType=AI_DOC} / {@code docId} 正确（来源徽章与筛选依赖该字段）；</li>
 *   <li>隔离不回归：新生成的卡只在本人 {@code /learning/flashcards} 可见，他人不可见；</li>
 *   <li>管理端两条 AI 路径：{@code user_id} = 当前管理员；</li>
 *   <li>管理端手建：缺省归当前管理员；显式传 {@code userId} 时尊重传入值（不破坏集成用法）；</li>
 *   <li>门禁：匿名 → 401；非管理员访问 {@code /api/admin/**} → 403。</li>
 * </ul>
 *
 * <p>AI 由 {@link FakeAiConfiguration} 脚本化提供，测试不触达外部 LLM。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 与 FlashcardIsolationTest / UserResourceOwnership403Test / KnowledgeBaseMembership403Test
// 使用同一 H2 URL，以便复用同一个 Spring 上下文（新增上下文会重跑 schema 触发建索引冲突）。
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1d2_403;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class FlashcardAiWriteTest {

    /** 普通用户（发起 AI 生成），取高位 id 避开种子用户与其它测试常量。 */
    private static final Long OWNER = 97701L;
    /** 旁观者，用于验证隔离。 */
    private static final Long OTHER = 97702L;
    /** 种子管理员（db/h2/data.sql：admin=1）。 */
    private static final Long ADMIN = 1L;

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private LearningFlashcardMapper flashcardMapper;

    @Autowired
    private DocDocumentMapper docMapper;

    @Autowired
    private LearningPathMapper pathMapper;

    @Autowired
    private LearningChapterMapper chapterMapper;

    @BeforeEach
    void resetAi() {
        FakeAiConfiguration.reset();
    }

    // ============================ 工具 ============================

    private String auth(Long userId) {
        return "Bearer " + jwtUtils.generateToken(userId, "p0-1b-" + userId, "USER");
    }

    private String adminAuth() {
        return "Bearer " + jwtUtils.generateToken(ADMIN, "admin", "ADMIN");
    }

    /** 建一篇已发布文档（AI 闪卡的来源）。 */
    private long newDoc(String title) {
        DocDocument doc = new DocDocument();
        doc.setTitle(title);
        doc.setContent("本文用于 P0-1b 回归测试，内容需足够长以支撑提示词构造。");
        doc.setStatus(1);
        docMapper.insert(doc);
        assertNotNull(doc.getId());
        return doc.getId();
    }

    /** 建一条学习路径 + 一个章节（路径批量生成闪卡的前置条件）。 */
    private long newPathWithChapter(String title) {
        LearningPath path = new LearningPath();
        path.setTitle(title);
        path.setStatus(1);
        path.setOwnerUserId(0L);
        pathMapper.insert(path);

        LearningChapter chapter = new LearningChapter();
        chapter.setPathId(path.getId());
        chapter.setTitle(title + "-章节");
        chapter.setContent("章节正文：用于验证路径批量生成闪卡时 owner 被正确落库。");
        chapter.setSortOrder(1);
        chapterMapper.insert(chapter);

        return path.getId();
    }

    /** 按 front 精确取一行（front 在各用例中唯一）。 */
    private LearningFlashcard findByFront(String front) {
        return flashcardMapper.selectOne(new LambdaQueryWrapper<LearningFlashcard>()
                .eq(LearningFlashcard::getFront, front)
                .last("LIMIT 1"));
    }

    private long countByFront(String front) {
        return flashcardMapper.selectCount(new LambdaQueryWrapper<LearningFlashcard>()
                .eq(LearningFlashcard::getFront, front));
    }

    /** 以 userId 身份读自己可见的闪卡 front 列表。 */
    private List<String> myFronts(Long userId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/learning/flashcards").header("Authorization", auth(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
        List<String> out = new ArrayList<>();
        data.forEach(n -> out.add(n.path("front").asText()));
        return out;
    }

    /** 单元素闪卡 JSON 数组脚本，供 FakeAi 返回。 */
    private String aiJson(String front) {
        return "[{\"front\":\"" + front + "\",\"back\":\"答案\",\"difficulty\":1}]";
    }

    // ============================================================
    // 零、约束前提（证明缺陷真实成立，而非静态推测）
    // ============================================================

    @Test
    @DisplayName("P0-1b-0: 无 owner 写 learning_flashcard 确实被数据库拒绝（user_id NOT NULL 实证）")
    void ownerlessInsertIsRejectedByDatabase() {
        LearningFlashcard orphan = new LearningFlashcard();
        orphan.setFront("P0-1b-0-无主闪卡");
        orphan.setBack("答案");
        orphan.setDifficulty(1);
        orphan.setReviewCount(0);

        Exception e = assertThrows(Exception.class, () -> flashcardMapper.insert(orphan),
                "user_id 为 NOT NULL，无 owner 的写入必须失败（这是 P0-1b 的前提）");
        String msg = String.valueOf(e).toLowerCase();
        assertTrue(msg.contains("null") || msg.contains("integrity"),
                "失败原因应为 NOT NULL / 完整性约束，实际：" + e);
    }

    // ============================================================
    // 一、主路径：POST /api/docs/{id}/ai-flashcards
    // ============================================================

    @Test
    @DisplayName("P0-1b-1 核心: 文档 AI 闪卡成功落库，user_id = 当前登录用户，sourceType=AI_DOC，docId 正确")
    void docAiFlashcardsPersistWithOwner() throws Exception {
        long docId = newDoc("P0-1b-文档A");
        String front = "P0-1b-1-文档AI闪卡";
        FakeAiConfiguration.script(aiJson(front));

        mockMvc.perform(post("/api/docs/" + docId + "/ai-flashcards").header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].front").value(front))
                // 返回体直接回显 owner 与来源，前端来源徽章/筛选依赖 sourceType
                .andExpect(jsonPath("$.data[0].userId").value(OWNER.intValue()))
                .andExpect(jsonPath("$.data[0].sourceType").value("AI_DOC"))
                .andExpect(jsonPath("$.data[0].docId").value((int) docId));

        LearningFlashcard row = findByFront(front);
        assertNotNull(row, "AI 生成的闪卡必须真实落库（修复前此处必然 500）");
        assertEquals(OWNER, row.getUserId(), "生成的闪卡必须归属当前登录用户");
        assertEquals("AI_DOC", row.getSourceType(), "来源必须是 AI_DOC（否则前端会错标为「手动」）");
        assertEquals(docId, row.getDocId(), "必须记录来源文档");
        assertEquals(0, row.getReviewCount() == null ? 0 : row.getReviewCount().intValue());
    }

    @Test
    @DisplayName("P0-1b-2 核心: 新生成的文档 AI 闪卡只在本人列表可见，他人不可见")
    void docAiFlashcardsAreOwnerScoped() throws Exception {
        long docId = newDoc("P0-1b-文档B");
        String front = "P0-1b-2-隔离验证";
        FakeAiConfiguration.script(aiJson(front));

        mockMvc.perform(post("/api/docs/" + docId + "/ai-flashcards").header("Authorization", auth(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertTrue(myFronts(OWNER).contains(front), "生成者应能在自己列表看到该卡");
        assertFalse(myFronts(OTHER).contains(front), "其他用户不得看到该卡（写入不得越权归属）");
    }

    @Test
    @DisplayName("P0-1b-3: 匿名调用文档 AI 闪卡 → 401（不落任何数据）")
    void anonymousDocAiFlashcardsYields401() throws Exception {
        long docId = newDoc("P0-1b-文档C");
        String front = "P0-1b-3-匿名";
        FakeAiConfiguration.script(aiJson(front));

        mockMvc.perform(post("/api/docs/" + docId + "/ai-flashcards"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        assertEquals(0, countByFront(front), "匿名请求不得落库");
    }

    @Test
    @DisplayName("P0-1b-4: AI 返回无效内容 → 不落库（避免写出无主/半成品数据）")
    void invalidAiOutputWritesNothing() throws Exception {
        long docId = newDoc("P0-1b-文档D");
        FakeAiConfiguration.script("{}");

        MvcResult r = mockMvc.perform(post("/api/docs/" + docId + "/ai-flashcards")
                        .header("Authorization", auth(OWNER)))
                .andReturn();

        // 既有语义：BusinessException(String) → code 500（HTTP 200 回落到 body.code）
        JsonNode body = json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(body.path("code").asInt() != 200, "无效 AI 输出不得返回成功，实际 body=" + body);
        assertEquals(0, countByFront("P0-1b-4-无效"), "无效输出不得落库");
    }

    // ============================================================
    // 二、同族：管理端两条 AI 路径
    // ============================================================

    @Test
    @DisplayName("P0-1b-5 核心: 管理端「路径批量生成闪卡」落库 user_id = 管理员，且带 pathId")
    void adminPathAiFlashcardsPersistWithAdminOwner() throws Exception {
        long pathId = newPathWithChapter("P0-1b-路径A");
        String front = "P0-1b-5-路径AI闪卡";
        FakeAiConfiguration.script("[{\"front\":\"" + front + "\",\"back\":\"答案\",\"difficulty\":2,"
                + "\"category\":\"测试分类\"}]");

        mockMvc.perform(post("/api/admin/learning/paths/" + pathId + "/ai-generate-flashcards")
                        .header("Authorization", adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].userId").value(ADMIN.intValue()));

        LearningFlashcard row = findByFront(front);
        assertNotNull(row, "路径批量生成的闪卡必须真实落库（修复前必然 500）");
        assertEquals(ADMIN, row.getUserId(), "管理端生成的闪卡必须归属当前管理员");
        assertEquals(pathId, row.getPathId(), "必须记录归属路径");
    }

    @Test
    @DisplayName("P0-1b-6 核心: 管理端「知识库/文档生成卡片」落库 user_id = 管理员，且带 docId/categoryId")
    void adminKbAiFlashcardsPersistWithAdminOwner() throws Exception {
        long docId = newDoc("P0-1b-文档E");
        String front = "P0-1b-6-知识库AI闪卡";
        FakeAiConfiguration.script("[{\"front\":\"" + front + "\",\"back\":\"答案\",\"difficulty\":1,"
                + "\"category\":\"测试分类\"}]");

        mockMvc.perform(post("/api/admin/learning/flashcards/ai-generate")
                        .header("Authorization", adminAuth())
                        .contentType("application/json")
                        .content("{\"docId\":" + docId + ",\"count\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].userId").value(ADMIN.intValue()));

        LearningFlashcard row = findByFront(front);
        assertNotNull(row, "知识库/文档生成的卡片必须真实落库（修复前必然 500）");
        assertEquals(ADMIN, row.getUserId(), "管理端生成的卡片必须归属当前管理员");
        assertEquals(docId, row.getDocId(), "必须记录来源文档");
        assertEquals("AI_DOC", row.getSourceType(), "来源为文档时应标记 AI_DOC");
    }

    // ============================================================
    // 三、同族：管理端手建闪卡
    // ============================================================

    @Test
    @DisplayName("P0-1b-7 核心: 管理端手建闪卡不带 userId → 缺省归属当前管理员（原为必然 500）")
    void adminManualCreateDefaultsOwnerToAdmin() throws Exception {
        String front = "P0-1b-7-管理端手建";

        mockMvc.perform(post("/api/admin/learning/flashcards")
                        .header("Authorization", adminAuth())
                        .contentType("application/json")
                        .content("{\"front\":\"" + front + "\",\"back\":\"答案\",\"category\":\"测试\",\"difficulty\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(ADMIN));

        LearningFlashcard row = findByFront(front);
        assertNotNull(row, "管理端手建的闪卡必须真实落库");
        assertEquals(ADMIN, row.getUserId(), "缺省 owner 应为当前登录管理员");
    }

    @Test
    @DisplayName("P0-1b-8: 管理端手建显式传 userId → 尊重传入值（不破坏按用户建卡的集成用法）")
    void adminManualCreateHonorsExplicitOwner() throws Exception {
        String front = "P0-1b-8-显式owner";

        mockMvc.perform(post("/api/admin/learning/flashcards")
                        .header("Authorization", adminAuth())
                        .contentType("application/json")
                        .content("{\"front\":\"" + front + "\",\"back\":\"答案\",\"difficulty\":1,\"userId\":" + OTHER + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        LearningFlashcard row = findByFront(front);
        assertNotNull(row);
        assertEquals(OTHER, row.getUserId(), "显式传入的 owner 不应被覆盖");
    }

    // ============================================================
    // 四、门禁不回归
    // ============================================================

    @Test
    @DisplayName("P0-1b-9: 非管理员调用管理端 AI 闪卡端点 → 403（角色门禁未被削弱）")
    void nonAdminCannotUseAdminAiFlashcards() throws Exception {
        long pathId = newPathWithChapter("P0-1b-路径B");

        mockMvc.perform(post("/api/admin/learning/paths/" + pathId + "/ai-generate-flashcards")
                        .header("Authorization", auth(OWNER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/learning/flashcards/ai-generate")
                        .header("Authorization", auth(OWNER))
                        .contentType("application/json")
                        .content("{\"docId\":1,\"count\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P0-1b-10: 文档不存在时仍 404，且不落库（既有语义未被破坏）")
    void nonexistentDocStillYields404() throws Exception {
        FakeAiConfiguration.script(aiJson("P0-1b-10-不该出现"));

        mockMvc.perform(post("/api/docs/999999999/ai-flashcards").header("Authorization", auth(OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        assertEquals(0, countByFront("P0-1b-10-不该出现"), "文档不存在时不得落库");
    }
}
