package com.knowflow.interview.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.apps.OfficialAppRegistry;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Career Apps V2.0：八个应用注册、旧用户迁移、工具箱与模板设计的真实 HTTP 链路。
 *
 * <p>不调用付费模型：模型替身只用于防误调用，工具箱本身是纯规则计算。</p>
 */
@SpringBootTest(properties = {
        "knowflow.datasource.h2.url=jdbc:h2:mem:career_apps_v2;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "knowflow.datasource.config-file=/tmp/career-apps-v2-no-overrides.json",
        "knowflow.seed.development-account.enabled=false",
        "knowflow.datasource.allow-runtime-switch=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CareerAppsV2HttpTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtUtils jwt;
    @Autowired JdbcTemplate jdbc;
    @MockBean com.knowflow.ai.service.impl.AiServiceImpl ai;

    private static final List<String> CAREER_KEYS = List.of(
            "career-overview", "career-resume", "career-jobs", "career-matching",
            "career-interviews", "career-workbench", "career-resume-toolbox", "career-resume-templates");

    private String token(long userId) {
        return "Bearer " + jwt.generateToken(userId, "career-v2-fixture", userId == 1 ? "ADMIN" : "USER");
    }

    private JsonNode call(MockHttpServletRequestBuilder builder, long userId, int expected) throws Exception {
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

    private JsonNode put(String path, Object body, long userId, int expected) throws Exception {
        return call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), userId, expected);
    }

    private Map<String, Object> claim(String value, String quote) {
        return Map.of("value", value, "quote", quote);
    }

    private long createRecord(long userId, String kind, String title, String source,
                              Map<String, List<Map<String, Object>>> sections) throws Exception {
        JsonNode created = post("/api/interview/career/records/" + kind,
                Map.of("title", title, "source", source, "sections", sections), userId, 200);
        long id = created.path("id").asLong();
        assertTrue(id > 0);
        post("/api/interview/career/records/" + id + "/confirm", Map.of("version", 1), userId, 200);
        return id;
    }

    @Test
    void pipelineHistoryIsOwnerScopedAndRepeatedStateIsIdempotent() throws Exception {
        long id = createRecord(2L, "JOB", "V31 history", "Java", Map.of());
        String base = "/api/interview/career/jobs/" + id;
        put(base + "/pipeline", Map.of("status", "TO_APPLY", "note", "准备"), 2L, 200);
        put(base + "/pipeline", Map.of("status", "TO_APPLY", "note", "更新备注"), 2L, 200);
        JsonNode events = call(get(base + "/events"), 2L, 200);
        assertEquals(1, events.size());
        assertEquals("SAVED", events.get(0).path("from_status").asText());
        assertEquals("TO_APPLY", events.get(0).path("to_status").asText());
        call(get(base + "/events"), 3L, 404);
        mvc.perform(get(base + "/events")).andExpect(status().isUnauthorized());
        put(base + "/pipeline", Map.of("status", "INVALID"), 2L, 400);
        assertEquals(1, call(get(base + "/events"), 2L, 200).size());
    }

    /* ------------------------- 应用注册与迁移 ------------------------- */

    @Test
    void registersEightCareerAppsIncludingOriginalWorkbenchKey() {
        for (String key : CAREER_KEYS) {
            assertTrue(new OfficialAppRegistry().isValid(key), "缺少应用 " + key);
        }
        assertEquals(5, OfficialAppRegistry.CAREER_COMPANION_KEYS.size());
        assertEquals(2, OfficialAppRegistry.CAREER_OPT_IN_KEYS.size());
        assertTrue(OfficialAppRegistry.CAREER_COMPANION_KEYS.contains("career-resume"));
        assertTrue(OfficialAppRegistry.CAREER_OPT_IN_KEYS.contains("career-resume-toolbox"));
        assertFalse(OfficialAppRegistry.CAREER_COMPANION_KEYS.contains("career-resume-templates"));
    }

    @Test
    void migratingLegacyWorkbenchUserAddsFiveCompanionsButNotOptInApps() throws Exception {
        long userId = 2;
        jdbc.update("DELETE FROM user_app WHERE user_id=?", userId);
        // 模拟 V1.0：只安装并启用 career-workbench，sortOrder 仍是默认 0
        put("/api/apps/user-apps/career-workbench", Map.of("installed", true, "enabled", true, "sortOrder", 0), userId, 200);

        JsonNode apps = call(get("/api/apps/user-apps"), userId, 200);
        Map<String, JsonNode> byKey = new java.util.LinkedHashMap<>();
        apps.forEach(node -> byKey.put(node.path("appKey").asText(), node));

        for (String key : OfficialAppRegistry.CAREER_COMPANION_KEYS) {
            assertTrue(byKey.containsKey(key), "未补齐应用 " + key);
            assertTrue(byKey.get(key).path("enabled").asBoolean(), "应继承工作台的启用状态");
        }
        assertTrue(byKey.containsKey("career-workbench"));
        assertEquals(6, byKey.get("career-workbench").path("sortOrder").asInt());
        assertFalse(byKey.containsKey("career-resume-toolbox"), "工具箱不能自动安装");
        assertFalse(byKey.containsKey("career-resume-templates"), "模板不能自动安装");

        // 幂等：再次读取不产生重复记录，也不改变已有状态
        int before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_app WHERE user_id=? AND deleted=0", Integer.class, userId);
        call(get("/api/apps/user-apps"), userId, 200);
        int after = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_app WHERE user_id=? AND deleted=0", Integer.class, userId);
        assertEquals(before, after);
    }

    @Test
    void explicitDisabledWorkbenchKeepsCompanionsDisabled() throws Exception {
        long userId = 2;
        jdbc.update("DELETE FROM user_app WHERE user_id=?", userId);
        put("/api/apps/user-apps/career-workbench",
                Map.of("installed", true, "enabled", false, "sortOrder", 0), userId, 200);

        JsonNode apps = call(get("/api/apps/user-apps"), userId, 200);
        for (JsonNode node : apps) {
            if (!OfficialAppRegistry.CAREER_COMPANION_KEYS.contains(node.path("appKey").asText())) continue;
            assertFalse(node.path("enabled").asBoolean(), "工作台停用时子应用不得自动出现在菜单");
            assertTrue(node.path("installed").asBoolean());
        }
    }

    @Test
    void existingExplicitDisableIsNotOverwrittenByMigration() throws Exception {
        long userId = 2;
        jdbc.update("DELETE FROM user_app WHERE user_id=?", userId);
        put("/api/apps/user-apps/career-workbench",
                Map.of("installed", true, "enabled", true, "sortOrder", 0), userId, 200);
        put("/api/apps/user-apps/career-resume",
                Map.of("installed", true, "enabled", false, "sortOrder", 2), userId, 200);

        JsonNode apps = call(get("/api/apps/user-apps"), userId, 200);
        for (JsonNode node : apps) {
            if (!"career-resume".equals(node.path("appKey").asText())) continue;
            assertFalse(node.path("enabled").asBoolean(), "用户显式停用不能被迁移覆盖");
            assertEquals(2, node.path("sortOrder").asInt());
        }
    }

    /* ------------------------- 工具箱 ------------------------- */

    @Test
    void atsCheckShowsHonestUnknownForLayoutAndPersistsHistory() throws Exception {
        long userId = 1;
        String source = "张三\n13800138000\nzhangsan@example.com\n工作经历\n2020.01-2022.06 某公司 Java 开发\n技能\nJava\nSpring Boot\nMySQL";
        long resumeId = createRecord(userId, "RESUME", "ATS 测试简历", source, Map.of(
                "basics", List.of(claim("张三", "张三")),
                "skills", List.of(claim("Java", "Java"), claim("Spring Boot", "Spring Boot"), claim("MySQL", "MySQL")),
                "experience", List.of(claim("2020.01-2022.06 某公司 Java 开发", "2020.01-2022.06 某公司 Java 开发"))));

        JsonNode result = post("/api/interview/career/toolbox/ats-check",
                Map.of("resumeId", resumeId), userId, 200);

        assertEquals("ATS", result.path("tool").asText());
        assertEquals(1, result.path("resumeVersion").asInt());
        assertTrue(result.path("items").isArray());
        boolean layoutUnknown = false;
        for (JsonNode item : result.path("items")) {
            if ("layout".equals(item.path("id").asText())) {
                layoutUnknown = "UNKNOWN".equals(item.path("status").asText());
            }
            assertFalse(item.path("evidence").asText().isBlank(), "每项都必须有证据或说明");
        }
        assertTrue(layoutUnknown, "没有版式解析能力时必须显示 UNKNOWN");
        assertTrue(result.path("disclaimer").asText().contains("不代表"));

        JsonNode history = call(get("/api/interview/career/toolbox/history").param("toolType", "ATS"), userId, 200);
        assertTrue(history.size() >= 1);
        assertEquals("ATS", history.get(0).path("toolType").asText());
    }

    @Test
    void contentCheckFlagsReversedPeriodWithoutInventingContent() throws Exception {
        long userId = 1;
        String source = "工作经历\n2022.06-2020.01 某公司 Java 开发\n技能\nJava\nSpring Boot\nMySQL";
        long resumeId = createRecord(userId, "RESUME", "体检测试简历", source, Map.of(
                "skills", List.of(claim("Java", "Java"), claim("Spring Boot", "Spring Boot"), claim("MySQL", "MySQL")),
                "experience", List.of(claim("2022.06-2020.01 某公司 Java 开发", "2022.06-2020.01 某公司 Java 开发"))));

        JsonNode result = post("/api/interview/career/toolbox/content-check", Map.of("resumeId", resumeId), userId, 200);
        boolean dateFail = false;
        for (JsonNode item : result.path("items")) {
            if ("date-order".equals(item.path("id").asText())) {
                assertEquals("FAIL", item.path("status").asText());
                dateFail = true;
            }
        }
        assertTrue(dateFail, "开始晚于结束必须判为需修改");
        assertTrue(result.path("disclaimer").asText().contains("不会替你生成"));
    }

    @Test
    void jdOptimizeReusesDeterministicMatcherAndNeverWritesResume() throws Exception {
        long userId = 1;
        String resumeSource = "技能\nJava\nSpring Boot\nMySQL";
        long resumeId = createRecord(userId, "RESUME", "JD 优化简历", resumeSource, Map.of(
                "skills", List.of(claim("Java", "Java"), claim("Spring Boot", "Spring Boot"), claim("MySQL", "MySQL"))));
        String jobSource = "必须 Java\n必须 Redis";
        long jobId = createRecord(userId, "JOB", "后端岗位", jobSource, Map.of(
                "requiredSkills", List.of(claim("Java", "必须 Java"), claim("Redis", "必须 Redis"))));

        int versionsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM interview_career_version WHERE record_id=?", Integer.class, resumeId);

        JsonNode result = post("/api/interview/career/toolbox/jd-optimize",
                Map.of("resumeId", resumeId, "resumeVersion", 1, "jobId", jobId, "jobVersion", 1), userId, 200);

        assertEquals(50, result.path("coverage").asInt());
        assertEquals(2, result.path("requiredCount").asInt());
        assertEquals(1, result.path("matchedCount").asInt());
        boolean missingRedis = false;
        for (JsonNode item : result.path("required")) {
            if ("Redis".equals(item.path("skill").asText())) {
                assertEquals("MISSING", item.path("status").asText());
                missingRedis = true;
            }
        }
        assertTrue(missingRedis);
        assertTrue(result.path("suggestions").size() >= 1);
        assertTrue(result.path("disclaimer").asText().contains("不代表录用概率"));

        int versionsAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM interview_career_version WHERE record_id=?", Integer.class, resumeId);
        assertEquals(versionsBefore, versionsAfter, "分析不得产生新的简历版本");
    }

    @Test
    void compareDetectsAddedAndRemovedWithoutMutatingVersions() throws Exception {
        long userId = 1;
        String first = "技能\nJava";
        long resumeId = createRecord(userId, "RESUME", "对比简历", first,
                Map.of("skills", List.of(claim("Java", "Java"))));
        put("/api/interview/career/records/" + resumeId,
                Map.of("expectedVersion", 1, "title", "对比简历", "source", "技能\nJava\nRedis",
                        "sections", Map.of("skills", List.of(claim("Java", "Java"), claim("Redis", "Redis")))),
                userId, 200);

        JsonNode result = call(get("/api/interview/career/records/" + resumeId + "/compare")
                .param("from", "1").param("to", "2"), userId, 200);
        JsonNode skills = result.path("changes").path("skills");
        assertEquals(1, skills.size());
        assertEquals("ADDED", skills.get(0).path("type").asText());
        assertEquals("Redis", skills.get(0).path("after").asText());

        int versions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM interview_career_version WHERE record_id=?", Integer.class, resumeId);
        assertEquals(2, versions, "对比不得修改或新增版本");
    }

    /* ------------------------- 模板设计 ------------------------- */

    @Test
    void designIsIndependentFromResumeVersionAndOwnerScoped() throws Exception {
        long owner = 1;
        long other = 2;
        long resumeId = createRecord(owner, "RESUME", "模板简历", "技能\nJava",
                Map.of("skills", List.of(claim("Java", "Java"))));

        Map<String, Object> theme = Map.of(
                "templateId", "ats-classic",
                "fontFamily", "sans",
                "fontSize", 11,
                "lineHeight", 1.6,
                "accentColor", "#1F2329",
                "pageSize", "A4",
                "sectionGap", 12,
                "margin", Map.of("top", 16, "right", 16, "bottom", 16, "left", 16),
                "sectionOrder", List.of("basics", "skills"),
                "visibleSections", List.of("basics", "skills"));

        JsonNode created = post("/api/interview/career/records/" + resumeId + "/designs",
                Map.of("name", "我的设计", "theme", theme), owner, 200);
        long designId = created.path("id").asLong();
        assertTrue(designId > 0);
        assertEquals("ats-classic", created.path("templateId").asText());

        int versionsAfterDesign = jdbc.queryForObject(
                "SELECT COUNT(*) FROM interview_career_version WHERE record_id=?", Integer.class, resumeId);

        // 切换模板只改设计，不产生简历版本
        JsonNode updated = put("/api/interview/career/records/" + resumeId + "/designs/" + designId,
                Map.of("theme", Map.of(
                        "templateId", "modern-two-col",
                        "fontFamily", "serif",
                        "fontSize", 10,
                        "lineHeight", 1.5,
                        "accentColor", "#1F3A5F",
                        "pageSize", "A4",
                        "sectionGap", 10,
                        "margin", Map.of("top", 14, "right", 14, "bottom", 14, "left", 14),
                        "sectionOrder", List.of("skills", "basics"),
                        "visibleSections", List.of("skills"))),
                owner, 200);
        assertEquals("modern-two-col", updated.path("templateId").asText());
        assertEquals(10, updated.path("theme").path("fontSize").asInt());

        int versionsAfterUpdate = jdbc.queryForObject(
                "SELECT COUNT(*) FROM interview_career_version WHERE record_id=?", Integer.class, resumeId);
        assertEquals(versionsAfterDesign, versionsAfterUpdate);

        // 他人不可读、不可改
        call(get("/api/interview/career/records/" + resumeId + "/designs"), other, 404);
        put("/api/interview/career/records/" + resumeId + "/designs/" + designId,
                Map.of("theme", theme), other, 404);

        // 非法模板与越界字号被拒绝
        post("/api/interview/career/records/" + resumeId + "/designs",
                Map.of("theme", Map.of(
                        "templateId", "not-exist", "fontFamily", "sans", "fontSize", 11,
                        "lineHeight", 1.6, "accentColor", "#1F2329", "pageSize", "A4", "sectionGap", 12,
                        "margin", Map.of("top", 16, "right", 16, "bottom", 16, "left", 16),
                        "sectionOrder", List.of("basics"), "visibleSections", List.of("basics"))),
                owner, 400);
        post("/api/interview/career/records/" + resumeId + "/designs",
                Map.of("theme", Map.of(
                        "templateId", "ats-classic", "fontFamily", "sans", "fontSize", 99,
                        "lineHeight", 1.6, "accentColor", "#1F2329", "pageSize", "A4", "sectionGap", 12,
                        "margin", Map.of("top", 16, "right", 16, "bottom", 16, "left", 16),
                        "sectionOrder", List.of("basics"), "visibleSections", List.of("basics"))),
                owner, 400);

        JsonNode templates = call(get("/api/interview/career/templates"), owner, 200);
        assertEquals(6, templates.size());
    }

    @Test
    void overviewAggregatesOnlyOwnData() throws Exception {
        long userId = 2;
        JsonNode overview = call(get("/api/interview/career/overview"), userId, 200);
        assertTrue(overview.has("counts"));
        assertTrue(overview.has("trends"));
        assertEquals(8, overview.path("trends").size());
        assertTrue(overview.path("skills").has("overall"));
        assertTrue(overview.path("interview").has("selfScoreAvg"));
    }

    @Test
    void toolboxRejectsUnknownToolType() throws Exception {
        call(get("/api/interview/career/toolbox/history").param("toolType", "HACK"), 1, 400);
    }

    @Test
    void analysisBoundToRequestedVersion() throws Exception {
        long userId = 1;
        long resumeId = createRecord(userId, "RESUME", "版本绑定简历", "技能\nJava",
                Map.of("skills", List.of(claim("Java", "Java"))));
        put("/api/interview/career/records/" + resumeId,
                Map.of("expectedVersion", 1, "title", "版本绑定简历", "source", "技能\nJava\nRedis",
                        "sections", Map.of("skills", List.of(claim("Java", "Java"), claim("Redis", "Redis")))),
                userId, 200);
        JsonNode result = post("/api/interview/career/toolbox/content-check",
                Map.of("resumeId", resumeId, "version", 2), userId, 200);
        assertEquals(2, result.path("resumeVersion").asInt());
    }
}
