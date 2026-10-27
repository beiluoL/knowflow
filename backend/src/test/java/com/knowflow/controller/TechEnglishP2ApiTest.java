package com.knowflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.DocCategory;
import com.knowflow.entity.DocDocument;
import com.knowflow.entity.LearningChapter;
import com.knowflow.entity.LearningNote;
import com.knowflow.entity.SysUser;
import com.knowflow.entity.TechVocabConceptLink;
import com.knowflow.mapper.DocCategoryMapper;
import com.knowflow.mapper.DocDocumentMapper;
import com.knowflow.mapper.LearningChapterMapper;
import com.knowflow.mapper.LearningNoteMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.mapper.TechVocabConceptLinkMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 技术英语 P2 API 集成测试（H2 + 真实 HTTP 语义）。
 *
 * 覆盖：
 * - 划词查询的归一化、缩写、同形多义、超长/纯中文选区、领域加权；
 * - 私人知识库文档、知识单元不得从术语关联中泄漏；
 * - 提词来源鉴权（文档阅读权限、笔记归属、他人笔记 404）；
 * - 选区提取只处理选区文本但保留来源鉴权；
 * - 未配置模型时诚实降级为规则提取、AI 结果结构化校验与领域回落；
 * - 候选保存幂等、用户隔离、非法词形拒绝；
 * - 管理员审核权限、发布/不发布语义、重复审核不重复建词条。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_tech_english_p2;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class TechEnglishP2ApiTest {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private DocCategoryMapper categoryMapper;

    @Autowired
    private DocDocumentMapper docMapper;

    @Autowired
    private LearningNoteMapper noteMapper;

    @Autowired
    private LearningChapterMapper chapterMapper;

    @Autowired
    private TechVocabConceptLinkMapper linkMapper;

    private Long userA;
    private Long userB;
    private Long admin;
    private Long aqsId;
    private Long embeddingId;

    @BeforeEach
    void setUp() throws Exception {
        FakeAiConfiguration.reset();
        userA = createUser("USER");
        userB = createUser("USER");
        admin = createUser("ADMIN");
        aqsId = termId("AQS");
        embeddingId = termId("Embedding");
    }

    /* ============================ 一、划词查询 ============================ */

    @Test
    @DisplayName("P2-API-1: 选区首尾标点、大小写与空白归一化后命中同一词条")
    void lookupNormalizesSelection() throws Exception {
        for (String raw : List.of("embedding", "  Embedding, ", "\"Embedding\"", "EMBEDDING。")) {
            var data = lookup(raw, null);
            assertEquals("embedding", data.path("normalized").asText(), "归一化键必须与小写词形一致：" + raw);
            assertTrue(data.path("rejectedReason").isNull() || data.path("rejectedReason").isMissingNode());
            assertEquals(1, data.path("matches").size(), "同一词形只命中一个词条：" + raw);
            assertEquals("Embedding", data.path("matches").get(0).path("headword").asText());
        }
    }

    @Test
    @DisplayName("P2-API-2: 缩写查词返回英文全称、类型与发音变体")
    void lookupAcronymReturnsFullFormAndPronunciations() throws Exception {
        var data = lookup("AQS", null);
        var first = data.path("matches").get(0);
        assertEquals("AQS", first.path("headword").asText());
        assertEquals("ACRONYM", first.path("termType").asText());
        assertEquals("AbstractQueuedSynchronizer", first.path("fullForm").asText());
        assertTrue(first.path("pronunciations").size() >= 1, "缩写必须有可播放/可降级的发音变体");
        assertFalse(data.path("ambiguous").asBoolean());
    }

    @Test
    @DisplayName("P2-API-3: 同形多义（Deadlock）返回两个义项并标记 ambiguous，领域上下文优先排序")
    void lookupMultiSenseAndDomainPriority() throws Exception {
        var plain = lookup("Deadlock", null);
        assertEquals(2, plain.path("matches").size(), "Deadlock 应保留并发与数据库两个义项");
        assertTrue(plain.path("ambiguous").asBoolean(), "同分多义必须明确告知前端");

        var withDomain = lookup("Deadlock", "DB");
        assertEquals(2, withDomain.path("matches").size(), "领域加权不能让其他义项消失");
        assertEquals("DB", withDomain.path("matches").get(0).path("domain").asText(),
                "文档技术分类命中的义项必须排在首位");
    }

    @Test
    @DisplayName("P2-API-4: 超长/跨段选区与纯中文选区明确拒绝，不执行词库查询")
    void lookupRejectsUnsupportedSelections() throws Exception {
        var tooLong = lookup("a".repeat(120), null);
        assertTrue(tooLong.path("rejectedReason").asText().contains("选区过长"));
        assertEquals(0, tooLong.path("matches").size());

        var multiLine = lookup("AbstractQueuedSynchronizer\nhelps", null);
        assertTrue(multiLine.path("rejectedReason").asText().contains("选区过长"));

        var chinese = lookup("同步器", null);
        assertTrue(chinese.path("rejectedReason").asText().contains("未包含可查询的英文术语"));
        assertEquals(0, chinese.path("matches").size());
    }

    @Test
    @DisplayName("P2-API-5: 私人知识库文档与失效知识实体不出现在术语关联中")
    void lookupFiltersUnauthorizedConceptLinks() throws Exception {
        DocCategory privateCategory = privateCategory();
        DocDocument privateDoc = doc(privateCategory.getId(), 1, "私人并发笔记：AQS 源码剖析");
        link("DOCUMENT", privateDoc.getId());
        link("KNOWLEDGE", 999_999L);
        link("DOCUMENT", 999_999L);

        var anonymous = lookup("AQS", null).path("matches").get(0).path("conceptLinks");
        assertFalse(containsTarget(anonymous, "DOCUMENT", privateDoc.getId()),
                "匿名查询不得看到私有知识库文档关联");
        assertFalse(containsTarget(anonymous, "KNOWLEDGE", 999_999L), "失效知识实体必须过滤");

        String owned = mockMvc.perform(get("/api/tech-english/terms/" + aqsId)
                        .header("Authorization", auth(userA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var links = json.readTree(owned).path("data").path("conceptLinks");
        assertTrue(containsTarget(links, "DOCUMENT", privateDoc.getId()),
                "知识库 Owner 本人应看到自己的关联文档");

        String other = mockMvc.perform(get("/api/tech-english/terms/" + aqsId)
                        .header("Authorization", auth(userB)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(containsTarget(json.readTree(other).path("data").path("conceptLinks"),
                "DOCUMENT", privateDoc.getId()), "同库其他普通用户不得看到 Owner 私有文档关联");
    }

    /* ============================ 二、提词与来源鉴权 ============================ */

    @Test
    @DisplayName("P2-API-5b: 章节关联只对登录用户展示，匿名不泄漏章节标题，登录后路由可用")
    void lookupHidesChapterLinksFromAnonymous() throws Exception {
        LearningChapter chapter = new LearningChapter();
        chapter.setPathId(1L);
        chapter.setTitle("Java 并发原理：AQS 与锁");
        chapter.setSortOrder(1);
        chapterMapper.insert(chapter);
        link("CHAPTER", chapter.getId());

        var anonymousLinks = lookup("AQS", null).path("matches").get(0).path("conceptLinks");
        assertFalse(containsTarget(anonymousLinks, "CHAPTER", chapter.getId()),
                "章节阅读入口需要登录，匿名查询不得展示章节关联");

        String body = mockMvc.perform(get("/api/tech-english/terms/" + aqsId)
                        .header("Authorization", auth(userA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var links = json.readTree(body).path("data").path("conceptLinks");
        assertTrue(containsTarget(links, "CHAPTER", chapter.getId()), "登录用户应看到章节关联");
        for (JsonNode link : links) {
            if ("CHAPTER".equals(link.path("targetType").asText())) {
                assertEquals("/learning/chapter/" + chapter.getId(), link.path("route").asText(),
                        "关联导航必须指向真实存在的章节路由");
            }
        }
    }

    @Test
    @DisplayName("P2-API-6: 匿名提取 401；他人笔记 404；本人笔记可提取")
    void extractRequiresLoginAndOwnsNote() throws Exception {
        LearningNote note = note(userA, "AQS 学习笔记", "The AQS framework helps implement synchronizers in Java.");

        mockMvc.perform(post("/api/tech-english/me/candidates/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("noteId", note.getId()))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/tech-english/me/candidates/extract")
                        .header("Authorization", auth(userB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("noteId", note.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(post("/api/tech-english/me/candidates/extract")
                        .header("Authorization", auth(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("noteId", note.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.extractor").value("RULE"))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("P2-API-7: 文档提词遵循知识库阅读权限，未授权/未发布统一 404")
    void extractHonorsDocumentReadPermission() throws Exception {
        DocCategory privateCategory = privateCategory();
        DocDocument privateDoc = doc(privateCategory.getId(), 1, "私有文档标题不得泄漏");
        DocDocument draftDoc = doc(null, 0, "未发布草稿");

        for (Long docId : List.of(privateDoc.getId(), draftDoc.getId())) {
            mockMvc.perform(post("/api/tech-english/me/candidates/extract")
                            .header("Authorization", auth(userB))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("documentId", docId))))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(post("/api/tech-english/me/candidates/extract")
                        .header("Authorization", auth(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("documentId", privateDoc.getId()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("P2-API-8: 选区提取只处理选区文本，但私有文档仍按归属鉴权")
    void selectionExtractUsesOnlySelectionAndKeepsAuth() throws Exception {
        DocDocument published = doc(null, 1, "公开教材");
        published.setContent("Kafka Partition Offset Consumer Group 不应出现在选区结果里");
        docMapper.updateById(published);

        String body = mockMvc.perform(post("/api/tech-english/me/candidates/extract")
                        .header("Authorization", auth(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "scope", "SELECTION",
                                "text", "The AQS framework helps implement synchronizers",
                                "documentId", published.getId(),
                                "types", List.of("ACRONYM", "WORD"),
                                "limit", 20))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var items = json.readTree(body).path("data").path("items");
        assertTrue(items.size() > 0, "选区应提取到候选");
        for (JsonNode item : items) {
            String headword = item.path("headword").asText();
            assertFalse(headword.equalsIgnoreCase("Kafka") || headword.equalsIgnoreCase("Partition"),
                    "选区提取不得扫描整篇文档：" + headword);
        }

        DocCategory privateCategory = privateCategory();
        DocDocument privateDoc = doc(privateCategory.getId(), 1, "私有选区来源");
        mockMvc.perform(post("/api/tech-english/me/candidates/extract")
                        .header("Authorization", auth(userB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "scope", "SELECTION",
                                "text", "AQS framework",
                                "documentId", privateDoc.getId()))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("P2-API-9: 未配置模型时如实降级为规则提取，不冒充 AI 结果")
    void extractDegradesHonestlyWithoutModel() throws Exception {
        FakeAiConfiguration.configured = false;
        String body = mockMvc.perform(post("/api/tech-english/me/candidates/extract")
                        .header("Authorization", auth(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "text", "The AQS framework helps implement synchronizers in Java.",
                                "types", List.of("ACRONYM", "WORD"),
                                "limit", 10,
                                "useAi", true))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var data = json.readTree(body).path("data");
        assertEquals("RULE", data.path("extractor").asText());
        assertFalse(data.path("aiAvailable").asBoolean());
        assertTrue(data.path("degradationNote").asText().contains("未配置"));
        assertEquals(0, FakeAiConfiguration.completeCalls, "未配置模型时不得调用模型");
    }

    @Test
    @DisplayName("P2-API-10: AI 返回结果结构化校验，非法词形丢弃、越界领域回落")
    void extractValidatesAiOutput() throws Exception {
        FakeAiConfiguration.script("""
                {"terms":[
                  {"headword":"事务隔离","termType":"WORD","domain":"DB","meaningCn":"非法"},
                  {"headword":"Liveness","termType":"WORD","domain":"MYSQL","meaningCn":"活性","definitionCn":"系统持续取得进展"},
                  {"headword":"Liveness","termType":"WORD","domain":"DB","meaningCn":"重复"},
                  {"headword":"Nope","termType":"ACRONYM","domain":"DB"}
                ]}
                """);
        String body = mockMvc.perform(post("/api/tech-english/me/candidates/extract")
                        .header("Authorization", auth(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "text", "Liveness ensures the system keeps making progress.",
                                "types", List.of("WORD"),
                                "limit", 10,
                                "useAi", true))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var data = json.readTree(body).path("data");
        assertEquals("AI", data.path("extractor").asText());
        assertEquals(1, data.path("items").size(), "非法中文词形与重复词形必须被丢弃");
        var item = data.path("items").get(0);
        assertEquals("Liveness", item.path("headword").asText());
        assertEquals("JAVA", item.path("domain").asText(), "非枚举领域必须回落到来源推断值");
        assertEquals("AI", item.path("extractor").asText());
    }

    /* ============================ 三、候选保存与审核 ============================ */

    @Test
    @DisplayName("P2-API-11: 候选保存幂等去重、用户隔离、非法词形 400")
    void saveCandidatesIdempotentAndScoped() throws Exception {
        String payload = json.writeValueAsString(Map.of("candidates", List.of(
                Map.of("headword", "Liveness", "termType", "WORD", "domain", "CONCURRENCY",
                        "meaningCn", "活性", "sourceType", "TEXT", "extractor", "RULE", "confidence", 40))));

        mockMvc.perform(post("/api/tech-english/me/candidates")
                        .header("Authorization", auth(userA))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(1));

        mockMvc.perform(post("/api/tech-english/me/candidates")
                        .header("Authorization", auth(userA))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved").value(0))
                .andExpect(jsonPath("$.data.skipped").value(1));

        String mine = mockMvc.perform(get("/api/tech-english/me/candidates")
                        .header("Authorization", auth(userA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(1, json.readTree(mine).path("data").size());

        String others = mockMvc.perform(get("/api/tech-english/me/candidates")
                        .header("Authorization", auth(userB)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, json.readTree(others).path("data").size(), "候选词条必须按用户隔离");

        mockMvc.perform(post("/api/tech-english/me/candidates")
                        .header("Authorization", auth(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("candidates", List.of(
                                Map.of("headword", "事务隔离", "meaningCn", "非法词形"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("P2-API-12: 候选审核需管理员；publish 决定是否进入公共词库；重复审核不重复建词条")
    void adminReviewGuardsPublishAndIdempotent() throws Exception {
        Long candidateId = saveCandidate(userA, "Liveness");
        Long publishCandidateId = saveCandidate(userA, "Jitter");

        mockMvc.perform(post("/api/admin/tech-english/candidates/" + candidateId + "/review")
                        .header("Authorization", auth(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("approve", true))))
                .andExpect(status().isForbidden());

        String reviewed = mockMvc.perform(post("/api/admin/tech-english/candidates/" + candidateId + "/review")
                        .header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "approve", true, "publish", false, "meaningCn", "活性（系统持续进展）"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long termId = json.readTree(reviewed).path("data").path("termId").asLong();
        assertTrue(termId > 0, "审核通过必须建立正式词条");

        mockMvc.perform(get("/api/tech-english/terms").param("q", "Liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        String replay = mockMvc.perform(post("/api/admin/tech-english/candidates/" + candidateId + "/review")
                        .header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("approve", true, "publish", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(termId, json.readTree(replay).path("data").path("termId").asLong(),
                "重复审核必须返回同一词条，不得重复建词条");

        mockMvc.perform(post("/api/admin/tech-english/candidates/" + publishCandidateId + "/review")
                        .header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "approve", true, "publish", true, "meaningCn", "抖动（时延波动）"))))
                .andExpect(status().isOk());

        var lookup = lookup("Jitter", null);
        assertEquals(1, lookup.path("matches").size(), "publish=true 后必须能被划词查询命中");
        var pronunciations = lookup.path("matches").get(0).path("pronunciations");
        for (JsonNode item : pronunciations) {
            assertFalse("VERIFIED".equals(item.path("verificationStatus").asText()),
                    "AI/用户候选生成的发音不得自动标记 VERIFIED");
        }
    }

    @Test
    @DisplayName("P2-API-13: 拒绝候选只改状态，不建词条、不进公共词库")
    void reviewRejectCreatesNoTerm() throws Exception {
        Long candidateId = saveCandidate(userA, "Skew");
        mockMvc.perform(post("/api/admin/tech-english/candidates/" + candidateId + "/review")
                        .header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("approve", false, "reviewNote", "非技术高频词"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.termId").doesNotExist());

        mockMvc.perform(get("/api/tech-english/terms").param("q", "Skew"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("P2-API-14: 词库扩充后共享词条总数达到两批种子规模（50 + 56）")
    void seedBatchesLoaded() throws Exception {
        String body = mockMvc.perform(get("/api/tech-english/filters"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long total = json.readTree(body).path("data").path("total").asLong();
        assertTrue(total >= 106, "P2 批次种子必须真实入库，当前：" + total);
        assertEquals(1, lookup("Vector Database", null).path("matches").size(), "P2 批次短语可检索");
        assertEquals(1, lookup("Overloading", null).path("matches").size(), "P2 批次技术词汇可检索");
    }

    /* ============================ 辅助 ============================ */

    private JsonNode lookup(String q, String domain) throws Exception {
        var request = get("/api/tech-english/lookup").param("q", q);
        if (domain != null) {
            request.param("contextDomain", domain);
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).path("data");
    }

    private boolean containsTarget(JsonNode links, String type, long targetId) {
        for (JsonNode link : links) {
            if (type.equals(link.path("targetType").asText()) && link.path("targetId").asLong() == targetId) {
                return true;
            }
        }
        return false;
    }

    private Long termId(String headword) throws Exception {
        String body = mockMvc.perform(get("/api/tech-english/terms").param("q", headword))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var records = json.readTree(body).path("data").path("records");
        assertTrue(records.size() > 0, "种子词条缺失：" + headword);
        return records.get(0).path("id").asLong();
    }

    private Long saveCandidate(Long userId, String headword) throws Exception {
        String body = mockMvc.perform(post("/api/tech-english/me/candidates")
                        .header("Authorization", auth(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("candidates", List.of(
                                Map.of("headword", headword, "termType", "WORD", "domain", "CONCURRENCY",
                                        "meaningCn", headword + " 待核验释义", "sourceType", "TEXT",
                                        "extractor", "RULE", "confidence", 40))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var items = json.readTree(body).path("data").path("items");
        assertTrue(items.size() > 0, "候选保存失败：" + headword);
        return items.get(0).path("id").asLong();
    }

    private void link(String targetType, Long targetId) {
        TechVocabConceptLink link = new TechVocabConceptLink();
        link.setTermId(aqsId);
        link.setTargetType(targetType);
        link.setTargetId(targetId);
        link.setRelationType("RELATED");
        linkMapper.insert(link);
    }

    private DocCategory privateCategory() {
        String key = UUID.randomUUID().toString().substring(0, 8);
        DocCategory category = new DocCategory();
        category.setName("私有知识库-" + key);
        category.setParentId(0L);
        category.setOwnerId(userA);
        category.setStatus(1);
        categoryMapper.insert(category);
        assertNotNull(category.getId());
        return category;
    }

    private DocDocument doc(Long categoryId, int status, String title) {
        DocDocument doc = new DocDocument();
        doc.setTitle(title);
        doc.setCategoryId(categoryId);
        doc.setStatus(status);
        doc.setContent("public content AQS framework");
        docMapper.insert(doc);
        assertNotNull(doc.getId());
        return doc;
    }

    private LearningNote note(Long userId, String title, String content) {
        LearningNote note = new LearningNote();
        note.setUserId(userId);
        note.setTitle(title);
        note.setContent(content);
        note.setStatus("ACTIVE");
        noteMapper.insert(note);
        assertNotNull(note.getId());
        return note;
    }

    private String auth(Long userId) {
        return "Bearer " + jwtUtils.generateToken(userId, "p2-" + userId, "USER");
    }

    private String adminAuth() {
        return "Bearer " + jwtUtils.generateToken(admin, "p2-admin-" + admin, "ADMIN");
    }

    private Long createUser(String role) {
        String key = UUID.randomUUID().toString().substring(0, 8);
        SysUser u = new SysUser();
        u.setUsername("p2-" + key);
        u.setEmail("p2-" + key + "@knowflow.test");
        u.setPassword("");
        u.setNickname("p2-" + key);
        u.setRole(role);
        u.setFavoriteCount(0);
        userMapper.insert(u);
        assertNotNull(u.getId(), "测试用户必须落库成功");
        return u.getId();
    }
}
