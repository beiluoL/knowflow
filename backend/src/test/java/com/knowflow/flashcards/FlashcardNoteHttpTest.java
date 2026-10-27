package com.knowflow.flashcards;

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
            "knowflow.datasource.h2.url=jdbc:h2:mem:flashcard_notes_v2;DB_CLOSE_DELAY=-1;MODE=MySQL",
            "knowflow.datasource.config-file=/tmp/knowflow-flashcard-note-test-no-config.json"
        })
class FlashcardNoteHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtils jwt;
    @Autowired ObjectMapper json;

    String auth(long id) {
        return "Bearer " + jwt.generateToken(id, "note-test" + id, "USER");
    }

    @Test
    void independentCardsStableIdentityAndOwnership() throws Exception {
        String body =
                "{\"type\":\"CLOZE\",\"front\":\"{{c1::可见性}} 与 {{c2::有序性}}\",\"back\":\"解释\"}";
        mvc.perform(
                        post("/api/learning/my/flashcard-notes")
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        var created =
                mvc.perform(
                                post("/api/learning/my/flashcard-notes")
                                        .header("Authorization", auth(98761))
                                        .contentType("application/json")
                                        .content(body))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(200))
                        .andExpect(jsonPath("$.data.cards.length()").value(2))
                        .andReturn();
        var note = json.readTree(created.getResponse().getContentAsString()).get("data");
        long id = note.get("id").asLong();
        long first = note.get("cards").get(0).get("id").asLong();
        mvc.perform(
                        get("/api/learning/my/flashcard-notes/" + id)
                                .header("Authorization", auth(98762)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(
                        delete("/api/learning/my/flashcards/" + first)
                                .header("Authorization", auth(98761)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
        mvc.perform(
                        post("/api/learning/flashcards/" + first + "/review")
                                .param("quality", "5")
                                .header("Authorization", auth(98761)))
                .andExpect(status().isOk());
        var before =
                json.readTree(
                                mvc.perform(
                                                get("/api/learning/my/flashcards/" + first)
                                                        .header("Authorization", auth(98761)))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .get("data");
        String edit =
                "{\"type\":\"CLOZE\",\"front\":\"{{c1::visibility}} 与"
                        + " {{c2::order}}\",\"back\":\"解释\",\"revision\":1}";
        mvc.perform(
                        put("/api/learning/my/flashcard-notes/" + id)
                                .header("Authorization", auth(98761))
                                .contentType("application/json")
                                .content(edit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cards[0].id").value(first))
                .andExpect(jsonPath("$.data.revision").value(2));
        var after =
                json.readTree(
                                mvc.perform(
                                                get("/api/learning/my/flashcards/" + first)
                                                        .header("Authorization", auth(98761)))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .get("data");
        org.junit.jupiter.api.Assertions.assertEquals(
                before.get("nextReviewTime"), after.get("nextReviewTime"));
        org.junit.jupiter.api.Assertions.assertEquals(
                before.get("reviewCount"), after.get("reviewCount"));
        String remove =
                "{\"type\":\"CLOZE\",\"front\":\"{{c1::visibility}}\",\"back\":\"解释\",\"revision\":2}";
        mvc.perform(
                        put("/api/learning/my/flashcard-notes/" + id)
                                .header("Authorization", auth(98761))
                                .contentType("application/json")
                                .content(remove))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
        mvc.perform(
                        put("/api/learning/my/flashcard-notes/" + id)
                                .header("Authorization", auth(98761))
                                .contentType("application/json")
                                .content(
                                        remove.replace(
                                                "\"revision\":2",
                                                "\"revision\":2,\"confirmHistoryImpact\":true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cards.length()").value(1));
    }
}
