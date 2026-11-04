package com.knowflow.controller;

import com.knowflow.utils.JwtUtils;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class WeeklyReportHttpTest {
    private static final String ROUTE = "/api/learning/weekly-report";

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwt;
    @Autowired private SysUserMapper users;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void currentHistoryAndRegenerationUseAuthenticatedOwnersReports() throws Exception {
        SysUser owner = createUser();
        SysUser other = createUser();
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        insertReport(owner.getId(), monday, "owner report");
        insertReport(other.getId(), monday, "other report");
        insertReport(owner.getId(), monday.minusWeeks(8), "outside history window");
        String bearer = "Bearer " + jwt.generateToken(owner.getId(), owner.getUsername(), "USER");

        mvc.perform(get(ROUTE)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get(ROUTE).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.summary").value("owner report"))
                .andExpect(jsonPath("$.data.weekStart").value(monday.toString()));
        mvc.perform(get(ROUTE + "/list").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].summary").value("owner report"));
        mvc.perform(post(ROUTE + "/generate").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.achievements").isEmpty())
                .andExpect(jsonPath("$.data.suggestions").isEmpty())
                .andExpect(jsonPath("$.data.studyMinutes").value(0));
        assertEquals("other report", jdbc.queryForObject(
                "SELECT summary FROM weekly_report WHERE user_id=? AND week_start=?",
                String.class, other.getId(), monday));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM weekly_report WHERE user_id=? AND week_start=?",
                Integer.class, owner.getId(), monday));
    }

    private SysUser createUser() {
        SysUser user = new SysUser();
        user.setUsername("weekly-" + UUID.randomUUID());
        user.setPassword("unused");
        user.setRole("USER");
        users.insert(user);
        return user;
    }

    private void insertReport(long userId, LocalDate monday, String summary) {
        jdbc.update("INSERT INTO weekly_report(user_id,week_start,week_end,summary) VALUES (?,?,?,?)",
                userId, monday, monday.plusDays(6), summary);
    }
}
