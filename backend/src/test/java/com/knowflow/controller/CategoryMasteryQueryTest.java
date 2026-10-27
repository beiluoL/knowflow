package com.knowflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.service.LearningService;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import com.knowflow.vo.CategoryMasteryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-C-1：{@code GET /api/learning/category-mastery} 的查询正确性回归测试。
 *
 * <p><b>缺陷背景</b>：{@code LearningServiceImpl#getCategoryMastery} 的原生 SQL 选取
 * {@code quiz_question.category}，而该列在三份 schema（根 {@code schema.sql} / {@code db/h2} /
 * {@code db/mysql}）中都不存在（真实列为 {@code category_id}，逻辑外键 → {@code doc_category.id}）。
 * SQL 必然抛 {@code BadSqlGrammarException}，又被方法内 {@code catch (Exception)} 吞掉并
 * {@code return List.of()} —— 于是 HTTP 200 + {@code data: []}，前端「分类维度掌握度」恒为空，
 * 系统故障被伪装成「没有数据」。本测试锁死修复后的契约：</p>
 * <ul>
 *   <li>有分类答题数据 → 返回真实分类名与正确率（而不是空数组）；</li>
 *   <li>确实没有答题记录 → 真空数组；</li>
 *   <li>{@code category_id} → {@code doc_category.name} 映射正确；</li>
 *   <li>按正确率升序 + {@code weak}（正确率 &lt; 60）标记；</li>
 *   <li>按 userId 隔离；</li>
 *   <li><b>SQL 故障不再被吞</b> —— 抛异常 / HTTP 500，绝不伪装成 200 + 空数组。</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
// 独立内存库：本项目 H2 url 默认固定 mem:knowflow（跨上下文共享），新增上下文会重跑 schema
// 导致 CREATE INDEX 重复而失败。本类给自身分配独立库，避免污染共享库。
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1c_mastery;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
// 「SQL 故障不再被吞」会破坏表结构，必须最后执行，故显式锁定顺序。
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CategoryMasteryQueryTest {

    /** 本类专属用户，避免与种子数据（admin/user1/user2）相互干扰。 */
    private static final Long USER_MAIN = 97301L;
    private static final Long USER_EMPTY = 97302L;
    private static final Long USER_OTHER = 97303L;

    /** 显式高位 id，避免与 db/h2/data.sql 种子分类（id 1..13）冲突。 */
    private static final Long CAT_A = 90301L;
    private static final Long CAT_B = 90302L;
    private static final Long Q1 = 90311L;
    private static final Long Q2 = 90312L;
    private static final Long Q3 = 90313L;

    private static final String CAT_A_NAME = "P1C分类A";
    private static final String CAT_B_NAME = "P1C分类B";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private LearningService learningService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String token(Long userId) {
        return jwtUtils.generateToken(userId, "p1c-mastery-" + userId, "USER");
    }

    private void insertCategory(Long id, String name) {
        jdbcTemplate.update(
                "INSERT INTO doc_category (id, name, code, parent_id, sort_order, status, deleted) "
                        + "VALUES (?, ?, ?, 0, 1, 1, 0)",
                id, name, "p1c-" + id);
    }

    private void insertQuestion(Long id, Long categoryId) {
        jdbcTemplate.update(
                "INSERT INTO quiz_question (id, title, content, question_type, answer, category_id, "
                        + "difficulty, source, status, sort_order, deleted) "
                        + "VALUES (?, ?, ?, 'SINGLE_CHOICE', '0', ?, 2, 'MANUAL', 1, 0, 0)",
                id, "P1C题目" + id, "P1C题干" + id, categoryId);
    }

    private void insertAnswer(Long userId, Long questionId, int isCorrect) {
        jdbcTemplate.update(
                "INSERT INTO quiz_answer_record (user_id, question_id, user_answer, is_correct, "
                        + "score, time_cost, deleted) VALUES (?, ?, '0', ?, ?, 1, 0)",
                userId, questionId, isCorrect, isCorrect == 1 ? 100 : 0);
    }

    /**
     * 造真实数据：
     * 分类 A（{@link #CAT_A}）3 题记录 1 对 → 正确率 33（weak）
     * 分类 B（{@link #CAT_B}）2 题记录 2 对 → 正确率 100（不 weak）
     */
    private void seedAnswerData() {
        insertCategory(CAT_A, CAT_A_NAME);
        insertCategory(CAT_B, CAT_B_NAME);
        insertQuestion(Q1, CAT_A);
        insertQuestion(Q2, CAT_A);
        insertQuestion(Q3, CAT_B);

        insertAnswer(USER_MAIN, Q1, 1);
        insertAnswer(USER_MAIN, Q1, 0);
        insertAnswer(USER_MAIN, Q2, 0);
        insertAnswer(USER_MAIN, Q3, 1);
        insertAnswer(USER_MAIN, Q3, 1);
    }

    // ============================================================
    // 一、有数据 → 返回真实分类（修复前此处恒为空数组）
    // ============================================================

    @Test
    @Order(1)
    @DisplayName("P1-C-1: 有分类答题记录 → 返回真实分类与正确数（不再是空数组）")
    void returnsRealCategoriesWhenDataExists() {
        seedAnswerData();

        List<CategoryMasteryVO> result = learningService.getCategoryMastery(USER_MAIN);

        assertFalse(result.isEmpty(), "有答题记录时不得返回空数组（原缺陷即在此静默返回空）");
        assertEquals(2, result.size(), "应聚合出 2 个分类");

        CategoryMasteryVO a = result.stream()
                .filter(v -> CAT_A_NAME.equals(v.getCategory())).findFirst().orElseThrow();
        assertEquals(3, a.getTotal(), "分类A 应有 3 条答题记录");
        assertEquals(1, a.getCorrect(), "分类A 应答对 1 条");
        assertEquals(33, a.getRate(), "分类A 正确率应为 33");
        assertTrue(a.isWeak(), "正确率 33 < 60 → 必须标记薄弱");

        CategoryMasteryVO b = result.stream()
                .filter(v -> CAT_B_NAME.equals(v.getCategory())).findFirst().orElseThrow();
        assertEquals(2, b.getTotal(), "分类B 应有 2 条答题记录");
        assertEquals(2, b.getCorrect(), "分类B 应全部答对");
        assertEquals(100, b.getRate(), "分类B 正确率应为 100");
        assertFalse(b.isWeak(), "正确率 100 ≥ 60 → 不应标记薄弱");
    }

    @Test
    @Order(2)
    @DisplayName("P1-C-1: 按正确率升序返回（最薄弱优先）")
    void orderedByRateAscending() {
        List<CategoryMasteryVO> result = learningService.getCategoryMastery(USER_MAIN);

        assertEquals(2, result.size());
        assertEquals(CAT_A_NAME, result.get(0).getCategory(), "33% 的薄弱分类必须排在前面");
        assertEquals(CAT_B_NAME, result.get(1).getCategory(), "100% 的分类排在后面");
    }

    @Test
    @Order(3)
    @DisplayName("P1-C-1: category_id 正确映射为 doc_category.name（不返回 id / null / 空串）")
    void categoryIdResolvedToCategoryName() {
        List<CategoryMasteryVO> result = learningService.getCategoryMastery(USER_MAIN);

        Set<String> names = result.stream().map(CategoryMasteryVO::getCategory).collect(Collectors.toSet());
        assertEquals(Set.of(CAT_A_NAME, CAT_B_NAME), names,
                "必须返回 doc_category.name，而不是 category_id 或 null");
        for (String name : names) {
            assertTrue(name != null && !name.isBlank(), "分类名不得为空");
        }
    }

    // ============================================================
    // 二、确实没有数据 → 真空数组（与被吞掉异常区分开）
    // ============================================================

    @Test
    @Order(4)
    @DisplayName("P1-C-1: 无答题记录 → 真空数组（HTTP 200 + data=[]）")
    void returnsGenuineEmptyArrayWhenNoRecords() throws Exception {
        assertTrue(learningService.getCategoryMastery(USER_EMPTY).isEmpty(),
                "无记录用户应返回空列表");

        String body = mockMvc.perform(get("/api/learning/category-mastery")
                        .header("Authorization", "Bearer " + token(USER_EMPTY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = objectMapper.readTree(body);
        assertTrue(json.path("data").isArray() && json.path("data").isEmpty(),
                "无记录用户应为真空数组，实际响应：" + body);

        // 对照组：同一时刻有记录的用户必须拿到非空数组 —— 证明「空」是真的没有数据，
        // 而不是查询链路整体失效（原缺陷下两者都会是空数组，无法区分）。
        assertFalse(learningService.getCategoryMastery(USER_MAIN).isEmpty(),
                "同一上下文中，有数据的用户必须仍能查到分类，故空数组代表真实无数据");
    }

    // ============================================================
    // 三、按 userId 隔离
    // ============================================================

    @Test
    @Order(5)
    @DisplayName("P1-C-1: 按 userId 隔离 —— 他人答题记录不进入当前用户统计")
    void isolatedByUserId() {
        int before = learningService.getCategoryMastery(USER_MAIN).stream()
                .filter(v -> CAT_A_NAME.equals(v.getCategory())).findFirst().orElseThrow().getTotal();

        // 另一个用户在相同题目上留下记录
        insertAnswer(USER_OTHER, Q1, 1);
        insertAnswer(USER_OTHER, Q1, 1);
        insertAnswer(USER_OTHER, Q2, 1);

        int after = learningService.getCategoryMastery(USER_MAIN).stream()
                .filter(v -> CAT_A_NAME.equals(v.getCategory())).findFirst().orElseThrow().getTotal();

        assertEquals(before, after, "他人答题记录不得计入当前用户的分类统计");
        // 而他人自己能看到自己的 3 条记录
        assertEquals(3, learningService.getCategoryMastery(USER_OTHER).stream()
                .filter(v -> CAT_A_NAME.equals(v.getCategory())).findFirst().orElseThrow().getTotal());
    }

    // ============================================================
    // 四、SQL / 系统故障不得伪装成空数组（本类最后执行）
    // ============================================================

    @Test
    @Order(6)
    @DisplayName("P1-C-1 核心: SQL 故障不再被吞 —— 抛异常 / HTTP 500，绝不返回 200 + []")
    void sqlFailureIsNotSwallowedAsEmptyData() throws Exception {
        // 破坏查询依赖的表，制造真实的 SQL 故障
        jdbcTemplate.execute("DROP TABLE quiz_answer_record");

        // Service 层：必须抛出，而不是 return List.of()
        assertThrows(Exception.class, () -> learningService.getCategoryMastery(USER_MAIN),
                "SQL 故障必须向上抛出，不得被 catch 成空数组");

        // HTTP 层：必须是 500 语义，且响应体不得是「200 + 空数组」
        MockHttpServletResponse response = mockMvc.perform(get("/api/learning/category-mastery")
                        .header("Authorization", "Bearer " + token(USER_MAIN)))
                .andReturn().getResponse();
        assertEquals(500, response.getStatus(),
                "SQL 故障必须映射为 HTTP 500，而不是伪装成 200 的『没有数据』");
        JsonNode errorJson = objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(500, errorJson.path("code").asInt(), "业务码应为 500");
        assertFalse(errorJson.path("data").isArray(),
                "系统故障绝不能被表述为空业务数据");
    }
}
