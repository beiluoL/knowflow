package com.knowflow.controller;

import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 验证 AI 建议 HTTP 接口的归属、周期兼容及认证。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties="knowflow.datasource.h2.url=jdbc:h2:mem:suggestion_batch2_http;DB_CLOSE_DELAY=-1;MODE=MySQL")
class AiSuggestionHttpContractTest {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtils tokens;

    @Test
    void cachedResponsesAreOwnedAndUnknownPeriodKeepsWeekFallback() throws Exception {
        for (long owner : new long[]{992201L,992202L}) {
            jdbc.update("INSERT INTO sys_user(id,username,password,deleted) VALUES(?,?,?,0)",owner,"suggestion-b2-"+owner,"");
            jdbc.update("INSERT INTO ai_learning_suggestion(user_id,period,suggestions,deleted) VALUES(?,?,?,0)",owner,"week","[{\"title\":\"owner-"+owner+"\"}]");
        }
        for (String role : java.util.List.of("USER","ADMIN")) {
            http.perform(get("/api/learning/suggestions").param("period","unknown").param("userId","992202")
                    .header("Authorization","Bearer "+tokens.generateToken(992201L,"suggestion-b2",role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.period").value("week"))
                    .andExpect(jsonPath("$.data.suggestions[0].title").value("owner-992201"));
        }
    }

    @Test
    void anonymousCannotReadOrRegenerate() throws Exception {
        http.perform(get("/api/learning/suggestions")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        http.perform(post("/api/learning/suggestions/regenerate")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }
}
