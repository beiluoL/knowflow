package com.knowflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.LearningPath;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.LearningPathMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties = {
        "knowflow.datasource.type=h2",
        "knowflow.datasource.config-file=/tmp/knowflow-private-path-test-nonexistent-config.json",
        "knowflow.datasource.h2.url=jdbc:h2:mem:imported_private_path_discovery;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
class ImportedPrivatePathDiscoveryHttpTest {
    private static final String PACK = "content-studio-java-ai-persistent-v1";
    @Autowired MockMvc http;
    @Autowired JwtUtils jwt;
    @Autowired JdbcTemplate sql;
    @Autowired LearningPathMapper paths;
    @Autowired SysUserMapper users;
    Long owner, other, admin, imported, publicPath, personalized, otherPrivate;

    @BeforeEach void fixture() {
        owner = user("USER"); other = user("USER"); admin = user("ADMIN");
        publicPath = path(0L, 1); imported = path(owner, 1);
        personalized = path(owner, 1); otherPrivate = path(other, 1);
        index(imported, PACK, "PATH", "user/" + owner + "/path/material");
        index(otherPrivate, PACK, "PATH", "user/" + other + "/path/material");
    }
    Long user(String role) {
        SysUser u = new SysUser(); u.setUsername("private-path-" + UUID.randomUUID());
        u.setPassword("unused"); u.setRole(role); users.insert(u); return u.getId();
    }
    Long path(Long userId, int status) {
        LearningPath p = new LearningPath(); p.setTitle("private-path-" + UUID.randomUUID());
        p.setOwnerUserId(userId); p.setStatus(status); paths.insert(p); return p.getId();
    }
    void index(Long id, String pack, String kind, String key) {
        sql.update("INSERT INTO interview_seed_resource(seed_key,resource_key,resource_type,resource_id) VALUES(?,?,?,?)",
                pack, key, kind, id);
    }
    String auth(Long id) { return "Bearer " + jwt.generateToken(id, "fixture", "USER"); }
    Set<Long> list(Long actor) throws Exception {
        var request = get("/api/learning/paths");
        if (actor != null) request.header("Authorization", auth(actor));
        JsonNode data = new ObjectMapper().readTree(http.perform(request)
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString()).path("data");
        Set<Long> result = new HashSet<>(); for (JsonNode row : data) result.add(row.path("id").asLong());
        return result;
    }
    @Test void ownerDiscoversImportedCourseAndCanReadItsDetail() throws Exception {
        assertTrue(list(owner).contains(imported), "Own fixed imported course must appear in path list");
        assertTrue(list(owner).contains(publicPath));
        http.perform(get("/api/learning/paths/" + imported).header("Authorization", auth(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(imported));
    }
    @Test void otherUserAndAnonymousCannotDiscoverOrReadPrivateCourse() throws Exception {
        assertFalse(list(other).contains(imported)); assertFalse(list(null).contains(imported));
        assertTrue(list(null).contains(publicPath)); assertTrue(list(other).contains(publicPath));
        http.perform(get("/api/learning/paths/" + imported).header("Authorization", auth(other)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        http.perform(get("/api/learning/paths/" + imported))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }
    @Test void unrelatedPersonalizedPrivateCourseStaysOutOfList() throws Exception {
        assertFalse(list(owner).contains(personalized)); assertFalse(list(owner).contains(otherPrivate));
        http.perform(get("/api/learning/paths/" + personalized).header("Authorization", auth(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }
    @Test void registryMustMatchPackTypePrefixOwnerAndPublishedStatus() throws Exception {
        Long wrongPack = path(owner, 1), wrongKind = path(owner, 1), wrongPrefix = path(owner, 1);
        Long wrongOwner = path(other, 1), inactive = path(owner, 0);
        index(wrongPack, "other-pack", "PATH", "user/" + owner + "/path/wrong-pack");
        index(wrongKind, PACK, "DOC", "user/" + owner + "/path/wrong-kind");
        index(wrongPrefix, PACK, "PATH", "user/" + owner + "/personalized/unrelated");
        index(wrongOwner, PACK, "PATH", "user/" + owner + "/path/wrong-owner");
        index(inactive, PACK, "PATH", "user/" + owner + "/path/inactive");
        Set<Long> ids = list(owner);
        for (Long id : new Long[]{wrongPack, wrongKind, wrongPrefix, wrongOwner, inactive}) assertFalse(ids.contains(id));
        assertTrue(ids.contains(imported));
    }
    @Test void adminDoesNotGainGlobalPrivateCourseDiscoveryButKeepsOwnImportedCourse() throws Exception {
        assertFalse(list(admin).contains(imported));
        sql.update("UPDATE sys_user SET role='ADMIN' WHERE id=?", owner);
        assertTrue(list(owner).contains(imported)); assertFalse(list(owner).contains(otherPrivate));
    }
}
