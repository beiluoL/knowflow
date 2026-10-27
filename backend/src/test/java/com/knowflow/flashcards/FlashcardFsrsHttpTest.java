package com.knowflow.flashcards;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
@TestPropertySource(
        properties = {
            "knowflow.datasource.h2.url=jdbc:h2:mem:flashcard_fsrs_v2;DB_CLOSE_DELAY=-1;MODE=MySQL",
            "knowflow.datasource.config-file=/tmp/knowflow-flashcard-fsrs-test-no-config.json"
        })
class FlashcardFsrsHttpTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtUtils jwt;
    @Autowired ObjectMapper json;

    String auth(long id) {
        return "Bearer " + jwt.generateToken(id, "fsrs-test" + id, "USER");
    }

    @Test
    void previewIsReadOnlyAndReviewIsIdempotentAndIsolated() throws Exception {
        var created =
                mvc.perform(
                                post("/api/learning/my/flashcards")
                                        .header("Authorization", auth(98771))
                                        .contentType("application/json")
                                        .content("{\"front\":\"FSRS Q\",\"back\":\"FSRS A\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();
        String path = "/api/learning/flashcards/" + id;
        mvc.perform(get(path + "/schedule"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mvc.perform(get(path + "/schedule").header("Authorization", auth(98772)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(get(path + "/schedule").header("Authorization", auth(98771)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("NEW"))
                .andExpect(jsonPath("$.data.revision").value(0))
                .andExpect(jsonPath("$.data.previews.length()").value(4));
        String body = "{\"rating\":3,\"revision\":0,\"requestId\":\"fsrs-test-operation-001\"}";
        var first =
                mvc.perform(
                                post(path + "/fsrs-review")
                                        .header("Authorization", auth(98771))
                                        .contentType("application/json")
                                        .content(body))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.revision").value(1))
                        .andExpect(jsonPath("$.data.algorithm").value("FSRS-6/java-fsrs-1.0.0"))
                        .andReturn();
        var repeated =
                mvc.perform(
                                post(path + "/fsrs-review")
                                        .header("Authorization", auth(98771))
                                        .contentType("application/json")
                                        .content(body))
                        .andExpect(status().isOk())
                        .andReturn();
        assertEquals(
                json.readTree(first.getResponse().getContentAsString()).get("data"),
                json.readTree(repeated.getResponse().getContentAsString()).get("data"));
        mvc.perform(
                        post(path + "/fsrs-review")
                                .header("Authorization", auth(98771))
                                .contentType("application/json")
                                .content(body.replace("001", "002")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
        mvc.perform(
                        post(path + "/review")
                                .param("quality", "5")
                                .header("Authorization", auth(98771)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
        mvc.perform(get("/api/learning/my/flashcards/" + id).header("Authorization", auth(98771)))
                .andExpect(jsonPath("$.data.reviewCount").value(1))
                .andExpect(jsonPath("$.data.nextReviewTime").isNotEmpty());
    }

    long createCard(long uid) throws Exception {
        var response =
                mvc.perform(
                                post("/api/learning/my/flashcards")
                                        .header("Authorization", auth(uid))
                                        .contentType("application/json")
                                        .content(
                                                "{\"front\":\"isolated"
                                                        + " fixture\",\"back\":\"answer\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        return json.readTree(response.getResponse().getContentAsString()).at("/data/id").asLong();
    }

    @Test
    void concurrentDifferentRequestsOnlyCommitOneRevision() throws Exception {
        long uid = 98773, id = createCard(uid);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Integer>>();
            for (int i = 0; i < 2; i++) {
                final int n = i;
                tasks.add(
                        () ->
                                mvc.perform(
                                                post("/api/learning/flashcards/"
                                                                + id
                                                                + "/fsrs-review")
                                                        .header("Authorization", auth(uid))
                                                        .contentType("application/json")
                                                        .content(
                                                                "{\"rating\":3,\"revision\":0,\"requestId\":\"concurrent-request-"
                                                                        + n
                                                                        + "\"}"))
                                        .andReturn()
                                        .getResponse()
                                        .getStatus());
            }
            var statuses = new java.util.ArrayList<Integer>();
            for (var future : pool.invokeAll(tasks)) statuses.add(future.get());
            java.util.Collections.sort(statuses);
            assertEquals(java.util.List.of(200, 409), statuses);
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM learning_event WHERE user_id=? AND"
                                    + " resource_type='FLASHCARD' AND resource_id=?",
                            Integer.class,
                            uid,
                            id));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void eventStorageFailureRollsBackScheduling() throws Exception {
        long uid = 98774, id = createCard(uid);
        jdbc.execute(
                "ALTER TABLE learning_event ADD CONSTRAINT reject_isolated_fsrs_event CHECK"
                        + " (resource_type <> 'FLASHCARD' OR resource_id <> "
                        + id
                        + ")");
        try {
            mvc.perform(
                            post("/api/learning/flashcards/" + id + "/fsrs-review")
                                    .header("Authorization", auth(uid))
                                    .contentType("application/json")
                                    .content(
                                            "{\"rating\":3,\"revision\":0,\"requestId\":\"rollback-fixture-001\"}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value(500));
            var row =
                    jdbc.queryForMap(
                            "SELECT fsrs_state,fsrs_revision,next_review_time,review_count FROM"
                                    + " learning_flashcard WHERE id=?",
                            id);
            assertNull(row.get("fsrs_state"));
            assertEquals(0, ((Number) row.get("fsrs_revision")).intValue());
            assertNull(row.get("next_review_time"));
            assertEquals(0, ((Number) row.get("review_count")).intValue());
        } finally {
            jdbc.execute("ALTER TABLE learning_event DROP CONSTRAINT reject_isolated_fsrs_event");
        }
    }

    @Test
    void settingsLimitsAndLegacyDueSurviveReadOnlyPreview() throws Exception {
        long uid = 98775, id = createCard(uid);
        var due = java.time.LocalDateTime.of(2030, 1, 2, 3, 4);
        jdbc.update(
                "UPDATE learning_flashcard SET next_review_time=?,last_review_time=?,review_count=5"
                        + " WHERE id=?",
                due,
                due.minusDays(1),
                id);
        mvc.perform(
                        get("/api/learning/flashcards/" + id + "/schedule")
                                .header("Authorization", auth(uid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conservativeInitialization").value(true));
        assertEquals(
                due,
                jdbc.queryForObject(
                                "SELECT next_review_time FROM learning_flashcard WHERE id=?",
                                java.sql.Timestamp.class,
                                id)
                        .toLocalDateTime());
        String settings =
                "{\"scheduling\":{\"desiredRetention\":0.91,\"maximumInterval\":1000,\"learningMinutes\":[1,10],\"relearningMinutes\":[10]},\"newCardLimit\":0,\"reviewLimit\":0,\"timezone\":\"Asia/Shanghai\",\"dayStartHour\":4}";
        mvc.perform(
                        put("/api/learning/my/flashcards/settings")
                                .header("Authorization", auth(uid))
                                .contentType("application/json")
                                .content(settings))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduling.desiredRetention").value(.91));
        mvc.perform(
                        post("/api/learning/flashcards/" + id + "/fsrs-review")
                                .header("Authorization", auth(uid))
                                .contentType("application/json")
                                .content(
                                        "{\"rating\":4,\"revision\":0,\"requestId\":\"limited-request-001\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
        mvc.perform(
                        put("/api/learning/my/flashcards/settings")
                                .header("Authorization", auth(uid))
                                .contentType("application/json")
                                .content(settings.replace("0.91", "1.10")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(
                        get("/api/learning/flashcards/" + id + "/schedule")
                                .header(
                                        "Authorization",
                                        "Bearer " + jwt.generateToken(1L, "admin", "ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void lateRequestReplaysOriginalResultWithoutAnotherReview() throws Exception {
        long uid = 98776, id = createCard(uid);
        String path = "/api/learning/flashcards/" + id + "/fsrs-review";
        String a = "{\"rating\":3,\"revision\":0,\"requestId\":\"late-request-operation-a\"}";
        var first =
                mvc.perform(
                                post(path)
                                        .header("Authorization", auth(uid))
                                        .contentType("application/json")
                                        .content(a))
                        .andExpect(status().isOk())
                        .andReturn();
        mvc.perform(
                        post(path)
                                .header("Authorization", auth(uid))
                                .contentType("application/json")
                                .content(
                                        a.replace("\"revision\":0", "\"revision\":1")
                                                .replace("operation-a", "operation-b")))
                .andExpect(status().isOk());
        var replay =
                mvc.perform(
                                post(path)
                                        .header("Authorization", auth(uid))
                                        .contentType("application/json")
                                        .content(a.replace("\"revision\":0", "\"revision\":2")))
                        .andExpect(status().isOk())
                        .andReturn();
        assertEquals(
                json.readTree(first.getResponse().getContentAsString()).get("data"),
                json.readTree(replay.getResponse().getContentAsString()).get("data"));
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM learning_event WHERE user_id=? AND"
                            + " resource_type='FLASHCARD' AND resource_id=?",
                        Integer.class,
                        uid,
                        id));
    }

    @Test
    void mappedReviewUpdatesMasteryAfterCommitWithoutCountingAgainAsSuccess() throws Exception {
        long uid = 98777, id = createCard(uid), kid = 990777;
        jdbc.update(
                "INSERT INTO kg_entity(id,doc_id,name,type,deleted) VALUES(?,1,'FSRS isolated"
                    + " mapped fixture','CONCEPT',0)",
                kid);
        jdbc.update(
                "INSERT INTO"
                    + " resource_knowledge_mapping(resource_type,resource_id,knowledge_id,source,status,deleted)"
                    + " VALUES('FLASHCARD',?,?,'MANUAL','ACCEPTED',0)",
                id,
                kid);
        mvc.perform(
                        post("/api/learning/flashcards/" + id + "/fsrs-review")
                                .header("Authorization", auth(uid))
                                .contentType("application/json")
                                .content(
                                        "{\"rating\":1,\"revision\":0,\"requestId\":\"mapped-review-fixture-001\"}"))
                .andExpect(status().isOk());
        var mastery =
                jdbc.queryForList(
                        "SELECT mastery_score,review_count FROM knowledge_mastery WHERE user_id=?"
                            + " AND knowledge_id=? AND deleted=0",
                        uid,
                        kid);
        assertEquals(1, mastery.size());
        assertEquals(0, ((Number) mastery.get(0).get("mastery_score")).intValue());
        assertEquals(0, ((Number) mastery.get(0).get("review_count")).intValue());
    }
}
