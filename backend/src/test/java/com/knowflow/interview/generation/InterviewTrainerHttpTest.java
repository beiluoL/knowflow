package com.knowflow.interview.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实安全链与隔离库：验证身份、知识库权限、任务归属与失败语义。
 * 模型使用替身，只验证协议与状态，不声称真实 AI 生成已验收。
 */
@SpringBootTest(properties = {
        "knowflow.ai.capability.enabled=true",
        "knowflow.datasource.h2.url=jdbc:h2:mem:phase1_generation_http;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "knowflow.datasource.config-file=/tmp/phase1-generation-no-overrides.json",
        "knowflow.seed.development-account.enabled=false",
        "knowflow.datasource.allow-runtime-switch=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class InterviewTrainerHttpTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtils jwt;
    @MockBean com.knowflow.ai.service.impl.AiServiceImpl ai;

    @BeforeEach
    void configure() {
        when(ai.isConfigured(anyLong())).thenReturn(false);
    }

    private String token(long uid) {
        return "Bearer " + jwt.generateToken(uid, "phase1-" + uid, uid == 1 ? "ADMIN" : "USER");
    }

    private String key() {
        return UUID.randomUUID().toString();
    }

    @Test
    void skillCatalogRequiresLoginAndReturnsTracks() throws Exception {
        mvc.perform(get("/api/interview/skills")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/interview/skills").header("Authorization", token(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.skills.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(12)));
        mvc.perform(post("/api/interview/skills/recommend").header("Authorization", token(1))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("jd", "RAG 与 Agent 工具调用", "role", "AI 应用开发工程师", "difficulty", "HARD", "years", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skills.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void generationIsScopedToReadableKnowledgeBaseAndOwner() throws Exception {
        long category = ownCategory(1L);
        long doc = insertDocument(category);
        var caps = mvc.perform(get("/api/interview/generation/capabilities").header("Authorization", token(1)))
                .andExpect(status().isOk()).andReturn();
        assertTrue(containsCategory(json.readTree(caps.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data"), category));
        var other = mvc.perform(get("/api/interview/generation/capabilities").header("Authorization", token(2)))
                .andExpect(status().isOk()).andReturn();
        assertFalse(containsCategory(json.readTree(other.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data"), category));

        mvc.perform(post("/api/interview/generation/tasks").header("Authorization", token(2))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("categoryId", category, "requestId", key()))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));

        String request = key();
        JsonNode task = submit(1L, Map.of("categoryId", category, "topic", "", "difficulty", 2, "questionCount", 3, "requestId", request), 200);
        long taskId = task.path("id").asLong();
        assertTrue(taskId > 0);
        assertEquals(taskId, submit(1L, Map.of("categoryId", category, "topic", "", "difficulty", 2, "questionCount", 3, "requestId", request), 200).path("id").asLong());
        submit(1L, Map.of("categoryId", category, "topic", "改变参数", "difficulty", 2, "questionCount", 3, "requestId", request), 409);

        mvc.perform(get("/api/interview/generation/tasks/" + taskId).header("Authorization", token(2)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));

        assertEquals("FAILED", settled(taskId, 1L).path("status").asText());
        assertTrue(settled(taskId, 1L).path("errorMessage").asText().contains("模型未配置"),
                settled(taskId, 1L).path("errorMessage").asText());
        mvc.perform(post("/api/interview/generation/tasks/" + taskId + "/retry").header("Authorization", token(1)))
                .andExpect(status().isOk());
        assertEquals("FAILED", settled(taskId, 1L).path("status").asText(), "模型仍未配置，重试必须再次明确失败");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM interview_generated_question WHERE task_id=?", Integer.class, taskId));
        assertTrue(doc > 0);
    }

    @Test
    void invalidRequestIsRejectedBeforeAnyTaskIsCreated() throws Exception {
        long category = ownCategory(1L);
        submit(1L, Map.of("categoryId", category, "requestId", "short"), 400);
        submit(1L, Map.of("categoryId", category, "requestId", key(), "questionCount", 9), 400);
        submit(1L, Map.of("categoryId", category, "requestId", key(), "difficulty", 4), 400);
    }

    @Test
    void generatedDraftCanBeDeletedAndRegeneratedWithoutPublishingOrLosingHistory() throws Exception {
        long category = ownCategory(1L);
        insertDocument(category);
        when(ai.isConfigured(anyLong())).thenReturn(true);
        Map<String,Object> question = new java.util.LinkedHashMap<>();
        question.put("title", "HashMap 扩容为什么需要重新计算索引？" + key());
        question.put("category", "Java 集合"); question.put("difficulty", 2);
        question.put("questionType", "SHORT_ANSWER");
        question.put("referenceAnswer", "扩容后数组长度变化，需要按新容量重新分布元素。");
        question.put("referencePoints", java.util.List.of("扩容改变数组长度", "旧桶元素重新分布"));
        question.put("rubric", Map.of("level1", "只能说扩容", "level3", "能说明索引变化", "level5", "能解释元素拆分"));
        question.put("followUps", java.util.List.of("扩容后如何重新分布？"));
        question.put("sourceChunkIds", java.util.List.of(1));
        question.put("citations", java.util.List.of("扩容时会重新计算索引"));
        when(ai.completeStructured(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull(), anyLong()))
                .thenReturn(json.writeValueAsString(Map.of("questions", java.util.List.of(question))));
        long first = submit(1L, Map.of("categoryId", category, "requestId", key()), 200).path("id").asLong();
        assertEquals("SUCCESS", settled(first, 1L).path("status").asText());
        long oldId = jdbc.queryForObject("SELECT id FROM interview_generated_question WHERE task_id=?", Long.class, first);
        mvc.perform(delete("/api/interview/generation/questions/" + oldId).header("Authorization", token(1)))
                .andExpect(status().isOk());
        long second = submit(1L, Map.of("categoryId", category, "requestId", key()), 200).path("id").asLong();
        assertEquals("SUCCESS", settled(second, 1L).path("status").asText());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM interview_generated_question WHERE task_id=? AND deleted=0", Integer.class, second));
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM interview_generated_question WHERE id=?", Integer.class, oldId));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM interview_generated_question WHERE task_id=?", String.class, second));
        long generated = jdbc.queryForObject("SELECT id FROM interview_generated_question WHERE task_id=?", Long.class, second);
        mvc.perform(post("/api/interview/generation/interviews").header("Authorization",token(1)).contentType("application/json").content(json.writeValueAsString(Map.of("questionIds",java.util.List.of(generated),"requestId",key()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
        mvc.perform(post("/api/interview/generation/questions/"+generated+"/publish").header("Authorization",token(1))).andExpect(status().isOk());
        mvc.perform(post("/api/interview/generation/interviews").header("Authorization",token(1)).contentType("application/json").content(json.writeValueAsString(Map.of("questionIds",java.util.List.of(generated),"requestId",key()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("ASKING"))
                .andExpect(jsonPath("$.data.currentQuestion.id").value("GENERATED-"+generated));

        mvc.perform(get("/api/interview/generation/tasks").header("Authorization", token(2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    private boolean containsCategory(JsonNode data, long categoryId) {
        for (JsonNode item : data) if (item.path("categoryId").asLong() == categoryId) return true;
        return false;
    }

    private long ownCategory(long owner) {
        jdbc.update("INSERT INTO doc_category(name,status,owner_id) VALUES(?,1,?)", "Phase1 知识库-" + UUID.randomUUID(), owner);
        return jdbc.queryForObject("SELECT MAX(id) FROM doc_category", Long.class);
    }

    private long insertDocument(long category) {
        jdbc.update("INSERT INTO doc_document(title,content,category_id,status,deleted) VALUES(?,?,?,1,0)",
                "HashMap 笔记", "HashMap 扩容时会重新计算索引，并把旧桶中的元素拆分到新数组。", category);
        return jdbc.queryForObject("SELECT MAX(id) FROM doc_document", Long.class);
    }

    private JsonNode submit(long uid, Map<String, Object> body, int status) throws Exception {
        var result = mvc.perform(post("/api/interview/generation/tasks").header("Authorization", token(uid))
                        .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().is(status)).andExpect(jsonPath("$.code").value(status)).andReturn();
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }

    private JsonNode settled(long taskId, long uid) throws Exception {
        for (int i = 0; i < 200; i++) {
            var result = mvc.perform(get("/api/interview/generation/tasks/" + taskId).header("Authorization", token(uid)))
                    .andExpect(status().isOk()).andReturn();
            JsonNode task = json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
            String status = task.path("status").asText();
            if ("SUCCESS".equals(status) || "FAILED".equals(status)) return task;
            Thread.sleep(20);
        }
        throw new AssertionError("generation task did not settle");
    }
}
