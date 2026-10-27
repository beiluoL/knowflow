package com.knowflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.knowflow.entity.SysUser;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 技术英语 P1 词汇训练集成测试（H2 + 真实 HTTP 语义 + 真实 FSRS 调度）。
 *
 * 覆盖：
 * - 首次加入复习创建一张卡，重复加入不重复建卡；
 * - 匿名 401、跨用户隔离；
 * - 首次评分真实持久化、重复 requestId 不重复记分；
 * - 收藏/取消收藏不重置复习状态；
 * - 词条内容修改不删除复习卡与历史；
 * - 队列到期/新词结构与模式可用性标记；
 * - 学习看板只返回真实服务端数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_tech_english_p1;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class TechEnglishReviewApiTest {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private Long userA;
    private Long userB;
    private Long aqsId;
    private Long wordId;

    @BeforeEach
    void setUp() throws Exception {
        userA = createUser("USER");
        userB = createUser("USER");
        aqsId = searchFirstId("AQS");
        wordId = searchFirstId("Polymorphism");
    }

    private Long createUser(String role) {
        String key = UUID.randomUUID().toString().substring(0, 8);
        SysUser u = new SysUser();
        u.setUsername("tech-p1-" + key);
        u.setEmail("tech-p1-" + key + "@knowflow.test");
        u.setPassword("");
        u.setNickname("tech-p1-" + key);
        u.setRole(role);
        u.setFavoriteCount(0);
        userMapper.insert(u);
        assertNotNull(u.getId(), "测试用户必须落库成功");
        return u.getId();
    }

    private String auth(Long userId) {
        return "Bearer " + jwtUtils.generateToken(userId, "tech-p1-" + userId, "USER");
    }

    private Long searchFirstId(String headword) throws Exception {
        String body = mockMvc.perform(get("/api/tech-english/terms").param("q", headword))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode records = json.readTree(body).path("data").path("records");
        assertTrue(records.size() > 0, "种子必须包含 " + headword);
        return records.get(0).path("id").asLong();
    }

    private JsonNode postJson(String path, Long userId, String payload) throws Exception {
        String body = mockMvc.perform(post(path)
                        .header("Authorization", auth(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).path("data");
    }

    private long cardCount(Long userId, Long termId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_vocab_state s JOIN learning_flashcard c"
                        + " ON c.id=s.linked_flashcard_id AND c.deleted=0"
                        + " WHERE s.user_id=? AND s.term_id=? AND s.deleted=0",
                Long.class, userId, termId);
        return count == null ? 0 : count;
    }

    /* ============================ 一、加入复习幂等 ============================ */

    @Test
    @DisplayName("TE-P1-1: 首次加入创建一张复习卡，重复加入复用同一张且不重复建卡")
    void addToReviewIsIdempotent() throws Exception {
        JsonNode first = postJson("/api/tech-english/me/terms/" + aqsId + "/review-card", userA, "{}");
        long cardId = first.path("cardId").asLong();
        assertTrue(first.path("created").asBoolean(), "首次加入必须新建复习卡");
        assertTrue(cardId > 0);

        JsonNode second = postJson("/api/tech-english/me/terms/" + aqsId + "/review-card", userA, "{}");
        assertEquals(cardId, second.path("cardId").asLong(), "重复加入必须复用同一张卡");
        assertFalse(second.path("created").asBoolean());

        assertEquals(1, cardCount(userA, aqsId), "同一词条只能有一条权威复习关系");
        assertEquals(0, cardCount(userB, aqsId), "不得为其他用户建卡");
        String front = jdbc.queryForObject(
                "SELECT front FROM learning_flashcard WHERE id=?", String.class, cardId);
        assertEquals("AQS", front, "复习卡正面使用词条主词，与词库文本解耦");
    }

    @Test
    @DisplayName("TE-P1-2: 匿名不能加入、评分或读取我的复习数据")
    void anonymousCannotUseReviewApis() throws Exception {
        mockMvc.perform(post("/api/tech-english/me/terms/" + aqsId + "/review-card"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tech-english/me/review-queue"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tech-english/me/progress"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/tech-english/me/terms/" + aqsId + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":3,\"requestId\":\"anon-request-0001\"}"))
                .andExpect(status().isUnauthorized());
    }

    /* ============================ 二、评分持久化与幂等 ============================ */

    @Test
    @DisplayName("TE-P1-3: 首次评分真实写入 FSRS 排程与学习事件，看板统计来自服务端")
    void firstRatingPersistsScheduleAndProgress() throws Exception {
        postJson("/api/tech-english/me/terms/" + aqsId + "/review-card", userA, "{}");
        String requestId = "req-" + UUID.randomUUID().toString().replace("-", "");
        JsonNode receipt = postJson("/api/tech-english/me/terms/" + aqsId + "/review",
                userA, json.writeValueAsString(Map.of("rating", 3, "requestId", requestId,
                        "sessionId", "session-p1-a-01")));
        assertEquals(3, receipt.path("rating").asInt());
        assertFalse(receipt.path("replayed").asBoolean());
        assertNotNull(receipt.path("dueAt").asText(), "评分必须返回真实下次到期时间");

        Integer reviewCount = jdbc.queryForObject(
                "SELECT review_count FROM learning_flashcard WHERE id=?",
                Integer.class, receipt.path("cardId").asLong());
        assertEquals(1, reviewCount, "首次评分后复习次数应为 1");
        Integer events = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_event WHERE user_id=? AND resource_type='FLASHCARD'"
                        + " AND event_type='FLASHCARD_REVIEWED' AND deleted=0"
                        + " AND metadata LIKE ?", Integer.class, userA, "%" + requestId + "%");
        assertEquals(1, events, "必须写入一条学习事件");

        String progress = mockMvc.perform(get("/api/tech-english/me/progress")
                        .header("Authorization", auth(userA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = json.readTree(progress).path("data");
        assertEquals(1, data.path("learned").asLong(), "已学习词汇数来自真实复习记录");
        assertEquals(1, data.path("completedToday").asLong());
        assertEquals(1, data.path("addedToReview").asLong());
        assertTrue(data.path("totalTerms").asLong() >= 50);
        assertTrue(data.path("domains").size() > 0);
    }

    @Test
    @DisplayName("TE-P1-4: 同一 requestId 重复提交返回回执且不重复记分")
    void duplicateRequestDoesNotDoubleCount() throws Exception {
        postJson("/api/tech-english/me/terms/" + aqsId + "/review-card", userA, "{}");
        String requestId = "req-" + UUID.randomUUID().toString().replace("-", "");
        String payload = json.writeValueAsString(Map.of("rating", 3, "requestId", requestId));

        JsonNode first = postJson("/api/tech-english/me/terms/" + aqsId + "/review", userA, payload);
        JsonNode second = postJson("/api/tech-english/me/terms/" + aqsId + "/review", userA, payload);
        assertFalse(first.path("replayed").asBoolean());
        assertTrue(second.path("replayed").asBoolean(), "重复请求必须标记为回放");
        assertEquals(first.path("revision").asLong(), second.path("revision").asLong());
        assertEquals(first.path("dueAt").asText(), second.path("dueAt").asText());

        Integer reviewCount = jdbc.queryForObject(
                "SELECT review_count FROM learning_flashcard WHERE id=?",
                Integer.class, first.path("cardId").asLong());
        assertEquals(1, reviewCount, "重复提交不得重复记分");

        mockMvc.perform(post("/api/tech-english/me/terms/" + aqsId + "/review")
                        .header("Authorization", auth(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("rating", 4, "requestId", requestId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("TE-P1-5: 非法评分与未加入复习的评分被拒绝")
    void invalidRatingAndUnlinkedTermRejected() throws Exception {
        postJson("/api/tech-english/me/terms/" + aqsId + "/review-card", userA, "{}");
        for (int rating : List.of(0, 5)) {
            mockMvc.perform(post("/api/tech-english/me/terms/" + aqsId + "/review")
                            .header("Authorization", auth(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("rating", rating,
                                    "requestId", "req-invalid-" + rating + "-0001"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
        mockMvc.perform(post("/api/tech-english/me/terms/" + wordId + "/review")
                        .header("Authorization", auth(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":3,\"requestId\":\"req-unlinked-0001\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
        assertEquals(0, cardCount(userA, wordId), "未加入复习不得因评分自动建卡");
    }

    /* ============================ 三、隔离与不重置 ============================ */

    @Test
    @DisplayName("TE-P1-6: 不同用户的复习记录互相隔离")
    void reviewStateIsolatedBetweenUsers() throws Exception {
        JsonNode a = postJson("/api/tech-english/me/terms/" + aqsId + "/review-card", userA, "{}");
        postJson("/api/tech-english/me/terms/" + aqsId + "/review", userA,
                json.writeValueAsString(Map.of("rating", 3,
                        "requestId", "req-" + UUID.randomUUID().toString().replace("-", ""))));

        JsonNode bAdd = postJson("/api/tech-english/me/terms/" + aqsId + "/review-card", userB, "{}");
        assertTrue(bAdd.path("created").asBoolean(), "B 必须有自己的复习卡");
        assertTrue(a.path("cardId").asLong() != bAdd.path("cardId").asLong(),
                "不同用户的复习卡必须独立");

        String progressB = mockMvc.perform(get("/api/tech-english/me/progress")
                        .header("Authorization", auth(userB)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, json.readTree(progressB).path("data").path("learned").asLong(),
                "B 不得继承 A 的学习进度");
    }

    @Test
    @DisplayName("TE-P1-7: 收藏与取消收藏不重置复习状态和已有复习卡")
    void favoriteDoesNotResetReviewState() throws Exception {
        JsonNode added = postJson("/api/tech-english/me/terms/" + aqsId + "/review-card", userA, "{}");
        long cardId = added.path("cardId").asLong();
        JsonNode receipt = postJson("/api/tech-english/me/terms/" + aqsId + "/review", userA,
                json.writeValueAsString(Map.of("rating", 3,
                        "requestId", "req-" + UUID.randomUUID().toString().replace("-", ""))));
        long revision = receipt.path("revision").asLong();

        for (boolean favorite : List.of(true, false, true)) {
            mockMvc.perform(put("/api/tech-english/me/terms/" + aqsId)
                            .header("Authorization", auth(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("favorite", favorite))))
                    .andExpect(status().isOk());
        }
        Long linked = jdbc.queryForObject(
                "SELECT linked_flashcard_id FROM user_vocab_state WHERE user_id=? AND term_id=?",
                Long.class, userA, aqsId);
        assertEquals(cardId, linked, "收藏变化不得改变复习卡映射");
        Long fsrsRevision = jdbc.queryForObject(
                "SELECT fsrs_revision FROM learning_flashcard WHERE id=?", Long.class, cardId);
        assertEquals(revision, fsrsRevision, "收藏不得重置 FSRS 修订");
        assertNotNull(jdbc.queryForObject(
                "SELECT last_review_time FROM learning_flashcard WHERE id=?", Object.class, cardId));
    }

    @Test
    @DisplayName("TE-P1-8: 修改词条中文释义不删除复习卡与历史")
    void termContentEditKeepsReviewHistory() throws Exception {
        JsonNode added = postJson("/api/tech-english/me/terms/" + aqsId + "/review-card", userA, "{}");
        long cardId = added.path("cardId").asLong();
        postJson("/api/tech-english/me/terms/" + aqsId + "/review", userA,
                json.writeValueAsString(Map.of("rating", 3,
                        "requestId", "req-" + UUID.randomUUID().toString().replace("-", ""))));

        jdbc.update("UPDATE tech_vocab_term SET meaning_cn=?,update_time=? WHERE id=?",
                "临时修改的中文释义", java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()), aqsId);

        assertEquals(1, cardCount(userA, aqsId), "词条编辑不得删除复习卡");
        assertEquals(1, jdbc.queryForObject(
                "SELECT review_count FROM learning_flashcard WHERE id=?", Integer.class, cardId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_event WHERE user_id=? AND resource_id=?"
                        + " AND event_type='FLASHCARD_REVIEWED' AND deleted=0",
                Integer.class, userA, cardId), "历史学习事件必须保留");
    }

    /* ============================ 四、队列与模式 ============================ */

    @Test
    @DisplayName("TE-P1-9: 队列区分到期卡与新词，并给出缩写/听音模式可用性")
    void queueExposesEligibilityAndNewTerms() throws Exception {
        postJson("/api/tech-english/me/terms/" + aqsId + "/review-card", userA, "{}");
        String body = mockMvc.perform(get("/api/tech-english/me/review-queue").param("limit", "5")
                        .header("Authorization", auth(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = json.readTree(body).path("data");
        JsonNode items = data.path("items");
        assertEquals(5, items.size(), "队列必须遵守 limit");
        assertTrue(data.path("hasReviewCards").asBoolean());
        assertEquals(1, data.path("dueCount").asInt(), "新加入未评分的卡应计入待复习");

        JsonNode aqs = null;
        for (JsonNode item : items) {
            if (item.path("termId").asLong() == aqsId) {
                aqs = item;
                break;
            }
        }
        assertNotNull(aqs, "已加入复习的 AQS 必须出现在队列中");
        assertTrue(aqs.path("acronymMode").asBoolean(), "缩写且存在全称才开放缩写模式");
        assertTrue(aqs.path("listeningMode").asBoolean(), "有已核验读法才开放听音模式");
        assertTrue(aqs.path("pronunciations").size() >= 2);
        assertTrue(aqs.path("cardId").asLong() > 0);
    }

    @Test
    @DisplayName("TE-P1-10: 普通单词不开放缩写模式；无卡新词 cardId 为空且可补入队列")
    void plainWordHasNoAcronymMode() throws Exception {
        String body = mockMvc.perform(get("/api/tech-english/me/review-queue").param("limit", "50")
                        .header("Authorization", auth(userA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode items = json.readTree(body).path("data").path("items");
        assertTrue(items.size() > 0, "未加入复习时也应补充新词");
        JsonNode word = null;
        for (JsonNode item : items) {
            if ("WORD".equals(item.path("termType").asText())) {
                word = item;
                break;
            }
        }
        assertNotNull(word, "队列应包含普通单词新词");
        assertFalse(word.path("acronymMode").asBoolean(), "普通单词不得进入缩写专项练习");
        boolean hasNullCard = false;
        for (JsonNode item : items) {
            if (item.path("cardId").isNull()) {
                hasNullCard = true;
                break;
            }
        }
        assertTrue(hasNullCard, "未加入复习的新词 cardId 必须为空，不得伪造复习实体");
    }

    @Test
    @DisplayName("TE-P1-11: 既有普通闪卡不受技术英语复习影响")
    void existingFlashcardsUnaffected() throws Exception {
        Long baseline = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_flashcard WHERE user_id=? AND deleted=0",
                Long.class, userA);
        postJson("/api/tech-english/me/terms/" + aqsId + "/review-card", userA, "{}");
        Long after = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_flashcard WHERE user_id=? AND deleted=0",
                Long.class, userA);
        assertEquals(baseline + 1, after, "只新增一张技术英语复习卡");
        Long other = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_flashcard WHERE user_id=? AND deleted=0"
                        + " AND source_type<>'TECH_ENGLISH'", Long.class, userA);
        assertEquals(baseline, other, "原有闪卡数量与来源不得被改写");
    }

    @Test
    @DisplayName("TE-P1-12: 未登录看板 401；登录后返回真实零值与领域进度")
    void progressRequiresLoginAndReturnsRealZeroes() throws Exception {
        mockMvc.perform(get("/api/tech-english/me/progress")).andExpect(status().isUnauthorized());
        String body = mockMvc.perform(get("/api/tech-english/me/progress")
                        .header("Authorization", auth(userA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = json.readTree(body).path("data");
        assertEquals(0, data.path("learned").asLong());
        assertEquals(0, data.path("dueToday").asLong());
        assertEquals(0, data.path("completedToday").asLong());
        assertEquals(0, data.path("addedToReview").asLong());
        assertEquals(0, data.path("favoriteCount").asLong());
        assertTrue(data.path("totalTerms").asLong() >= 50);
        assertTrue(data.path("domains").size() > 0);
        assertTrue(data.path("domains").get(0).path("learned").isNumber(), "领域进度必须是真实数值");
    }
}
