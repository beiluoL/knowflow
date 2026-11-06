package com.knowflow.controller;

import com.knowflow.support.ActiveIdentityFixtures;
import com.knowflow.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 设置持久化迁入所属服务后，HTTP 契约保持稳定。 */
@SpringBootTest(properties = "knowflow.datasource.h2.url=jdbc:h2:mem:v32_settings;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class UserSettingsBoundaryTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtils jwt;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void users() {
        ActiveIdentityFixtures.ensureActive(jdbc, 98301L, "settings-owner", "USER");
        ActiveIdentityFixtures.ensureActive(jdbc, 98302L, "settings-other", "USER");
        jdbc.update("DELETE FROM user_background_preset WHERE user_id IN (98301,98302)");
        jdbc.update("DELETE FROM sys_user_pref WHERE user_id IN (98301,98302)");
    }

    private String token(long id) { return "Bearer " + jwt.generateToken(id, "settings", "USER"); }

    @Test
    void presetsUpsertWithinOwnerAndForeignDeleteIsNoOp() throws Exception {
        String path = "/api/settings/background-presets";
        String initial = mvc.perform(post(path).header("Authorization", token(98301))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"desk\",\"bgType\":\"color\",\"bgValue\":\"blue\",\"userId\":98302}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(98301)).andReturn().getResponse().getContentAsString();
        long id = json.readTree(initial).path("data").path("id").asLong();
        mvc.perform(post(path).header("Authorization", token(98301)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"desk\",\"bgType\":\"gradient\",\"bgValue\":\"green\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        mvc.perform(delete(path + "/" + id).header("Authorization", token(98302)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get(path).header("Authorization", token(98302)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get(path).header("Authorization", token(98301)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].bgValue").value("green"));
    }

    @Test
    void preferenceReadDoesNotPersistAndPartialSavePreservesFields() throws Exception {
        String path = "/api/learning/prefs";
        mvc.perform(get(path).header("Authorization", token(98301)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.focusMinutes").value(25));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_pref WHERE user_id=98301", Integer.class));
        mvc.perform(post(path).header("Authorization", token(98301)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"focusMinutes\":40,\"soundEnabled\":0,\"userId\":98302}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(98301));
        mvc.perform(post(path).header("Authorization", token(98301)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"night\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.focusMinutes").value(40))
                .andExpect(jsonPath("$.data.soundEnabled").value(0)).andExpect(jsonPath("$.data.theme").value("night"));
        mvc.perform(get(path).header("Authorization", token(98302)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.focusMinutes").value(25));
    }
}
