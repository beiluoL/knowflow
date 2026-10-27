package com.knowflow.security;

import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "knowflow.datasource.h2.url=jdbc:h2:mem:local_tools_servlet;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "knowflow.datasource.config-file=./target/local-tools-servlet-no-overrides.json",
        "knowflow.developer-tools.enabled=true",
        "knowflow.seed.development-account.enabled=false",
        "spring.h2.console.enabled=true"
})
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
class LocalDeveloperToolsServletTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test void realH2ServletAndResourcesUseBasicAcrossRedirects() throws Exception {
        jdbc.update("INSERT INTO sys_user (id,username,password,role,deleted) VALUES (98002,'seed-personal-v1',?,'USER',0)",
                passwords.encode("ToolsTest!2026"));
        var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        var uri = URI.create("http://localhost:" + port + "/h2-console/");
        var anonymous = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, anonymous.statusCode());
        assertEquals("Basic realm=\"KnowFlow local tools\"", anonymous.headers().firstValue("WWW-Authenticate").orElse(""));
        for (String[] account : new String[][]{{"admin", "admin123"}, {"seed-personal-v1", "ToolsTest!2026"}}) {
            String authorization = "Basic " + Base64.getEncoder().encodeToString(
                    (account[0] + ":" + account[1]).getBytes(StandardCharsets.UTF_8));
            var page = client.send(HttpRequest.newBuilder(uri).header("Authorization", authorization).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("H2 Console"));
            for (String path : new String[]{"/api-docs", "/api-docs/swagger-config", "/swagger-ui/index.html", "/swagger-ui/swagger-ui.css"}) {
                var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Authorization", authorization).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), path);
            }
        }
    }
}
