package com.knowflow.controller;

import com.knowflow.identity.entity.SysUser;
import com.knowflow.identity.mapper.SysUserMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties="knowflow.datasource.h2.url=jdbc:h2:mem:search_privacy_batch1;DB_CLOSE_DELAY=-1;MODE=MySQL")
class UserSearchPrivacyHttpTest {
    @Autowired MockMvc http;
    @Autowired SysUserMapper users;
    @Autowired JwtUtils tokens;

    @Test
    void ordinaryAndAdminSearchExposeOnlyPublicProfile() throws Exception {
        String key = "privacy-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        SysUser target = new SysUser();
        target.setUsername(key);
        target.setNickname("Public profile");
        target.setEmail(key + "@private.test");
        target.setPassword("private-password-hash");
        target.setRole("USER");
        users.insert(target);
        SysUser caller = new SysUser();
        caller.setUsername("caller-" + java.util.UUID.randomUUID());
        caller.setEmail(caller.getUsername() + "@test.invalid");
        caller.setPassword("");
        caller.setRole("USER");
        users.insert(caller);
        for (String role : java.util.List.of("USER", "ADMIN")) {
            http.perform(get("/api/users/search").param("keyword", key)
                    .header("Authorization", "Bearer " + tokens.generateToken(caller.getId(), caller.getUsername(), role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].username").value(key))
                    .andExpect(jsonPath("$.data[0].email").doesNotExist())
                    .andExpect(jsonPath("$.data[0].password").doesNotExist())
                    .andExpect(jsonPath("$.data[0].apiKey").doesNotExist());
        }
    }

    @Test
    void anonymousSearchIsRejected() throws Exception {
        http.perform(get("/api/users/search").param("keyword", "user"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }
}
