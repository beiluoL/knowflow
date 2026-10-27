package com.knowflow.flashcards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in browser fixture; all settings/review writes stay in a fresh test-only H2 database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "knowflow.datasource.h2.url=jdbc:h2:mem:flashcard_settings_ui;MODE=MySQL;DB_CLOSE_DELAY=-1"})
@Import({FakeAiConfiguration.class, FlashcardSettingsUiBrowserTest.Observation.class})
@EnabledIfSystemProperty(named = "flash.settings.browser", matches = "true")
class FlashcardSettingsUiBrowserTest {
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired SysUserMapper users;
    @Autowired LearningFlashcardMapper cards;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtUtils jwt;
    static final Path RUN = Path.of(System.getProperty("flash.settings.run-dir", "/tmp/knowflow-flash-settings-browser"));
    static final Path OUT = Path.of(System.getProperty("user.dir")).getParent().resolve("docs/reports/evidence/flashcard-settings-v2");
    static final List<Map<String,Object>> HTTP = new ArrayList<>();
    static final String SETTINGS = "/api/learning/my/flashcards/settings";

    @TestConfiguration static class Observation {
        @Bean FilterRegistrationBean<OncePerRequestFilter> settingsBrowserEvidence(ObjectMapper json) {
            var bean = new FilterRegistrationBean<OncePerRequestFilter>(); bean.setOrder(Integer.MIN_VALUE);
            bean.setFilter(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws java.io.IOException, ServletException {
                    var wrapped = new ContentCachingResponseWrapper(response);
                    String uri = request.getRequestURI(); boolean isSettings = SETTINGS.equals(uri), get = "GET".equals(request.getMethod());
                    try {
                        if (isSettings && Files.deleteIfExists(RUN.resolve(get ? "fail-read" : "fail-save"))) {
                            wrapped.setStatus(500); wrapped.setContentType("application/json"); wrapped.setCharacterEncoding("UTF-8"); wrapped.getWriter().write("{\"code\":500,\"message\":\"隔离设置故障，请重试\",\"data\":null}");
                        } else {
                            if (isSettings && !get && Files.deleteIfExists(RUN.resolve("delay-save"))) {
                                try { Thread.sleep(350); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ServletException(e); }
                            }
                            chain.doFilter(request, wrapped);
                            if (isSettings && !get && wrapped.getStatus() == 200 && Files.deleteIfExists(RUN.resolve("fail-readback"))) Files.writeString(RUN.resolve("fail-read"), "1");
                        }
                    } finally {
                        if (uri.startsWith("/api/")) {
                            int code = -1;
                            try { code = json.readTree(wrapped.getContentAsByteArray()).path("code").asInt(-1); } catch (Exception ignored) { /* Non-JSON remains unknown in the evidence. */ }
                            synchronized (HTTP) { HTTP.add(Map.of("path", uri, "method", request.getMethod(), "http", wrapped.getStatus(), "bodyCode", code)); Files.createDirectories(OUT); Files.writeString(OUT.resolve("server-http.json"), json.writeValueAsString(HTTP)); }
                        }
                        wrapped.copyBodyToResponse();
                    }
                }
            }); return bean;
        }
    }

    private SysUser user(String role) {
        var user = new SysUser(); user.setUsername("settings-ui-" + UUID.randomUUID()); user.setPassword(passwords.encode("Settings-ui-test-only!2026")); user.setNickname("闪卡设置隔离验收"); user.setRole(role); users.insert(user); return user;
    }
    private String auth(SysUser user) { return "Bearer " + jwt.generateToken(user.getId(), user.getUsername(), user.getRole()); }
    private JsonNode call(String method, String path, SysUser user, JsonNode body, int expected) throws Exception {
        var builder = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + path));
        if (user != null) builder.header("Authorization", auth(user));
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? java.net.http.HttpRequest.BodyPublishers.noBody() : java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response = java.net.http.HttpClient.newHttpClient().send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(expected, response.statusCode(), method + " settings HTTP status");
        var result = json.readTree(response.body()); assertEquals(expected, result.path("code").asInt(), "settings body.code"); return result.path("data");
    }
    private JsonNode read(SysUser user) throws Exception { return call("GET", SETTINGS, user, null, 200); }
    private void settingsContracts(SysUser owner, SysUser other, SysUser admin) throws Exception {
        call("GET", SETTINGS, null, null, 401); call("PUT", SETTINGS, null, json.createObjectNode(), 401);
        JsonNode original = read(owner);
        var custom = json.valueToTree(FlashcardFsrsService.defaults()).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) custom).put("timezone", "UTC+05:30");
        ((com.fasterxml.jackson.databind.node.ObjectNode) custom.path("scheduling")).set("learningMinutes", json.readTree("[]"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) custom.path("scheduling")).set("relearningMinutes", json.readTree("[]"));
        assertEquals(custom, call("PUT", SETTINGS + "?userId=" + owner.getId(), other, custom, 200));
        assertEquals(custom, read(other)); assertEquals(original, read(owner));
        assertEquals("Asia/Shanghai", call("GET", SETTINGS + "?userId=" + other.getId(), admin, null, 200).path("timezone").asText());
        call("PUT", SETTINGS + "?userId=" + other.getId(), admin, json.valueToTree(FlashcardFsrsService.defaults()), 200);
        assertEquals(custom, read(other));
        for (String steps : List.of("[0]", "[1,1]", "[10,1]", "[1441]", "[1,2,3,4,5,6,7]", "null")) {
            var bad = json.valueToTree(FlashcardFsrsService.defaults()).deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) bad.path("scheduling")).set("learningMinutes", json.readTree(steps));
            call("PUT", SETTINGS, other, bad, 400);
        }
        var badZone = json.valueToTree(FlashcardFsrsService.defaults()).deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) badZone).put("timezone", "Not/AZone");
        call("PUT", SETTINGS, other, badZone, 400); assertEquals(custom, read(other)); assertEquals(original, read(owner));
        Files.writeString(OUT.resolve("settings-contract.json"), json.writeValueAsString(Map.of("anonymous401", true, "ownerReadDefaults", true, "otherUserCannotSelectOwnerViaQuery", true, "adminSettingsRemainOwn", true, "emptyStepArraysAndOffsetZoneRoundTrip", true, "invalidStepsAndZone400", true)));
    }

    @Test void settingsUiAndFourRatingsViaRealChrome() throws Exception {
        Files.createDirectories(RUN); Files.createDirectories(OUT); assertNotEquals(8080, port);
        var owner = user("USER"); var other = user("USER"); var admin = user("ADMIN");
        settingsContracts(owner, other, admin);
        var others = db.queryForList("SELECT user_id,settings_json,revision FROM learning_flashcard_settings WHERE user_id<>? ORDER BY user_id", owner.getId());
        List<Long> cardIds = new ArrayList<>();
        for (String question : List.of("Again", "Hard", "Good", "Easy")) {
            var card = new LearningFlashcard(); card.setUserId(owner.getId()); card.setFront("设置UI隔离回归 · " + question); card.setBack("这是独立测试卡，用于验证翻面和原四档评分，不代表真实学习成绩。"); card.setSourceType("MANUAL"); cards.insert(card); cardIds.add(card.getId());
        }
        var fixture = Map.of("backendUrl", "http://127.0.0.1:" + port, "username", owner.getUsername(), "password", "Settings-ui-test-only!2026", "userId", owner.getId(), "cardIds", cardIds);
        Path fixtureFile = RUN.resolve("fixture.json"); Files.writeString(fixtureFile, json.writeValueAsString(fixture)); Files.setPosixFilePermissions(fixtureFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        var root = Path.of(System.getProperty("user.dir")).getParent();
        var frontBuilder = new ProcessBuilder("node", root.resolve("scripts/tests/today_actions_ui_server.cjs").toString(), RUN.toString()); frontBuilder.directory(root.toFile()); frontBuilder.redirectErrorStream(true); frontBuilder.redirectOutput(RUN.resolve("vite.log").toFile());
        FakeAiConfiguration.reset(); FakeAiConfiguration.configured = false; var frontend = frontBuilder.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!Files.exists(RUN.resolve("ready.json")) && System.nanoTime() < deadline) { assertTrue(frontend.isAlive()); Thread.sleep(100); }
            assertTrue(Files.exists(RUN.resolve("ready.json")), "Owned Vite did not become ready");
            var browserBuilder = new ProcessBuilder("node", root.resolve("scripts/tests/flashcard_settings_ui_browser.cjs").toString(), RUN.toString()); browserBuilder.directory(root.toFile()); browserBuilder.environment().put("NODE_PATH", "/Users/beiluo/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules"); browserBuilder.redirectErrorStream(true); browserBuilder.redirectOutput(RUN.resolve("browser.log").toFile()); var browser = browserBuilder.start();
            if (!browser.waitFor(300, TimeUnit.SECONDS)) { browser.descendants().forEach(ProcessHandle::destroy); browser.destroy(); fail("Owned browser timed out"); }
            assertEquals(0, browser.exitValue(), "See private browser.log and this task's failure screenshot");
            assertEquals(others, db.queryForList("SELECT user_id,settings_json,revision FROM learning_flashcard_settings WHERE user_id<>? ORDER BY user_id", owner.getId()));
            var setting = read(owner); assertEquals(.935, setting.path("scheduling").path("desiredRetention").asDouble()); assertEquals("Asia/Tokyo", setting.path("timezone").asText()); assertEquals(6, setting.path("dayStartHour").asInt());
            assertEquals(2, db.queryForObject("SELECT revision FROM learning_flashcard_settings WHERE user_id=?", Integer.class, owner.getId()));
            assertEquals(4, db.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='FLASHCARD_REVIEWED'", Integer.class, owner.getId()));
            List<Map<String,Object>> results = new ArrayList<>();
            for (int i = 0; i < cardIds.size(); i++) {
                var card = cards.selectById(cardIds.get(i)); assertEquals(i == 0 ? 0 : 1, card.getReviewCount(), "Legacy review_count resets after Again; events and FSRS revision remain authoritative"); assertNotNull(card.getNextReviewTime());
                assertEquals(1, db.queryForObject("SELECT fsrs_revision FROM learning_flashcard WHERE id=?", Integer.class, card.getId()));
                var memory = json.readTree(cards.fsrsState(card.getId())); assertEquals(i + 1, memory.path("rating").asInt());
                results.add(Map.of("cardId", card.getId(), "rating", i + 1, "reviewCount", card.getReviewCount(), "nextReviewTime", card.getNextReviewTime().toString()));
            }
            assertEquals(0, FakeAiConfiguration.completeCalls);
            Files.writeString(OUT.resolve("database.json"), json.writeValueAsString(Map.of("scope", "private H2 fixture only; real browser settings and four ratings", "userId", owner.getId(), "settings", setting, "settingsRevision", 2, "ratings", results, "otherUsersSettingsUnchanged", true, "realAiCalls", 0)));
        } finally {
            Files.writeString(RUN.resolve("stop"), "stop"); if (!frontend.waitFor(10, TimeUnit.SECONDS)) { frontend.destroy(); if (!frontend.waitFor(5, TimeUnit.SECONDS)) frontend.destroyForcibly(); }
            Files.deleteIfExists(fixtureFile); FakeAiConfiguration.reset();
        }
    }
}
