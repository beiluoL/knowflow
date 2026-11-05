package com.knowflow.interview.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Career V3.0 真实 HTTP 链路：岗位导入、多简历匹配、定制版本、数据分析。
 *
 * <p>不访问外网、不调用付费模型：发现源由适配器单测覆盖，这里只验证落库与业务约束。</p>
 */
@SpringBootTest(properties = {
        "knowflow.datasource.h2.url=jdbc:h2:mem:career_v3;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "knowflow.datasource.config-file=/tmp/career-v3-no-overrides.json",
        "knowflow.seed.development-account.enabled=false",
        "knowflow.datasource.allow-runtime-switch=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CareerV3HttpTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtUtils jwt;
    @Autowired JdbcTemplate jdbc;
    @MockBean com.knowflow.ai.service.impl.AiServiceImpl ai;

    private String token(long userId) {
        return "Bearer " + jwt.generateToken(userId, "v3-fixture", userId == 1 ? "ADMIN" : "USER");
    }

    private JsonNode call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                          long userId, int expected) throws Exception {
        var response = mvc.perform(builder.header("Authorization", token(userId)))
                .andExpect(status().is(expected))
                .andExpect(jsonPath("$.code").value(expected))
                .andReturn();
        return json.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    private JsonNode post(String path, Object body, long userId, int expected) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), userId, expected);
    }

    private Map<String, Object> claim(String value, String quote) {
        return Map.of("value", value, "quote", quote);
    }

    private long createConfirmed(long userId, String kind, String title, String source,
                                 Map<String, List<Map<String, Object>>> sections) throws Exception {
        JsonNode created = post("/api/interview/career/records/" + kind,
                Map.of("title", title, "source", source, "sections", sections), userId, 200);
        long id = created.path("id").asLong();
        post("/api/interview/career/records/" + id + "/confirm", Map.of("version", 1), userId, 200);
        return id;
    }

    /* ------------------------------ 岗位发现 ------------------------------ */

    @Test
    void sourcesAreRegisteredWithPolicyAndAttribution() throws Exception {
        JsonNode sources = call(get("/api/interview/career/discover/sources"), 1, 200);
        assertTrue(sources.size() >= 6);
        boolean remotive = false;
        for (JsonNode source : sources) {
            assertTrue(source.path("lastPolicyReviewedAt").asText().length() >= 8, "每个来源必须有复核日期");
            if ("remotive".equals(source.path("sourceId").asText())) {
                remotive = true;
                assertTrue(source.path("attributionRequired").asBoolean());
                assertTrue(source.path("attributionText").asText().contains("Remotive"));
            }
        }
        assertTrue(remotive);
    }

    @Test
    void importIsIdempotentAndTraceable() throws Exception {
        long userId = 1;
        Map<String, Object> body = Map.of(
                "sourceId", "remotive",
                "externalId", "ext-9001",
                "title", "Acme · Java Engineer",
                "company", "Acme",
                "location", "Remote",
                "description", "必须 Java，优先 Redis",
                "sourceUrl", "https://remotive.com/jobs/ext-9001",
                "postedAt", "2026-11-01T08:00:00Z",
                "attribution", "数据来源：Remotive");

        JsonNode first = post("/api/interview/career/discover/import", body, userId, 200);
        long recordId = first.path("recordId").asLong();
        assertTrue(recordId > 0);
        assertFalse(first.path("duplicate").asBoolean());

        JsonNode second = post("/api/interview/career/discover/import", body, userId, 200);
        assertEquals(recordId, second.path("recordId").asLong());
        assertTrue(second.path("duplicate").asBoolean(), "同来源同外部ID不得重复建岗位");

        JsonNode source = call(get("/api/interview/career/discover/jobs/" + recordId + "/source"), userId, 200);
        assertEquals("remotive", source.path("sourceId").asText());
        assertEquals("https://remotive.com/jobs/ext-9001", source.path("sourceUrl").asText());

        // 他人不可读该溯源
        call(get("/api/interview/career/discover/jobs/" + recordId + "/source"), 2, 200);
        JsonNode other = call(get("/api/interview/career/discover/jobs/" + recordId + "/source"), 2, 200);
        assertTrue(other.isEmpty(), "来源溯源必须按用户隔离");
    }

    @Test
    void importRejectsUnknownSourceAndInvalidUrl() throws Exception {
        post("/api/interview/career/discover/import", Map.of(
                "sourceId", "boss", "title", "x", "description", "y"), 1, 400);
        post("/api/interview/career/discover/import", Map.of(
                "sourceId", "remotive", "title", "x", "description", "y",
                "sourceUrl", "javascript:alert(1)"), 1, 400);
    }

    /* ------------------------------ 多简历匹配 ------------------------------ */

    @Test
    void oneJobMatchesAllConfirmedResumesAndRecommendsBest() throws Exception {
        long userId = 2;
        long javaResume = createConfirmed(userId, "RESUME", "Java 主简历",
                "技能\nJava\nSpring Boot\nRedis",
                Map.of("skills", List.of(claim("Java", "Java"), claim("Spring Boot", "Spring Boot"), claim("Redis", "Redis"))));
        long frontResume = createConfirmed(userId, "RESUME", "前端简历",
                "技能\nVue\nTypeScript",
                Map.of("skills", List.of(claim("Vue", "Vue"), claim("TypeScript", "TypeScript"))));
        long job = createConfirmed(userId, "JOB", "Java 后端",
                "必须 Java\n必须 Redis",
                Map.of("requiredSkills", List.of(claim("Java", "必须 Java"), claim("Redis", "必须 Redis"))));

        JsonNode result = post("/api/interview/career/matches/multi-resume",
                Map.of("jobId", job), userId, 200);
        JsonNode candidates = result.path("candidates");
        assertEquals(2, candidates.size());
        assertEquals("Java 主简历", candidates.get(0).path("resumeTitle").asText());
        assertEquals(100.0, candidates.get(0).path("coverage").asDouble(), 0.01);
        assertTrue(candidates.get(0).path("recommended").asBoolean());
        assertEquals(javaResume, candidates.get(0).path("resumeId").asLong());
        assertEquals(frontResume, candidates.get(1).path("resumeId").asLong());
        assertEquals(0.0, candidates.get(1).path("coverage").asDouble(), 0.01);
        assertFalse(result.path("recommendedResumeId").isNull());

        // 缺口清单复用快照
        long matchId = candidates.get(1).path("matchId").asLong();
        JsonNode gaps = call(get("/api/interview/career/matches/" + matchId + "/gaps"), userId, 200);
        assertEquals(2, gaps.path("missing").size());
        assertEquals(0, gaps.path("covered").size());
        assertTrue(gaps.path("note").asText().contains("不代表本人不会"));

        // 他人不可见
        call(get("/api/interview/career/matches/" + matchId), 1, 404);
    }

    @Test
    void oneResumeMatchesMultipleJobsWithPartialFailure() throws Exception {
        long userId = 1;
        long resume = createConfirmed(userId, "RESUME", "全栈简历", "技能\nJava\nVue",
                Map.of("skills", List.of(claim("Java", "Java"), claim("Vue", "Vue"))));
        long jobA = createConfirmed(userId, "JOB", "Java 岗", "必须 Java",
                Map.of("requiredSkills", List.of(claim("Java", "必须 Java"))));
        long draftJob = post("/api/interview/career/records/JOB",
                Map.of("title", "未确认岗位", "source", "必须 Go",
                        "sections", Map.of("requiredSkills", List.of(claim("Go", "必须 Go")))), userId, 200)
                .path("id").asLong();

        JsonNode result = post("/api/interview/career/matches/batch",
                Map.of("resumeId", resume, "resumeVersion", 1, "jobIds", List.of(jobA, draftJob)), userId, 200);
        assertEquals(1, result.path("jobs").size());
        assertEquals(1, result.path("failures").size(), "未确认岗位应单独失败，不影响其他岗位");
        assertTrue(result.path("partial").asBoolean());
    }

    /* ------------------------------ 定制简历 ------------------------------ */

    @Test
    void tailoringGeneratesBoundSuggestionsAndCreatesNewVersion() throws Exception {
        long userId = 1;
        String source = "工作经历\n2019.01-2020.01 某公司 前端开发\n2020.01-2022.06 某公司 Java 开发\n技能\nJava";
        long resume = createConfirmed(userId, "RESUME", "定制主简历", source, Map.of(
                "experience", List.of(
                        claim("2019.01-2020.01 某公司 前端开发", "2019.01-2020.01 某公司 前端开发"),
                        claim("2020.01-2022.06 某公司 Java 开发", "2020.01-2022.06 某公司 Java 开发")),
                "skills", List.of(claim("Java", "Java"))));
        String jobSource = "必须 Java\n必须 Redis";
        long job = createConfirmed(userId, "JOB", "Java+Redis 岗", jobSource,
                Map.of("requiredSkills", List.of(claim("Java", "必须 Java"), claim("Redis", "必须 Redis"))));

        JsonNode task = post("/api/interview/career/tailoring/tasks", Map.of(
                "resumeId", resume, "resumeVersion", 1, "jobId", job, "jobVersion", 1,
                "mode", "DEEP", "allowModelProcessing", false), userId, 200);

        assertEquals("RULE", task.path("modelSource").asText(), "未授权模型时必须使用规则");
        JsonNode changes = task.path("changes");
        assertTrue(changes.size() >= 1);
        boolean hasQuestion = false;
        for (JsonNode change : changes) {
            assertFalse(change.path("originalQuote").asText().isBlank(), "每条建议必须绑定原文");
            String quote = change.path("originalQuote").asText();
            String quoteSource = change.path("quoteSource").asText("RESUME");
            assertTrue(("JD".equals(quoteSource) ? jobSource : source).contains(quote),
                    "引用必须来自声明的来源：" + quoteSource);
            if ("QUESTION".equals(change.path("type").asText())
                    && "Redis".equals(change.path("requirement").asText())) hasQuestion = true;
        }
        assertTrue(hasQuestion, "缺失技能必须转为提问而不是补写");

        long taskId = task.path("id").asLong();
        List<String> accepted = new java.util.ArrayList<>();
        for (JsonNode change : changes) {
            if (!"QUESTION".equals(change.path("type").asText())) accepted.add(change.path("id").asText());
        }
        JsonNode reviewed = post("/api/interview/career/tailoring/tasks/" + taskId + "/review",
                Map.of("acceptedIds", accepted), userId, 200);
        assertTrue(reviewed.path("changes").size() >= 1);

        JsonNode applied = post("/api/interview/career/tailoring/tasks/" + taskId + "/create-version",
                Map.of(), userId, 200);
        assertEquals("APPLIED", applied.path("status").asText());
        assertEquals(2, applied.path("resultVersion").asInt());

        // 原版本内容未被覆盖
        JsonNode original = call(get("/api/interview/career/records/" + resume).param("version", "1"), userId, 200);
        assertEquals(source, original.path("source").asText());

        int versions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM interview_career_version WHERE record_id=?", Integer.class, resume);
        assertEquals(2, versions, "只新增一个版本，不覆盖历史");
    }

    @Test
    void tailoringRejectsOtherUsersTask() throws Exception {
        long owner = 1;
        long resume = createConfirmed(owner, "RESUME", "私有简历", "技能\nJava",
                Map.of("skills", List.of(claim("Java", "Java"))));
        long job = createConfirmed(owner, "JOB", "私有岗位", "必须 Java",
                Map.of("requiredSkills", List.of(claim("Java", "必须 Java"))));
        JsonNode task = post("/api/interview/career/tailoring/tasks", Map.of(
                "resumeId", resume, "resumeVersion", 1, "jobId", job, "jobVersion", 1,
                "mode", "DEEP", "allowModelProcessing", false), owner, 200);
        long taskId = task.path("id").asLong();
        call(get("/api/interview/career/tailoring/tasks/" + taskId), 2, 404);
        post("/api/interview/career/tailoring/tasks/" + taskId + "/review",
                Map.of("acceptedIds", List.of()), 2, 404);
    }

    /* ------------------------------ 数据分析 ------------------------------ */

    @Test
    void insightsReportSampleSizeAndScope() throws Exception {
        createConfirmed(1, "JOB", "薪资样本岗位",
                "15-25K·14薪 北京 本科 3-5年 必须 Java",
                Map.of("requiredSkills", List.of(claim("Java", "必须 Java"))));
        JsonNode insights = call(get("/api/interview/career/insights"), 1, 200);
        assertTrue(insights.path("sampleSize").asInt() >= 1);
        assertEquals(20, insights.path("salary").path("median").asInt());
        assertTrue(insights.path("scope").asText().contains("不代表行业整体"));
        assertTrue(insights.path("personal").path("rateNote").asText().contains("样本"));
        assertTrue(insights.path("salary").path("note").asText().contains("不计入"));
        // 没有数据时中位数为 null，而不是 0
        if (insights.path("salary").path("parsed").asInt() == 0) {
            assertTrue(insights.path("salary").path("median").isNull());
        }
    }

    @Test
    void searchHistoryIsOwnerScopedAndDeduplicated() throws Exception {
        long userId = 2;
        JsonNode first = post("/api/interview/career/discover/history",
                Map.of("keyword", "java", "filters", Map.of("city", "北京"), "saved", false), userId, 200);
        long id = first.path("id").asLong();
        JsonNode again = post("/api/interview/career/discover/history",
                Map.of("keyword", "java", "filters", Map.of("city", "北京"), "saved", true), userId, 200);
        assertEquals(id, again.path("id").asLong(), "相同条件应更新而不是重复插入");
        assertTrue(again.path("saved").asBoolean());

        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/interview/career/discover/history/" + id), userId, 200);
        JsonNode history = call(get("/api/interview/career/discover/history"), userId, 200);
        assertEquals(0, history.size());
    }
}
