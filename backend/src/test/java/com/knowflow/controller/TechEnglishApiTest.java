package com.knowflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 技术英语 P0 API 集成测试（H2 + 真实 HTTP 语义）。
 *
 * 覆盖：
 * - 匿名检索 / 类型与领域筛选 / 中文关键词 / 同形多义；
 * - 详情字段、缩写全称拆解、发音变体、相关术语关系；
 * - 收藏与学习状态的用户隔离、幂等、非法入参；
 * - 匿名写状态 401、普通用户访问管理端 403；
 * - 管理员导入预览 + 确认 + 同 token 重复确认幂等（不重复写库）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_tech_english_p0;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class TechEnglishApiTest {

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
    private Long admin;
    private Long aqsId;

    @BeforeEach
    void setUp() throws Exception {
        userA = createUser("USER");
        userB = createUser("USER");
        admin = createUser("ADMIN");
        // 种子词条在应用启动时写入；AQS 是 P0 验收核心词条
        String body = mockMvc.perform(get("/api/tech-english/terms").param("q", "AQS"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var records = json.readTree(body).path("data").path("records");
        assertTrue(records.size() > 0, "种子数据必须包含 AQS，检查 seed 初始化是否执行");
        aqsId = records.get(0).path("id").asLong();
    }

    private String auth(Long userId) {
        return "Bearer " + jwtUtils.generateToken(userId, "tech-" + userId, "USER");
    }

    private String adminAuth() {
        return "Bearer " + jwtUtils.generateToken(admin, "tech-admin-" + admin, "ADMIN");
    }

    private Long createUser(String role) {
        String key = UUID.randomUUID().toString().substring(0, 8);
        SysUser u = new SysUser();
        u.setUsername("tech-" + key);
        u.setEmail("tech-" + key + "@knowflow.test");
        u.setPassword("");
        u.setNickname("tech-" + key);
        u.setRole(role);
        u.setFavoriteCount(0);
        userMapper.insert(u);
        assertNotNull(u.getId(), "测试用户必须落库成功");
        return u.getId();
    }

    /* ============================ 一、公开检索 ============================ */

    @Test
    @DisplayName("TE-API-1: 匿名搜索 AQS 返回完整分页结构，且包含缩写全称与类型")
    void anonymousSearchReturnsPagedAcronym() throws Exception {
        String body = mockMvc.perform(get("/api/tech-english/terms")
                        .param("q", "AQS")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var first = json.readTree(body).path("data").path("records").get(0);
        assertEquals("AQS", first.path("headword").asText());
        assertEquals("ACRONYM", first.path("termType").asText());
        assertEquals("AbstractQueuedSynchronizer", first.path("fullForm").asText());
        assertEquals("CONCURRENCY", first.path("domain").asText());
        assertTrue(first.path("hasVerifiedPronunciation").asBoolean(), "AQS 必须有已核验读法可播放");
    }

    @Test
    @DisplayName("TE-API-2: 中文关键词「死锁」返回并发与数据库两个义项，领域筛选互不吞并")
    void chineseKeywordAndDomainFilterKeepTwoSenses() throws Exception {
        String body = mockMvc.perform(get("/api/tech-english/terms").param("q", "死锁"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var records = json.readTree(body).path("data").path("records");
        assertEquals(2, records.size(), "死锁应有并发与数据库两个义项");
        List<String> domains = List.of(records.get(0).path("domain").asText(), records.get(1).path("domain").asText());
        assertTrue(domains.contains("CONCURRENCY"));
        assertTrue(domains.contains("DB"));

        String dbOnly = mockMvc.perform(get("/api/tech-english/terms")
                        .param("q", "死锁").param("domain", "DB"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var dbRecords = json.readTree(dbOnly).path("data").path("records");
        assertEquals(1, dbRecords.size(), "DB 领域筛选后应只剩一个死锁义项");
        assertEquals("DB", dbRecords.get(0).path("domain").asText());
    }

    @Test
    @DisplayName("TE-API-3: 类型筛选 ACRONYM 只返回缩写；非法类型返回 400")
    void typeFilterAndInvalidType() throws Exception {
        String body = mockMvc.perform(get("/api/tech-english/terms")
                        .param("type", "ACRONYM").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var records = json.readTree(body).path("data").path("records");
        assertTrue(records.size() >= 10, "缩写词条应成规模存在");
        records.forEach(node -> assertEquals("ACRONYM", node.path("termType").asText()));

        mockMvc.perform(get("/api/tech-english/terms").param("type", "NOT_A_TYPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("TE-API-4: 筛选统计返回类型/领域计数")
    void filtersReturnCounts() throws Exception {
        mockMvc.perform(get("/api/tech-english/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.byType.ACRONYM").isNumber())
                .andExpect(jsonPath("$.data.domains").isArray());
    }

    /* ============================ 二、详情与发音 ============================ */

    @Test
    @DisplayName("TE-API-5: AQS 详情含全称拆解、ABBR/FULL_FORM 两种读法、相关术语 CAS")
    void detailExposesBreakdownPronunciationsAndRelations() throws Exception {
        String body = mockMvc.perform(get("/api/tech-english/terms/" + aqsId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headword").value("AQS"))
                .andExpect(jsonPath("$.data.contentStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.wordBreakdown[0].word").value("Abstract"))
                .andExpect(jsonPath("$.data.referenceSource").isNotEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var data = json.readTree(body).path("data");
        List<String> variants = data.path("pronunciations").findValuesAsText("variant");
        assertTrue(variants.contains("ABBR"), "缩写必须有字母读法");
        assertTrue(variants.contains("FULL_FORM"), "缩写必须有全称读法");
        List<String> related = data.path("relatedTerms").findValuesAsText("headword");
        assertTrue(related.contains("CAS"), "AQS 应关联 CAS");
        // 无授权音频时必须如实为空，不得伪造 URL
        data.path("pronunciations").forEach(p -> assertTrue(p.path("audioUrl").isNull()
                || p.path("audioUrl").asText().isEmpty(), "P0 无授权录音，audioUrl 必须为空"));
    }

    @Test
    @DisplayName("TE-API-6: 发音独立接口返回已核验变体；不存在词条 404")
    void pronunciationEndpoint() throws Exception {
        String body = mockMvc.perform(get("/api/tech-english/terms/" + aqsId + "/pronunciations"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var rows = json.readTree(body).path("data");
        assertTrue(rows.size() >= 2);
        rows.forEach(p -> assertEquals("VERIFIED", p.path("verificationStatus").asText()));

        mockMvc.perform(get("/api/tech-english/terms/99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    /* ============================ 三、用户状态隔离 ============================ */

    @Test
    @DisplayName("TE-API-7: 匿名读我的词条 401；A 收藏后 A 详情为 true、B 为 false")
    void favoriteIsolatedBetweenUsers() throws Exception {
        mockMvc.perform(get("/api/tech-english/me/terms"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/tech-english/me/terms/" + aqsId)
                        .header("Authorization", auth(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\":true,\"learningStatus\":\"LEARNING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorite").value(true))
                .andExpect(jsonPath("$.data.learningStatus").value("LEARNING"));

        String a = mockMvc.perform(get("/api/tech-english/terms/" + aqsId).header("Authorization", auth(userA)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(json.readTree(a).path("data").path("favorite").asBoolean());

        String b = mockMvc.perform(get("/api/tech-english/terms/" + aqsId).header("Authorization", auth(userB)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(json.readTree(b).path("data").path("favorite").asBoolean(), "B 不得继承 A 的收藏");

        String mine = mockMvc.perform(get("/api/tech-english/me/terms").param("favorite", "true")
                        .header("Authorization", auth(userA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var records = json.readTree(mine).path("data").path("records");
        assertEquals(1, records.size());
        assertEquals(aqsId.longValue(), records.get(0).path("term").path("id").asLong());
    }

    @Test
    @DisplayName("TE-API-8: 重复收藏幂等且只落一行；非法学习状态 400")
    void favoriteIsIdempotentAndValidatesStatus() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(put("/api/tech-english/me/terms/" + aqsId)
                            .header("Authorization", auth(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"favorite\":true}"))
                    .andExpect(status().isOk());
        }
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_vocab_state WHERE user_id = ? AND term_id = ?",
                Integer.class, userA, aqsId);
        assertEquals(1, rows, "重复收藏不得产生多行");

        mockMvc.perform(put("/api/tech-english/me/terms/" + aqsId)
                        .header("Authorization", auth(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"learningStatus\":\"DONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(put("/api/tech-english/me/terms/" + aqsId)
                        .header("Authorization", auth(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    /* ============================ 四、管理端与导入幂等 ============================ */

    @Test
    @DisplayName("TE-API-9: 普通用户访问管理端 403，匿名 401")
    void adminEndpointsRequireAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/tech-english/terms"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/tech-english/terms").header("Authorization", auth(userA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("TE-API-10: 管理员创建草稿词条后公开检索不可见，管理端可见")
    void adminCreateDraftIsHiddenFromPublicSearch() throws Exception {
        String headword = "TestTerm" + UUID.randomUUID().toString().substring(0, 6);
        String createBody = json.writeValueAsString(Map.of(
                "headword", headword,
                "termType", "WORD",
                "domain", "JAVA",
                "senseKey", "api-test",
                "meaningCn", "测试词条",
                "contentStatus", "DRAFT"));
        mockMvc.perform(post("/api/admin/tech-english/terms")
                        .header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.pronunciations.length()").value(2));

        String publicBody = mockMvc.perform(get("/api/tech-english/terms").param("q", headword))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, json.readTree(publicBody).path("data").path("records").size(),
                "草稿不得出现在公开检索");

        String adminBody = mockMvc.perform(get("/api/admin/tech-english/terms").param("q", headword)
                        .header("Authorization", adminAuth()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(1, json.readTree(adminBody).path("data").path("records").size(),
                "管理端检索必须能看到草稿");
    }

    @Test
    @DisplayName("TE-API-11: 导入预览不写库；确认后写入；同 token 重复确认返回 replayed 且不重复写")
    void importPreviewConfirmIsIdempotent() throws Exception {
        String headword = "ImportTerm" + UUID.randomUUID().toString().substring(0, 6);
        String payload = """
                {"terms":[{
                  "headword":"%s",
                  "termType":"ACRONYM",
                  "domain":"AGENT",
                  "senseKey":"import-test",
                  "fullForm":"Imported Test Term",
                  "meaningCn":"导入测试词条",
                  "contentStatus":"PUBLISHED",
                  "referenceSource":"集成测试自造，不进入公共种子",
                  "pronunciations":[
                    {"variant":"ABBR","readingText":"I T T","readingMode":"LETTER_BY_LETTER","verificationStatus":"VERIFIED"},
                    {"variant":"FULL_FORM","readingText":"Imported Test Term","readingMode":"SPOKEN_WORD","verificationStatus":"VERIFIED"}
                  ]
                }]}
                """.formatted(headword);

        String previewBody = mockMvc.perform(post("/api/admin/tech-english/import-preview")
                        .header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newCount").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = json.readTree(previewBody).path("data").path("token").asText();
        assertFalse(token.isEmpty());

        Integer afterPreview = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tech_vocab_term WHERE normalized_key = ?",
                Integer.class, headword.toLowerCase());
        assertEquals(0, afterPreview, "预览阶段不得写库");

        String confirm = json.writeValueAsString(Map.of("token", token));
        mockMvc.perform(post("/api/admin/tech-english/import-confirm")
                        .header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.replayed").value(false));

        mockMvc.perform(post("/api/admin/tech-english/import-confirm")
                        .header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.replayed").value(true));

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tech_vocab_term WHERE normalized_key = ?",
                Integer.class, headword.toLowerCase());
        assertEquals(1, rows, "重复确认不得重复写入词条");
    }
}
