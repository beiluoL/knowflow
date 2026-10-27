package com.knowflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.controller.admin.AdminUserController;
import com.knowflow.exception.GlobalExceptionHandler;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties =
        "knowflow.datasource.h2.url=jdbc:h2:mem:knowflow_p1_admin_user;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL")
class AdminUserErrorSemanticsTest {

    private static final long ADMIN_ID = 1L;

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private ObjectMapper json;
    @Autowired private AdminUserController controller;
    @Autowired private JdbcTemplate jdbc;

    private String auth() {
        return "Bearer " + jwtUtils.generateToken(ADMIN_ID, "admin", "ADMIN");
    }

    private String name() {
        return "p1au" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private void assertError(MockHttpServletRequestBuilder request, int code, String message) throws Exception {
        mockMvc.perform(request.header("Authorization", auth()))
                .andExpect(status().is(code))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message));
    }

    private long addUser(String username, String role) throws Exception {
        mockMvc.perform(post("/api/admin/users").header("Authorization", auth())
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", username, "password", "Passw0rd", "role", role))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        String listBody = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/users").header("Authorization", auth()).param("keyword", username))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(listBody).path("data").path("records").get(0).path("id").asLong();
    }

    private int activeUserCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE deleted = 0", Integer.class);
    }

    private int activeUserCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id = ? AND deleted = 0",
                Integer.class, id);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("新增用户参数错误与用户名冲突返回 400/409，而非 HTTP 200 + code 500")
    void addErrorsUseExplicitHttpStatus() throws Exception {
        int before = activeUserCount();
        assertError(post("/api/admin/users").contentType(APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"Passw0rd\"}"), 400, "用户名不能为空");
        assertError(post("/api/admin/users").contentType(APPLICATION_JSON)
                .content("{\"username\":\"valid-name\",\"password\":\"\"}"), 400, "密码不能为空");
        assertEquals(before, activeUserCount(), "参数错误不得新增用户");

        String username = name();
        addUser(username, "USER");
        int afterFirstInsert = activeUserCount();
        assertError(post("/api/admin/users").contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", "Passw0rd"))),
                409, "用户名已存在");
        assertEquals(afterFirstInsert, activeUserCount(), "用户名冲突不得新增重复用户");
    }

    @Test
    @DisplayName("无效重置密码返回 400")
    void invalidResetPasswordReturns400() throws Exception {
        long userId = addUser(name(), "USER");
        String passwordBefore = jdbc.queryForObject("SELECT password FROM sys_user WHERE id = ?", String.class, userId);
        assertError(put("/api/admin/users/{id}/password", userId).contentType(APPLICATION_JSON)
                .content("{\"password\":\"123\"}"), 400, "密码不能为空且不少于6位");
        String passwordAfter = jdbc.queryForObject("SELECT password FROM sys_user WHERE id = ?", String.class, userId);
        assertEquals(passwordBefore, passwordAfter, "无效密码不得覆盖原密码哈希");
    }

    @Test
    @DisplayName("禁止删除当前用户或管理员账户返回 403")
    void forbiddenDeletesReturn403() throws Exception {
        assertError(delete("/api/admin/users/{id}", ADMIN_ID), 403, "不能删除当前登录用户");
        assertEquals(1, activeUserCount(ADMIN_ID), "禁止自删后当前管理员必须仍存在");
        long otherAdmin = addUser(name(), "ADMIN");
        assertError(delete("/api/admin/users/{id}", otherAdmin), 403, "不能删除管理员账户");
        assertEquals(1, activeUserCount(otherAdmin), "禁止删除后目标管理员必须仍存在");
    }

    @Test
    @DisplayName("异常 principal 不强转为 Long，而是返回 401 业务错误")
    void invalidPrincipalReturns401() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("unexpected-principal", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        MockMvc standalone = MockMvcBuilders.standaloneSetup(new InvalidPrincipalEndpoint(controller))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        try {
            standalone.perform(delete("/invalid-principal"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.message").value("未登录"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @RestController
    static class InvalidPrincipalEndpoint {
        private final AdminUserController controller;

        InvalidPrincipalEndpoint(AdminUserController controller) {
            this.controller = controller;
        }

        @DeleteMapping("/invalid-principal")
        public Object deleteWithInvalidPrincipal() {
            return controller.delete(999999L);
        }
    }
}
