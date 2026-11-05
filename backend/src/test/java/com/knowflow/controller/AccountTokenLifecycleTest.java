package com.knowflow.controller;

import com.knowflow.identity.entity.SysUser;
import com.knowflow.identity.mapper.SysUserMapper;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AccountTokenLifecycleTest {
    @Autowired private MockMvc mvc;
    @Autowired private SysUserMapper users;
    @Autowired private JwtUtils jwt;

    @Test
    void issuedAdminTokenLosesAdminAccessAfterDemotion() throws Exception {
        SysUser maintainer = createUser("ADMIN");
        SysUser subject = createUser("ADMIN");
        String previousToken = bearer(subject);
        mvc.perform(put("/api/admin/users/" + subject.getId())
                        .header("Authorization", bearer(maintainer))
                        .contentType("application/json").content("{\"role\":\"USER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));

        mvc.perform(get("/api/admin/users/" + subject.getId()).header("Authorization", previousToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        mvc.perform(get("/api/admin/users/" + subject.getId())
                        .header("Authorization", bearer(users.selectById(subject.getId()))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void issuedUserTokenCannotReachProtectedSearchAfterDeletion() throws Exception {
        SysUser maintainer = createUser("ADMIN");
        SysUser subject = createUser("USER");
        String previousToken = bearer(subject);
        mvc.perform(delete("/api/admin/users/" + subject.getId())
                        .header("Authorization", bearer(maintainer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/admin/users/" + subject.getId())
                        .header("Authorization", bearer(maintainer)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));

        mvc.perform(get("/api/users/search").param("keyword", maintainer.getUsername())
                        .header("Authorization", previousToken))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void logoutRejectsTokenOfAnOtherwiseActiveAccount() throws Exception {
        SysUser subject = createUser("USER");
        String token = bearer(subject);
        mvc.perform(get("/api/users/search").param("keyword", subject.getUsername())
                        .header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(post("/api/auth/logout").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/users/search").param("keyword", subject.getUsername())
                        .header("Authorization", token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    private SysUser createUser(String role) {
        SysUser user = new SysUser();
        user.setUsername("token-" + UUID.randomUUID());
        user.setPassword("unused");
        user.setRole(role);
        users.insert(user);
        return user;
    }

    private String bearer(SysUser user) {
        return "Bearer " + jwt.generateToken(user.getId(), user.getUsername(), user.getRole());
    }
}
