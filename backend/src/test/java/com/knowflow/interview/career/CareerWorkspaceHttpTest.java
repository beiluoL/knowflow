package com.knowflow.interview.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 求职工作台总览、岗位流程与方案审核的真实 HTTP 链路；不调用付费模型。 */
@SpringBootTest(properties = {
        "knowflow.datasource.h2.url=jdbc:h2:mem:career_workspace;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "knowflow.datasource.config-file=/tmp/career-workspace-no-overrides.json",
        "knowflow.seed.development-account.enabled=false",
        "knowflow.datasource.allow-runtime-switch=false"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CareerWorkspaceHttpTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtUtils jwt;
    @Autowired JdbcTemplate jdbc;
    @MockBean com.knowflow.service.impl.AiServiceImpl ai;

    private String token(long id) {
        return "Bearer " + jwt.generateToken(id, "career-workspace-fixture", id == 1 ? "ADMIN" : "USER");
    }

    private JsonNode post(String path, Object body, long user, int expected) throws Exception {
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                        .header("Authorization", token(user))
                        .contentType("application/json")
                        .content(json.writeValueAsString(body)))
                .andExpect(status().is(expected))
                .andExpect(jsonPath("$.code").value(expected))
                .andReturn();
        return json.readTree(response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }

    private JsonNode put(String path, Object body, long user, int expected) throws Exception {
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                        .header("Authorization", token(user))
                        .contentType("application/json")
                        .content(json.writeValueAsString(body)))
                .andExpect(status().is(expected))
                .andExpect(jsonPath("$.code").value(expected))
                .andReturn();
        return json.readTree(response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }

    @Test
    void dashboardAndJobPipelineUseRealOwnerData() throws Exception {
        var installed = put("/api/apps/user-apps/career-workbench",
                Map.of("installed", true, "enabled", true, "sortOrder", 0), 1, 200);
        assertEquals("career-workbench", installed.path("appKey").asText());
        assertEquals(true, installed.path("installed").asBoolean());

        String root = "/api/interview/career";
        var job = post(root + "/records/JOB", Map.of(
                "title", "高级 Java 工程师",
                "source", "必须 Java 并发",
                "sections", Map.of("requiredSkills", List.of(Map.of("value", "Java 并发", "quote", "必须 Java 并发")))), 1, 200);
        long jobId = job.path("id").asLong();
        assertTrue(jobId > 0);

        mvc.perform(get(root + "/dashboard").header("Authorization", token(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.jobs").value(1))
                .andExpect(jsonPath("$.data.counts.favoriteJobs").value(1));
        mvc.perform(get(root + "/dashboard")).andExpect(status().isUnauthorized());

        var applied = put(root + "/jobs/" + jobId + "/pipeline", Map.of("status", "APPLIED", "note", "已投递"), 1, 200);
        assertEquals("APPLIED", applied.path("status").asText());
        assertTrue(!applied.path("appliedAt").isMissingNode() && !applied.path("appliedAt").isNull());

        mvc.perform(get(root + "/jobs?status=APPLIED").header("Authorization", token(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].jobStatus.status").value("APPLIED"));
        mvc.perform(get(root + "/jobs?status=SAVED").header("Authorization", token(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mvc.perform(get(root + "/jobs/" + jobId + "/pipeline").header("Authorization", token(2)))
                .andExpect(status().isNotFound());
    }

    @Test
    void reviewedPlanStartsOriginalV2AndKeepsPreviousSnapshot() throws Exception {
        String root = "/api/interview/career";
        var resume = post(root + "/records/RESUME", Map.of(
                "title", "测试简历",
                "source", "曾完成 Java 多线程练习",
                "sections", Map.of("skills", List.of(Map.of("value", "Java 多线程", "quote", "Java 多线程练习")))), 1, 200);
        long resumeId = resume.path("id").asLong();
        var job = post(root + "/records/JOB", Map.of(
                "title", "Java 并发岗位",
                "source", "必须 Java 并发",
                "sections", Map.of("requiredSkills", List.of(Map.of("value", "Java 并发", "quote", "必须 Java 并发")))), 1, 200);
        long jobId = job.path("id").asLong();
        post(root + "/records/" + resumeId + "/confirm", Map.of("version", 1), 1, 200);
        post(root + "/records/" + jobId + "/confirm", Map.of("version", 1), 1, 200);
        var match = post(root + "/matches", Map.of(
                "resumeId", resumeId, "resumeVersion", 1,
                "jobId", jobId, "jobVersion", 1,
                "requestId", UUID.randomUUID().toString()), 1, 200);
        lisMatchId(match);

        var plan = post(root + "/plans", Map.ofEntries(
                Map.entry("matchId", match.path("id").asLong()),
                Map.entry("projectCount", 0), Map.entry("coreCount", 2),
                Map.entry("gapCount", 0), Map.entry("designCount", 1),
                Map.entry("durationMinutes", 20), Map.entry("difficulty", "MEDIUM"),
                Map.entry("focusSkills", List.of("Java 并发")),
                Map.entry("interviewType", "TECHNICAL"), Map.entry("projectDepth", "MEDIUM"),
                Map.entry("requestId", UUID.randomUUID().toString())), 1, 200);
        assertEquals(3, plan.path("questions").size());
        long planId = plan.path("id").asLong();

        var revised = new LinkedHashMap<String, Object>();
        var questions = new java.util.ArrayList<Map<String, Object>>();
        for (JsonNode question : plan.path("questions")) {
            var row = new LinkedHashMap<String, Object>();
            row.put("questionId", question.path("questionId").asText());
            row.put("title", question.path("questionId").asText().endsWith("-0")
                    ? "人工审核后的第一题：请说明 Java 并发的核心机制与边界。"
                    : question.path("title").asText());
            row.put("questionPurpose", question.path("questionPurpose").asText());
            row.put("evidenceReference", question.path("evidenceReference").asText());
            row.put("expectedKnowledgePoints", List.of("Java 并发", "工程边界"));
            row.put("targetSkill", question.path("targetSkill").asText(""));
            row.put("source", question.path("source").asText("USER_EDITED"));
            row.put("scoringDimension", question.path("scoringDimension").asText("TECHNICAL_CORRECTNESS"));
            row.put("followupDirections", List.of("验证方法"));
            row.put("skillIds", List.of());
            questions.add(row);
        }
        revised.put("requestId", UUID.randomUUID().toString());
        revised.put("questions", questions);

        var reviewed = post(root + "/plans/" + planId + "/questions", revised, 1, 200);
        assertEquals(3, reviewed.path("questions").size());
        assertEquals(planId, reviewed.path("previousPlanId").asLong());
        assertTrue(reviewed.path("questions").get(0).path("title").asText().contains("人工审核"));

        var session = post(root + "/plans/" + reviewed.path("id").asLong() + "/start",
                Map.of("requestId", UUID.randomUUID().toString()), 1, 200);
        assertEquals("ASKING", session.path("state").asText());
        assertEquals(3, session.path("questionCount").asInt());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM interview_career_session WHERE user_id=1 AND plan_id=?",
                Integer.class, reviewed.path("id").asLong()));
    }

    @Test
    void polishSuggestionsMustBindToSourceAndNeverModifyRecord() throws Exception {
        String root = "/api/interview/career";
        var record = post(root + "/records/RESUME", Map.of(
                "title", "测试简历",
                "source", "学习 Java 并发",
                "sections", Map.of("skills", List.of(Map.of("value", "Java 并发", "quote", "Java 并发")))), 1, 200);
        long id = record.path("id").asLong();

        post(root + "/records/" + id + "/polish",
                Map.of("version", 1, "allowModelProcessing", false, "quotes", List.of("学习 Java 并发")), 1, 400);

        org.mockito.Mockito.when(ai.isConfigured(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        org.mockito.Mockito.when(ai.completeStructured(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn("[{\"original\":\"不存在的片段\",\"suggestion\":\"改写\",\"reason\":\"表达\"}]");
        post(root + "/records/" + id + "/polish",
                Map.of("version", 1, "allowModelProcessing", true, "quotes", List.of("学习 Java 并发")), 1, 502);

        org.mockito.Mockito.when(ai.completeStructured(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn("[{\"original\":\"学习 Java 并发\",\"suggestion\":\"熟悉 Java 并发\",\"reason\":\"表达更简洁\"}]");
        var suggestion = post(root + "/records/" + id + "/polish",
                Map.of("version", 1, "allowModelProcessing", true, "quotes", List.of("学习 Java 并发")), 1, 200);
        assertEquals(false, suggestion.path("modified").asBoolean());
        assertEquals(1, suggestion.path("suggestions").size());
        assertEquals(1, jdbc.queryForObject(
                "SELECT current_version FROM interview_career_record WHERE id=?", Integer.class, id));
    }

    @Test
    void linkImportRejectsLocalAndPrivateTargets() {
        var service = new CareerLinkImportService(null, null);
        assertThrows(BusinessException.class, () -> service.validate("file:///etc/passwd"));
        assertThrows(BusinessException.class, () -> service.validate("http://localhost:8080/jd"));
        assertThrows(BusinessException.class, () -> service.validate("http://127.0.0.1/jd"));
        assertThrows(BusinessException.class, () -> service.validate("http://10.0.0.1/jd"));
        assertThrows(BusinessException.class, () -> service.validate("http://user:pass@example.com/jd"));
        assertThrows(BusinessException.class, () -> service.validate("https://example.com:8443/jd"));
    }

    private void lisMatchId(JsonNode match) {
        assertTrue(match.path("id").asLong() > 0);
    }
}
