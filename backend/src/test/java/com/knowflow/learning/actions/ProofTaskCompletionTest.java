package com.knowflow.learning.actions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.reward.WalletService;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实计划、真实资源、真实HTTP：证明任务不能用任何手动状态入口替代学习提交。 */
@SpringBootTest(properties = "knowflow.datasource.h2.url=jdbc:h2:mem:phaseg_proof;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc
@Import(FakeAiConfiguration.class)
class ProofTaskCompletionTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired JwtUtils jwt;
    @Autowired SysUserMapper users;
    @Autowired QuizQuestionMapper questions;
    @Autowired DocCategoryMapper categories;
    @Autowired ChatConversationMapper conversations;
    @Autowired LearningTaskMapper legacy;
    @Autowired DailyLearningPlanService plans;
    @Autowired WalletService wallets;
    @Autowired LearningActionProgress progress;

    private record Fixture(long user, long task, long resource, String type) {}

    private long user() {
        var user = new SysUser(); user.setUsername("phaseg-" + UUID.randomUUID());
        user.setPassword("test-only"); user.setRole("USER"); users.insert(user);
        wallets.getOrCreate(user.getId());
        return user.getId();
    }

    private String token(long user, String role) { return "Bearer " + jwt.generateToken(user, "phaseg", role); }

    private JsonNode send(MockHttpServletRequestBuilder request, Long user, String role, int code) throws Exception {
        if (user != null) request.header("Authorization", token(user, role));
        var response = http.perform(request).andExpect(status().is(code)).andExpect(jsonPath("$.code").value(code))
                .andReturn().getResponse();
        return json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object value) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsBytes(value));
    }

    private Fixture fixture(String type) throws Exception {
        return fixture(type, "TRUE_FALSE");
    }

    private Fixture fixture(String type, String questionType) throws Exception {
        long user = user(), resource;
        if (type.equals("FLASHCARD")) {
            resource = send(body(post("/api/learning/my/flashcards"), Map.of("front", "G1 CAS回忆", "back", "原子比较交换")), user, "USER", 200).path("id").asLong();
            db.update("UPDATE learning_flashcard SET next_review_time=? WHERE id=?", Timestamp.valueOf(LocalDateTime.now().minusDays(1)), resource);
        } else {
            var category = new DocCategory(); category.setOwnerId(user); category.setName("G1私有题库"); category.setStatus(1); categories.insert(category);
            var question = new QuizQuestion(); question.setTitle("G1 CAS原子操作"); question.setContent("CAS是原子比较交换。");
            question.setQuestionType(questionType); question.setAnswer("true"); question.setStatus(1); question.setReviewStatus("APPROVED"); question.setCategoryId(category.getId()); questions.insert(question);
            resource = question.getId();
        }
        var request = new PlanModels.Request(); request.setAvailableMinutes(30); request.setIncludeInterview(false); request.setUseAi(false);
        var draft = plans.preview(user, request);
        long selectedResource = resource;
        var item = draft.tasks().stream().filter(i -> type.equals(i.resourceType()) && Objects.equals(i.resourceId(), selectedResource)).findFirst().orElseThrow();
        var confirmed = plans.confirm(user, draft.planId(), confirmation(draft, List.of(item)));
        return new Fixture(user, confirmed.createdTasks().get(0).id(), resource, type);
    }

    private PlanModels.ConfirmRequest confirmation(PlanModels.Draft draft, List<PlanModels.Item> items) {
        var request = new PlanModels.ConfirmRequest(); request.setVersion(draft.version()); request.setConfirmationToken(draft.confirmationToken());
        request.setIdempotencyKey(UUID.randomUUID().toString());
        request.setTasks(items.stream().map(i -> new PlanModels.EditedItem(i.key(), i.title(), i.estimatedMinutes(), i.priority())).toList());
        return request;
    }

    /** 第五条真实经过Chat意图→草案→相同确认Controller，不绕开其版本/确认令牌。 */
    private MockHttpServletRequestBuilder manual(Fixture fixture, int entry, int target) throws Exception {
        return switch (entry) {
            case 1 -> put("/api/tasks/" + fixture.task + "/status").param("status", "" + target);
            case 2 -> body(put("/api/tasks/" + fixture.task), Map.of("status", target));
            case 3 -> put("/api/tasks/" + fixture.task + "/stage").param("stage", target == 1 ? "2" : "0");
            case 4 -> put("/api/learning/tasks/" + fixture.task + "/status").param("status", "" + target).param("source", "TASK");
            case 5 -> {
                PlanModels.Draft draft;
                if (target == 1) {
                    var conversation = new ChatConversation(); conversation.setUserId(fixture.user); conversation.setTitle("G1状态确认"); conversation.setMessageCount(0); conversations.insert(conversation);
                    var answer = send(body(post("/api/chat/learning-actions"), Map.of("conversationId", conversation.getId(), "message", "我已经完成了G1任务", "selectedTaskId", fixture.task, "selectedTaskSource", "TASK")), fixture.user, "USER", 200);
                    draft = plans.get(fixture.user, answer.path("planId").asText());
                } else draft = plans.previewStatus(fixture.user, fixture.task, "TASK", target, new PlanModels.Request());
                yield body(post("/api/learning/action-plans/" + draft.planId() + "/confirm"), confirmation(draft, draft.tasks()));
            }
            default -> throw new IllegalArgumentException();
        };
    }

    private Map<String, Object> snapshot(Fixture fixture) {
        var result = new LinkedHashMap<String, Object>();
        // Task无独立完成时间列；同时检查stage和update_time，不能提前写完成状态或更新时间。
        result.put("task", db.queryForMap("SELECT status,stage,update_time FROM task WHERE id=?", fixture.task));
        result.put("events", db.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=?", Long.class, fixture.user));
        result.put("proof", db.queryForObject("SELECT COUNT(*) FROM learning_task_evidence WHERE task_id=?", Long.class, fixture.task));
        result.put("ledger", db.queryForObject("SELECT COUNT(*) FROM reward_ledger WHERE user_id=?", Long.class, fixture.user));
        result.put("wallet", db.queryForMap("SELECT xp,coin FROM wallet WHERE user_id=?", fixture.user));
        return result;
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3, 4, 5})
    void fiveManualEntrypointsCannotCompleteARegisteredProofTask(int entry) throws Exception {
        var fixture = fixture("FLASHCARD"); var before = snapshot(fixture);
        send(manual(fixture, entry, 1), fixture.user, "USER", 409);
        assertEquals(before, snapshot(fixture));
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3, 4, 5})
    void anonymousOtherAndAdminCannotWritePrivateTasks(int entry) throws Exception {
        var fixture = fixture("FLASHCARD"); var before = snapshot(fixture); long other = user();
        send(manual(fixture, entry, 1), null, "USER", 401);
        for (String role : List.of("USER", "ADMIN")) send(manual(fixture, entry, 1), other, role, 404);
        assertEquals(before, snapshot(fixture));
    }

    @ParameterizedTest @ValueSource(strings = {"FLASHCARD", "QUIZ"})
    void onlyTheRealLearningSubmissionCompletesAndLinksItsTask(String type) throws Exception {
        var fixture = fixture(type);
        send(manual(fixture, 1, 1), fixture.user, "USER", 409);
        submit(fixture);
        assertEquals(1, db.queryForObject("SELECT status FROM task WHERE id=?", Integer.class, fixture.task));
        var proof = db.queryForMap("SELECT e.id,e.event_type,e.resource_id,e.user_id FROM learning_task_evidence p JOIN learning_event e ON e.id=p.event_id WHERE p.task_id=?", fixture.task);
        assertEquals(fixture.user, ((Number) proof.get("user_id")).longValue());
        assertEquals(fixture.resource, ((Number) proof.get("resource_id")).longValue());
        assertEquals(type.equals("QUIZ") ? "QUESTION_ANSWERED" : "FLASHCARD_REVIEWED", proof.get("event_type"));
        var after = snapshot(fixture);
        send(manual(fixture, 1, 0), fixture.user, "USER", 409);
        assertEquals(after, snapshot(fixture), "禁止手动撤销，不能删除或替换已经存在的学习证明");
    }

    private void submit(Fixture fixture) throws Exception {
        if (fixture.type.equals("QUIZ")) {
            send(body(post("/api/quiz/submit"), Map.of("requestId", UUID.randomUUID().toString(), "answers", List.of(Map.of("questionId", fixture.resource, "userAnswer", "true")))), fixture.user, "USER", 200);
        } else {
            var schedule = send(get("/api/learning/flashcards/" + fixture.resource + "/schedule"), fixture.user, "USER", 200);
            send(body(post("/api/learning/flashcards/" + fixture.resource + "/fsrs-review"), Map.of("rating", 3, "revision", schedule.path("revision").asLong(), "settingsRevision", schedule.path("settingsRevision").asLong(), "requestId", UUID.randomUUID().toString(), "sessionId", "phaseg-real-fsrs-review")), fixture.user, "USER", 200);
        }
    }

    @Test void plainTodoToolAndOldInterviewReminderKeepManualCompletion() throws Exception {
        for (String title : List.of("普通Todo", "面试复习：CAS", "打开工具整理资料")) {
            long user = user(); long task = send(body(post("/api/tasks"), Map.of("title", title, "scheduledDate", java.time.LocalDate.now().toString())), user, "USER", 200).asLong();
            var fixture = new Fixture(user, task, 0, "TOOL");
            for (int entry = 1; entry <= 5; entry++) {
                db.update("UPDATE task SET status=0,stage=0 WHERE id=?", task);
                send(manual(fixture, entry, 1), user, "USER", 200);
            }
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM learning_task_evidence WHERE task_id=?", Integer.class, task));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"null", "broken", "identity", "missingIdentity"})
    void brokenMetadataAndMismatchedRegistrationCannotFallBackToTodo(String damage) throws Exception {
        var fixture = fixture("FLASHCARD");
        switch (damage) {
            case "null" -> db.update("UPDATE task SET learning_metadata=NULL WHERE id=?", fixture.task);
            case "broken" -> db.update("UPDATE task SET learning_metadata='{' WHERE id=?", fixture.task);
            case "identity" -> db.update("UPDATE learning_action_identity SET resource_key='wrong' WHERE task_id=?", fixture.task);
            case "missingIdentity" -> db.update("DELETE FROM learning_action_identity WHERE task_id=?", fixture.task);
        }
        var before = snapshot(fixture);
        send(manual(fixture, 1, 1), fixture.user, "USER", 409);
        assertEquals(before, snapshot(fixture));
    }

    @Test void deletedTaskIsNotWritable() throws Exception {
        var fixture = fixture("FLASHCARD"); db.update("UPDATE task SET deleted=1 WHERE id=?", fixture.task);
        var before = snapshot(fixture); send(manual(fixture, 1, 1), fixture.user, "USER", 404); assertEquals(before, snapshot(fixture));
    }

    @Test void dtoStageAloneCannotDisplayAProofTaskAsCompleted() throws Exception {
        var fixture = fixture("FLASHCARD"); var before = snapshot(fixture);
        send(body(put("/api/tasks/" + fixture.task), Map.of("stage", 2)), fixture.user, "USER", 409);
        assertEquals(before, snapshot(fixture));
    }

    @Test void validToolMetadataWithoutAProofProtocolStillAllowsManualCompletion() throws Exception {
        long user = user(); long task = send(body(post("/api/tasks"), Map.of("title", "历史工具事项")), user, "USER", 200).asLong();
        db.update("UPDATE task SET learning_plan_ref='legacy-tool',learning_metadata=? WHERE id=?", "{\"resourceType\":\"TOOL\",\"resourceId\":null,\"estimatedMinutes\":10}", task);
        send(put("/api/tasks/" + task + "/status").param("status", "1"), user, "USER", 200);
    }

    @Test void malformedToolMetadataIsRejectedInsteadOfBeingTreatedAsPlainTodo() throws Exception {
        long user = user(); long task = send(body(post("/api/tasks"), Map.of("title", "损坏工具事项")), user, "USER", 200).asLong();
        db.update("UPDATE task SET learning_plan_ref='legacy-tool',learning_metadata=? WHERE id=?", "{\"resourceType\":\"TOOL\",\"resourceId\":null,\"estimatedMinutes\":\"bad\"}", task);
        send(put("/api/tasks/" + task + "/status").param("status", "1"), user, "USER", 409);
        assertEquals(0, db.queryForObject("SELECT status FROM task WHERE id=?", Integer.class, task));
    }

    @ParameterizedTest @ValueSource(strings = {"FLASHCARD", "QUIZ"})
    void removedResourceDoesNotHideTaskHistoryOrPermitManualCompletion(String type) throws Exception {
        var fixture = fixture(type);
        db.update("UPDATE " + (type.equals("QUIZ") ? "quiz_question" : "learning_flashcard") + " SET deleted=1 WHERE id=?", fixture.resource);
        var before = snapshot(fixture);
        var tasks = send(get("/api/tasks").param("smart", "all"), fixture.user, "USER", 200);
        assertTrue(tasks.toString().contains("completionRequirement"));
        send(manual(fixture, 1, 1), fixture.user, "USER", 409);
        assertEquals(before, snapshot(fixture));
    }

    @Test void revokedQuizAccessDoesNotAllowManualProofCompletion() throws Exception {
        var fixture = fixture("QUIZ");
        db.update("UPDATE doc_category SET status=0 WHERE id=(SELECT category_id FROM quiz_question WHERE id=?)", fixture.resource);
        var before = snapshot(fixture);
        send(get("/api/quiz/questions").param("questionId", "" + fixture.resource), fixture.user, "USER", 403);
        send(manual(fixture, 1, 1), fixture.user, "USER", 409);
        var projected=send(get("/api/tasks").param("smart","all"),fixture.user,"USER",200);
        var owned=java.util.stream.StreamSupport.stream(projected.spliterator(),false).filter(t->t.path("id").asLong()==fixture.task).findFirst().orElseThrow();
        assertFalse(owned.path("completionRequirement").path("available").asBoolean(true));
        assertEquals(before, snapshot(fixture));
    }

    @ParameterizedTest @ValueSource(strings={"SHORT_ANSWER","CODE"})
    void historicalSubjectiveQuizWithoutCompletionProtocolRemainsManual(String type) throws Exception {
        var fixture=fixture("QUIZ",type);
        var metadata=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(db.queryForObject("SELECT learning_metadata FROM task WHERE id=?",String.class,fixture.task));metadata.remove("completionProtocol");
        db.update("UPDATE task SET learning_metadata=? WHERE id=?",json.writeValueAsString(metadata),fixture.task);
        send(manual(fixture,1,1),fixture.user,"USER",200);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM learning_task_evidence WHERE task_id=?",Integer.class,fixture.task));
    }

    @ParameterizedTest @ValueSource(strings={"EDITOR","READER"})
    void sharedKnowledgeRolesKeepResourceAccessButDoNotGrantOtherTaskWrites(String role) throws Exception {
        var fixture=fixture("QUIZ");long originalOwner=user();long category=db.queryForObject("SELECT category_id FROM quiz_question WHERE id=?",Long.class,fixture.resource);
        db.update("UPDATE doc_category SET owner_id=? WHERE id=?",originalOwner,category);
        db.update("INSERT INTO kb_member(category_id,user_id,role,status,deleted) VALUES(?,?,?,1,0)",category,fixture.user,role);
        var readable=send(get("/api/tasks").param("smart","all"),fixture.user,"USER",200);
        assertTrue(java.util.stream.StreamSupport.stream(readable.spliterator(),false).filter(t->t.path("id").asLong()==fixture.task).findFirst().orElseThrow().path("completionRequirement").path("available").asBoolean());
        send(get("/api/quiz/questions").param("questionId",""+fixture.resource),fixture.user,"USER",200);
        send(manual(fixture,1,1),originalOwner,"USER",404);
        db.update("UPDATE kb_member SET status=0 WHERE category_id=? AND user_id=?",category,fixture.user);
        var revoked=send(get("/api/tasks").param("smart","all"),fixture.user,"USER",200);
        assertFalse(java.util.stream.StreamSupport.stream(revoked.spliterator(),false).filter(t->t.path("id").asLong()==fixture.task).findFirst().orElseThrow().path("completionRequirement").path("available").asBoolean(true));
        send(manual(fixture,1,1),fixture.user,"USER",409);
    }

    @Test void changingQuestionTypeCannotTurnAnExistingProofTaskIntoManualTodo() throws Exception {
        var fixture = fixture("QUIZ"); var before = snapshot(fixture);
        db.update("UPDATE quiz_question SET question_type='SHORT_ANSWER' WHERE id=?", fixture.resource);
        send(manual(fixture, 1, 1), fixture.user, "USER", 409);
        assertEquals(before, snapshot(fixture));
    }

    @Test void aNewSubjectivePracticeWithoutProofProtocolRemainsManual() throws Exception {
        var fixture = fixture("QUIZ", "SHORT_ANSWER");
        send(manual(fixture, 1, 1), fixture.user, "USER", 200);
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM learning_task_evidence WHERE task_id=?", Integer.class, fixture.task));
    }

    @Test void unsupportedOrOldOrForeignEventsCannotCompleteTheTask() throws Exception {
        var fixture = fixture("FLASHCARD"); var before = snapshot(fixture);
        var event = new LearningEvent(); event.setId(-1L); event.setUserId(fixture.user);
        event.setResourceType("FLASHCARD"); event.setResourceId(fixture.resource); event.setEventType("FLASHCARD_REVIEWED");
        event.setCreateTime(LocalDateTime.now()); event.setMetadata("{\"algorithm\":\"SM2\"}");
        // 这些是未落库的负向信号，不作为真实学习事件/证据；正向链路只使用submit()。
        progress.onEvidence(new LearningActionProgress.Committed(event));
        event.setMetadata("{\"algorithm\":\"FSRS-6\"}"); event.setCreateTime(LocalDateTime.now().minusDays(2));
        progress.onEvidence(new LearningActionProgress.Committed(event));
        event.setCreateTime(LocalDateTime.now()); event.setUserId(user());
        progress.onEvidence(new LearningActionProgress.Committed(event));
        assertEquals(before, snapshot(fixture));
    }

    @Test void taskAndLegacyLearningTaskWithSameNumericIdStaySeparate() throws Exception {
        Fixture fixture;
        do { fixture = fixture("FLASHCARD"); } while (legacy.selectById(fixture.task) != null);
        var task = new LearningTask(); task.setId(fixture.task); task.setUserId(fixture.user); task.setTitle("同数字ID的旧学习任务"); task.setStatus(0); legacy.insert(task);
        send(put("/api/learning/tasks/" + fixture.task + "/status").param("source", "LEARNING").param("status", "1"), fixture.user, "USER", 200);
        assertEquals(0, db.queryForObject("SELECT status FROM task WHERE id=?", Integer.class, fixture.task));
        assertEquals(1, legacy.selectById(fixture.task).getStatus());
    }

    public static class RejectProofInsert implements org.h2.api.Trigger {
        @Override public void fire(java.sql.Connection connection, Object[] oldRow, Object[] newRow) throws java.sql.SQLException {
            throw new java.sql.SQLException("phaseg isolated proof persistence failure");
        }
    }

    @Test void postCommitFailureKeepsRealAnswerAndLeavesDiagnosablePendingProof() throws Exception {
        var fixture = fixture("QUIZ");
        var logger = (Logger) LoggerFactory.getLogger(LearningActionProgress.class);
        var captured = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>(); captured.start(); logger.addAppender(captured);
        // 只在本类私有H2中临时注入失败；不改任何仓库Schema或用户数据库。
        db.execute("CREATE TRIGGER phaseg_reconcile_failure BEFORE INSERT ON learning_task_evidence FOR EACH ROW CALL 'com.knowflow.learning.actions.ProofTaskCompletionTest$RejectProofInsert'");
        try {
            submit(fixture);
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM quiz_answer_record WHERE user_id=?", Integer.class, fixture.user));
            long event = db.queryForObject("SELECT id FROM learning_event WHERE user_id=? AND event_type='QUESTION_ANSWERED'", Long.class, fixture.user);
            assertEquals(0, db.queryForObject("SELECT status FROM task WHERE id=?", Integer.class, fixture.task));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM learning_task_evidence WHERE task_id=?", Integer.class, fixture.task));
            assertTrue(captured.list.stream().anyMatch(e -> e.getFormattedMessage().contains("LEARNING_TASK_UNRECONCILED") && e.getFormattedMessage().contains("eventId=" + event)));
        } finally {
            db.execute("DROP TRIGGER phaseg_reconcile_failure"); logger.detachAppender(captured); captured.stop();
        }
    }
}
