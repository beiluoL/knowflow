package com.knowflow.security;

import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "knowflow.datasource.h2.url=jdbc:h2:mem:local_tools_access;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "knowflow.developer-tools.enabled=true",
        "knowflow.datasource.config-file=./target/local-tools-test-no-overrides.json",
        "knowflow.seed.development-account.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
class LocalDeveloperToolsAccessTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtUtils jwt;

    @BeforeEach void accounts() {
        jdbc.update("DELETE FROM sys_user WHERE id BETWEEN 98001 AND 98003 OR username='seed-personal-v1'");
        for (int i = 1; i <= 3; i++) {
            jdbc.update("INSERT INTO sys_user (id,username,password,role,deleted) VALUES (?,?,?,?,0)",
                    98000L + i, i == 1 ? "tools-admin" : i == 2 ? "seed-personal-v1" : "tools-other",
                    passwords.encode("ToolsTest!2026"), i == 1 ? "ADMIN" : "USER");
        }
    }

    private String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test void anonymousBrowserGetsChallengeAndBody401() throws Exception {
        mvc.perform(get("/api-docs")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"KnowFlow local tools\""))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test void bothAccountsCanLoadDocsAndSwaggerResourcesWithoutToken() throws Exception {
        for (String user : new String[]{"tools-admin", "seed-personal-v1"}) {
            for (String path : new String[]{"/api-docs", "/api-docs.yaml", "/api-docs/swagger-config",
                    "/swagger-ui/index.html", "/swagger-ui/swagger-ui.css"}) {
                mvc.perform(get(path).header("Authorization", basic(user, "ToolsTest!2026")))
                        .andExpect(status().isOk());
            }
            // MockMvc does not dispatch to the H2 servlet, so 404 proves only that the security gate passed.
            mvc.perform(get("/h2-console/").header("Authorization", basic(user, "ToolsTest!2026")))
                    .andExpect(status().isNotFound());
        }
    }

    @Test void otherUserGets403() throws Exception {
        mvc.perform(get("/api-docs").header("Authorization", basic("tools-other", "ToolsTest!2026")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }

    @Test void wrongPasswordGets401() throws Exception {
        mvc.perform(get("/api-docs").header("Authorization", basic("seed-personal-v1", "wrong")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }

    @Test void deletedAccountGets401() throws Exception {
        jdbc.update("UPDATE sys_user SET deleted=1 WHERE id=98002");
        mvc.perform(get("/api-docs").header("Authorization", basic("seed-personal-v1", "ToolsTest!2026")))
                .andExpect(status().isUnauthorized());
    }

    @Test void externalRequestAndForwardedLoopbackGet403() throws Exception {
        mvc.perform(get("/api-docs").header("Authorization", basic("seed-personal-v1", "ToolsTest!2026"))
                        .header("X-Forwarded-For", "127.0.0.1").with(request -> {
                            request.setRemoteAddr("192.0.2.10"); return request;
                        }))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }

    @Test void basicDoesNotAuthenticateBusinessOrAdminApi() throws Exception {
        for (String user : new String[]{"tools-admin", "seed-personal-v1"}) {
            for (String path : new String[]{"/api/auth/me", "/api/admin/users"}) {
                mvc.perform(get(path).header("Authorization", basic(user, "ToolsTest!2026")))
                        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
            }
        }
    }

    @Test void existingJwtStillWorksAndSeedGetsNoAdminRole() throws Exception {
        String token = jwt.generateToken(98002L, "seed-personal-v1", "USER");
        mvc.perform(get("/api-docs").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
    }

    @Test void deletedJwtSubjectAndForgedRoleAreRejected() throws Exception {
        jdbc.update("UPDATE sys_user SET deleted=1 WHERE id=98002");
        for (String token : new String[]{jwt.generateToken(98002L, "seed-personal-v1", "USER"),
                jwt.generateToken(98003L, "tools-other", "ADMIN")}) {
            mvc.perform(get("/api-docs").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }
}
