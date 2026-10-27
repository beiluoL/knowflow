package com.knowflow.apps;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.entity.SysUser;
import com.knowflow.entity.UserApp;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.mapper.UserAppMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties = "knowflow.datasource.h2.url=jdbc:h2:mem:exam_install_regression;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class ExamAppInstallIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtils jwt;
    @Autowired SysUserMapper users;
    @Autowired UserAppMapper apps;
    @Autowired OfficialAppRegistry registry;
    Long owner, other, admin;

    @BeforeEach void setup() {
        owner = create("USER"); other = create("USER"); admin = create("ADMIN");
    }
    Long create(String role) {
        SysUser user = new SysUser(); user.setUsername("exam-install-" + System.nanoTime());
        user.setPassword(""); user.setRole(role); users.insert(user); return user.getId();
    }
    String token(Long id, String role) { return "Bearer " + jwt.generateToken(id, "exam-test", role); }
    long count(Long id) { return apps.selectCount(new LambdaQueryWrapper<UserApp>().eq(UserApp::getUserId,id).eq(UserApp::getAppKey,"exam")); }

    @Test void examInstallPersistsOnceAndUsesAuthenticatedOwner() throws Exception {
        for (int i=0; i<2; i++) {
            mvc.perform(put("/api/apps/user-apps/exam").header("Authorization",token(owner,"USER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"installed\":true,\"enabled\":true,\"sortOrder\":5,\"userId\":" + other + "}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.appKey").value("exam"))
                    .andExpect(jsonPath("$.data.installed").value(true)).andExpect(jsonPath("$.data.enabled").value(true));
        }
        assertEquals(1,count(owner)); assertEquals(0,count(other));
        mvc.perform(get("/api/apps/user-apps").header("Authorization",token(owner,"USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[?(@.appKey=='exam')].enabled").value(org.hamcrest.Matchers.hasItem(true)));
        mvc.perform(put("/api/apps/user-apps/exam").header("Authorization",token(owner,"USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"installed\":true,\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enabled").value(false));
        assertEquals(1,count(owner)); assertEquals(0,count(other));
    }
    @Test void anonymousCannotInstallAndUnknownKeysRemainRejected() throws Exception {
        mvc.perform(put("/api/apps/user-apps/exam").contentType(MediaType.APPLICATION_JSON).content("{\"installed\":true}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mvc.perform(put("/api/apps/user-apps/unknown-app").header("Authorization",token(owner,"USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"installed\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(2006)); // existing BusinessException HTTP compatibility
        assertEquals(0,count(owner));
    }
    @Test void adminInstallsOnlyTheirOwnExam() throws Exception {
        mvc.perform(put("/api/apps/user-apps/exam").header("Authorization",token(admin,"ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"installed\":true,\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enabled").value(true));
        assertEquals(1,count(admin)); assertEquals(0,count(owner));
    }
    @Test void backendRecognizesEveryFrontendAppKey() throws Exception {
        String frontend = Files.readString(Path.of("../frontend/src/apps/registry.ts"));
        var keys = Pattern.compile("\\bkey:\\s*'([^']+)'").matcher(frontend);
        int count=0;
        while(keys.find()) { count++; assertTrue(registry.isActive(keys.group(1)),"Frontend key absent/inactive in backend: " + keys.group(1)); }
        assertTrue(count>0); assertTrue(registry.isValid("exam"));
    }
}
