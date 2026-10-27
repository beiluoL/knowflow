package com.knowflow.interview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3H 模拟面试基础模式集成测试：真实 HTTP/服务/持久化链路，无 AI 参与。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "knowflow.datasource.h2.url=jdbc:h2:mem:phase3h-interview;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "knowflow.datasource.config-file=/tmp/knowflow-interview-followups-missing-config.json",
        "server.address=127.0.0.1",
        "knowflow.datasource.allow-runtime-switch=false",
        "knowflow.exam.timeout-scheduler.enabled=false",
        "knowflow.coding.seed.algorithm.enabled=false"
})
class MockInterviewIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtUtils jwt;
    @Autowired SysUserMapper userMapper;

    private SysUser alice;
    private SysUser bob;
    private String aliceToken;
    private String bobToken;

    @Test @org.springframework.transaction.annotation.Transactional
    void sourceThreeLevelFollowupsRemainInSessionSnapshotAfterSourceEdit() throws Exception {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO doc_category(name,owner_id,status) VALUES('私人追问教材',?,1)", alice.getId());
        long category = jdbc.queryForObject("SELECT MAX(id) FROM doc_category", Long.class);
        var tree = java.util.List.of(
                java.util.Map.of("level",1,"question","第一层问题","referenceAnswer","第一层答案"),
                java.util.Map.of("level",2,"question","第二层问题","referenceAnswer","第二层答案"),
                java.util.Map.of("level",3,"question","第三层问题","referenceAnswer","第三层答案"));
        String explanation = "教材正文\n来源与审核原记录：\n"
                + objectMapper.writeValueAsString(java.util.Map.of("followupTree", tree));
        String canonical = "followup-snapshot-" + alice.getId();
        jdbc.update("INSERT INTO quiz_question(title,content,answer,explanation,question_type,topic_code,tags,status,review_status,category_id,canonical_key) VALUES('三层追问教材','主问题','主参考答案',?,'SHORT_ANSWER','JAVA_COLLECTION','content-studio-persistent',1,'APPROVED',?,?)",
                explanation, category, canonical);
        JsonNode session = startSession(aliceToken, "{\"track\":\"CONTENT_JAVA\",\"questionCount\":1}");
        assertEquals("第一层问题", session.at("/questions/0/followups/0").asText());
        assertEquals("第三层问题", session.at("/questions/0/followups/2").asText());
        assertEquals("主参考答案", session.at("/questions/0/referencePoints/0").asText());
        jdbc.update("UPDATE quiz_question SET answer='已修改',explanation='已清除追问' WHERE canonical_key=?", canonical);
        mvc.perform(get("/api/interview/sessions/" + session.path("id").asLong()).header("Authorization",aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].referencePoints[0]").value("主参考答案"))
                .andExpect(jsonPath("$.data.questions[0].referencePoints[3]").value("第3层追问：第三层问题\n参考答案：第三层答案"))
                .andExpect(jsonPath("$.data.questions[0].followups[2]").value("第三层问题"));
    }

    @Test @org.springframework.transaction.annotation.Transactional
    void persistedStudioQuestionsAppearOnlyInReadablePublishedTracks() throws Exception {
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO doc_category(name,owner_id,status) VALUES('私人固化教材',?,1)",alice.getId());
        long cat=jdbc.queryForObject("SELECT MAX(id) FROM doc_category",Long.class);
        jdbc.update("INSERT INTO doc_document(title,content,category_id,status) VALUES('固化Java教材','完整原文',?,1)",cat);
        long doc=jdbc.queryForObject("SELECT MAX(id) FROM doc_document",Long.class);
        jdbc.update("INSERT INTO quiz_question(title,content,answer,question_type,topic_code,tags,status,review_status,category_id,doc_id,canonical_key) VALUES('固化集合契约','问题','哈希定位后仍须equals比较','SHORT_ANSWER','JAVA_COLLECTION','content-studio-persistent',1,'APPROVED',?,?,?)",cat,doc,"studio-test-"+alice.getId());
        jdbc.update("INSERT INTO quiz_question(title,content,answer,question_type,topic_code,tags,status,review_status,category_id,doc_id) VALUES('待审不应进入面试','问题','参考答案','SHORT_ANSWER','JAVA_COLLECTION','content-studio-persistent',0,'PENDING_REVIEW',?,?)",cat,doc);
        String raw=mvc.perform(get("/api/interview/tracks").header("Authorization",aliceToken)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(raw.contains("CONTENT_JAVA"));
        String others=mvc.perform(get("/api/interview/tracks").header("Authorization",bobToken)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertFalse(others.contains("CONTENT_JAVA"));
        JsonNode session=startSession(aliceToken,"{\"track\":\"CONTENT_JAVA\",\"questionCount\":1}");
        assertEquals("固化集合契约",session.at("/questions/0/title").asText());
        jdbc.update("UPDATE quiz_question SET answer='后续编辑' WHERE canonical_key=?","studio-test-"+alice.getId());
        mvc.perform(get("/api/interview/sessions/"+session.path("id").asLong()).header("Authorization",aliceToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.questions[0].referencePoints[0]").value("哈希定位后仍须equals比较"));
    }

    @BeforeEach
    void setUp() {
        alice = newUser("USER");
        bob = newUser("USER");
        aliceToken = token(alice);
        bobToken = token(bob);
    }

    private SysUser newUser(String role) {
        SysUser u = new SysUser();
        u.setUsername("p3h-iv-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        u.setPassword("test-only");
        u.setNickname("P3H IV");
        u.setRole(role);
        userMapper.insert(u);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getId(), null, List.of()));
        return u;
    }

    private String token(SysUser u) {
        return "Bearer " + jwt.generateToken(u.getId(), u.getUsername(), u.getRole());
    }

    private JsonNode startSession(String token, String body) throws Exception {
        String resp = mvc.perform(post("/api/interview/sessions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("BASIC"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(resp).path("data");
    }

    /** MockMvc content(String) 默认按 ISO-8859-1 编码，中文追问必须显式 UTF-8 字节。 */
    private org.springframework.test.web.servlet.ResultActions saveAnswer(String token, long sessionId, String body)
            throws Exception {
        return mvc.perform(put("/api/interview/sessions/" + sessionId + "/answers")
                .header("Authorization", token)
                .contentType(new MediaType(MediaType.APPLICATION_JSON, java.nio.charset.StandardCharsets.UTF_8))
                .content(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private String answerBody(JsonNode session, int idx, int score, String followupQ) {
        JsonNode q = session.path("questions").get(idx);
        String qid = q.path("id").asText();
        String followups = followupQ == null ? "[]"
                : "[{\"q\":" + JsonNodeText(followupQ) + ",\"a\":\"因为可见性禁止指令重排\"}]";
        return "{\"questionId\":" + JsonNodeText(qid)
                + ",\"answer\":\"我的回答要点一二三\","
                + "\"followups\":" + followups + ","
                + "\"selfScore\":" + score + ","
                + "\"selfComment\":\"细节还需加强\"}";
    }

    private static String JsonNodeText(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    @DisplayName("方向目录：9 个预置方向含 Kafka 且题池非空")
    void tracksListed() throws Exception {
        mvc.perform(get("/api/interview/tracks").header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(9))
                .andExpect(jsonPath("$.data[0].questionPoolSize").value(3))
                .andExpect(jsonPath("$.data[8].code").value("KAFKA"));
    }

    @Test
    @DisplayName("开始面试：默认 3 题快照；非法方向 400；题数被题池/上限钳制")
    void startSnapshotAndValidation() throws Exception {
        mvc.perform(post("/api/interview/sessions").header("Authorization", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(400));

        JsonNode session = startSession(aliceToken, "{\"track\":\"JVM\"}");
        assertEquals(3, session.path("questionCount").asInt());
        assertEquals(3, session.path("questions").size());
        assertNotNull(session.path("questions").get(0).path("rubric").path("level3").asText(null));

        JsonNode clamped = startSession(aliceToken, "{\"track\":\"JVM\",\"questionCount\":99}");
        assertEquals(3, clamped.path("questionCount").asInt(), "题池只有 3 题，应钳制为 3");
    }

    @Test
    @DisplayName("作答保存：合法保存推进进度，重复保存幂等；非法输入 400")
    void saveAnswerFlowAndValidation() throws Exception {
        JsonNode session = startSession(aliceToken, "{\"track\":\"JAVA_BASIC\"}");
        long id = session.path("id").asLong();
        String validFollowup = session.path("questions").get(0).path("followups").get(0).asText();

        saveAnswer(aliceToken, id, answerBody(session, 0, 4, validFollowup))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answeredCount").value(1));

        // 同题重复保存为覆盖，不产生第二条
        saveAnswer(aliceToken, id, answerBody(session, 0, 3, validFollowup))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answeredCount").value(1));

        // 题目不属于该会话
        String badQid = answerBody(session, 0, 3, null).replaceFirst("\"questionId\":\"[^\"]+\"", "\"questionId\":\"NOPE\"");
        saveAnswer(aliceToken, id, badQid)
                .andExpect(status().is4xxClientError());

        // 追问不在预置树
        String badFollow = "{\"questionId\":" + JsonNodeText(session.path("questions").get(1).path("id").asText())
                + ",\"answer\":\"x\",\"followups\":[{\"q\":\"不是预置追问\",\"a\":\"y\"}],\"selfScore\":3}";
        saveAnswer(aliceToken, id, badFollow)
                .andExpect(status().is4xxClientError());

        // 自评分越界
        String badScore = "{\"questionId\":" + JsonNodeText(session.path("questions").get(1).path("id").asText())
                + ",\"answer\":\"x\",\"selfScore\":6}";
        saveAnswer(aliceToken, id, badScore)
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("可选字段：自评备注留空允许保存；追问回答留空的条目被忽略")
    void optionalFieldsAccepted() throws Exception {
        JsonNode session = startSession(aliceToken, "{\"track\":\"AGENT_MCP\"}");
        long id = session.path("id").asLong();
        String qid = session.path("questions").get(0).path("id").asText();
        String body = "{\"questionId\":" + JsonNodeText(qid)
                + ",\"answer\":\"正常作答\","
                + "\"followups\":[{\"q\":" + JsonNodeText(session.path("questions").get(0).path("followups").get(0).asText())
                + ",\"a\":\"\"}],"
                + "\"selfScore\":4}";
        saveAnswer(aliceToken, id, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answeredCount").value(1))
                .andExpect(jsonPath("$.data.answers[0].selfComment").isEmpty())
                .andExpect(jsonPath("$.data.answers[0].followups.length()").value(0));
    }

    @Test
    @DisplayName("结束面试：缺题 400；全部作答后 FINISHED、均分与用时服务端计算，结束后不可再改")
    void finishFlow() throws Exception {
        JsonNode session = startSession(aliceToken, "{\"track\":\"JVM\"}");
        long id = session.path("id").asLong();

        mvc.perform(post("/api/interview/sessions/" + id + "/finish").header("Authorization", aliceToken))
                .andExpect(status().is4xxClientError());

        for (int i = 0; i < 3; i++) {
            saveAnswer(aliceToken, id, answerBody(session, i, 2 + i, null))
                    .andExpect(status().isOk());
        }

        mvc.perform(post("/api/interview/sessions/" + id + "/finish").header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINISHED"))
                .andExpect(jsonPath("$.data.avgSelfScore").value(3.0))
                .andExpect(jsonPath("$.data.durationSec").isNumber());

        // 结束后再作答被拒绝
        saveAnswer(aliceToken, id, answerBody(session, 0, 5, null))
                .andExpect(status().is4xxClientError());

        // 历史列表可见
        mvc.perform(get("/api/interview/sessions").header("Authorization", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].trackName").value("JVM"));
    }

    @Test
    @DisplayName("归属隔离：他人不能读取/作答/结束我的面试（统一 404）")
    void ownershipIsolation() throws Exception {
        JsonNode session = startSession(aliceToken, "{\"track\":\"RAG\"}");
        long id = session.path("id").asLong();

        mvc.perform(get("/api/interview/sessions/" + id).header("Authorization", bobToken))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/interview/sessions/" + id + "/finish").header("Authorization", bobToken))
                .andExpect(status().isNotFound());
        saveAnswer(bobToken, id, answerBody(session, 0, 3, null))
                .andExpect(status().isNotFound());

        // 匿名拒绝
        mvc.perform(get("/api/interview/sessions/" + id))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void concurrentDifferentQuestionSavePreservesCommittedAnswer() throws Exception {
        JsonNode session = startSession(aliceToken, "{\"track\":\"JAVA_BASIC\",\"questionCount\":2}");
        long id = session.path("id").asLong();
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("SELECT id FROM mock_interview_session WHERE id=? FOR UPDATE")) {
                lock.setLong(1,id); lock.executeQuery().close();
            }
            var pending = executor.submit(() -> saveAnswer(aliceToken,id,answerBody(session,1,4,null)).andReturn().getResponse().getStatus());
            Thread.sleep(200);
            try (var edit = connection.prepareStatement("UPDATE mock_interview_session SET answers_json=? WHERE id=?")) {
                edit.setString(1,"["+answerBody(session,0,3,null)+"]"); edit.setLong(2,id); edit.executeUpdate();
            }
            connection.commit();
            assertEquals(200,pending.get(5,java.util.concurrent.TimeUnit.SECONDS));
            mvc.perform(get("/api/interview/sessions/"+id).header("Authorization",aliceToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.answeredCount").value(2));
        } finally { executor.shutdownNow(); }
    }

}
