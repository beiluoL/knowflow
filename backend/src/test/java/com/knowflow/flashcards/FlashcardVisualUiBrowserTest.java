package com.knowflow.flashcards;

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
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real Chrome; IsolatedTestContextFactory supplies a new private H2 database. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"server.address=127.0.0.1","knowflow.datasource.h2.url=jdbc:h2:mem:flash_visual_ui;MODE=MySQL;DB_CLOSE_DELAY=-1"})
@Import({FakeAiConfiguration.class, FlashcardVisualUiBrowserTest.Observation.class})
@EnabledIfSystemProperty(named="flash.visual.browser", matches="true")
class FlashcardVisualUiBrowserTest {
    private static final String ISOLATED_JWT_SECRET=UUID.randomUUID().toString()+UUID.randomUUID();
    @org.springframework.test.context.DynamicPropertySource
    static void isolatedCredentials(org.springframework.test.context.DynamicPropertyRegistry properties) {
        // A token left in localhost storage cannot authenticate against the normal backend.
        properties.add("jwt.secret",()->ISOLATED_JWT_SECRET);
    }
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired ObjectMapper json; @Autowired JdbcTemplate db;
    @Autowired SysUserMapper users; @Autowired LearningFlashcardMapper cards;
    @Autowired PasswordEncoder passwords; @Autowired JwtUtils jwt;
    static final Path RUN=Path.of(System.getProperty("flash.visual.run-dir","/tmp/knowflow-flash-visual-browser"));
    static final Path OUT=Boolean.getBoolean("flash.visual.preview") ? RUN.resolve("preview-evidence") : Path.of(System.getProperty("user.dir")).getParent().resolve("docs/ui-v2/evidence/flashcard-ui-v4");
    static final List<Map<String,Object>> HTTP=new ArrayList<>();
    @TestConfiguration static class Observation {
        @Bean FilterRegistrationBean<OncePerRequestFilter> learningBrowserEvidence(ObjectMapper json) {
            var bean=new FilterRegistrationBean<OncePerRequestFilter>();bean.setOrder(Integer.MIN_VALUE);
            bean.setFilter(new OncePerRequestFilter() {
                void fault(ContentCachingResponseWrapper response) throws java.io.IOException {
                    response.resetBuffer();response.setStatus(500);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");response.getWriter().write("{\"code\":500,\"message\":\"隔离网络故障，请重试\",\"data\":null}");
                }
                @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws java.io.IOException,ServletException {
                    var wrapped=new ContentCachingResponseWrapper(response);String uri=request.getRequestURI();boolean review=uri.endsWith("/fsrs-review");
                    try {
                        boolean failRead="/api/learning/flashcards".equals(uri)&&Files.deleteIfExists(RUN.resolve("fail-list"));
                        if(failRead || review&&Files.deleteIfExists(RUN.resolve("fail-review"))) fault(wrapped);
                        else {
                            if(review&&Files.deleteIfExists(RUN.resolve("delay-review")))try{Thread.sleep(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ServletException(e);}
                            chain.doFilter(request,wrapped);
                            if(review&&wrapped.getStatus()==200&&Files.deleteIfExists(RUN.resolve("lose-review-response")))fault(wrapped);
                        }
                    } finally {
                        if(uri.startsWith("/api/")) {
                            int code=-1;try{code=json.readTree(wrapped.getContentAsByteArray()).path("code").asInt(-1);}catch(Exception ignored){}
                            synchronized(HTTP){HTTP.add(Map.of("path",uri,"method",request.getMethod(),"http",wrapped.getStatus(),"bodyCode",code));Files.createDirectories(OUT);Files.writeString(OUT.resolve("server-http.json"),json.writeValueAsString(HTTP));}
                        }
                        wrapped.copyBodyToResponse();
                    }
                }
            });return bean;
        }
    }
    SysUser user(String role) {var u=new SysUser();u.setUsername("learning-ui-"+UUID.randomUUID());u.setNickname("闪卡学习隔离验收");u.setPassword(passwords.encode("Learning-ui-test-only!2026"));u.setRole(role);users.insert(u);return u;}
    void contract(String method,String path,SysUser actor,int expected) throws Exception {
        var b=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+path));
        if(actor!=null)b.header("Authorization","Bearer "+jwt.generateToken(actor.getId(),actor.getUsername(),actor.getRole()));
        if("POST".equals(method))b.header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"rating\":3,\"revision\":0,\"requestId\":\"learning-ui-forbidden-review\"}"));else b.GET();
        var r=java.net.http.HttpClient.newHttpClient().send(b.build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(expected,r.statusCode());assertEquals(expected,json.readTree(r.body()).path("code").asInt());
    }
    @Test void realLearningUiAndOriginalFsrsContract() throws Exception {
        Files.createDirectories(RUN);Files.createDirectories(OUT);assertNotEquals(8080,port);
        var owner=user("USER");var other=user("USER");var admin=user("ADMIN");var visual=user("USER");var visualCard=new LearningFlashcard();visualCard.setUserId(visual.getId());visualCard.setFront("桶长达到 8 就一定树化吗？");visualCard.setBack("不是。OpenJDK8 在容量小于 64 时优先扩容；树化阈值只是实现条件的一部分。还要区分 API 契约与特定实现，Java 17 使用者不能依赖内部节点类型。");visualCard.setCategory("Java 核心与集合");visualCard.setDifficulty(2);visualCard.setSourceType("MANUAL");cards.insert(visualCard);
        List<Long> ids=new ArrayList<>();
        String longAnswer="## HashMap 学习隔离样例\n\n"+"- 完整解释容量、阈值与扩容，并区分接口约定和实现细节。\n".repeat(30)+"\n```java\nString value = \""+"LongTechnicalIdentifier".repeat(25)+"\";\n```\n\n| 场景 | 说明 |\n| --- | --- |\n| 扩容 | 保留完整表格 |\n\n结尾校验：答案完整可达。";
        for(int i=0;i<7;i++){var c=new LearningFlashcard();c.setUserId(owner.getId());c.setFront("HashMap 学习隔离卡 "+(i+1)+"：如何解释容量与扩容？"+(i==1?"\n\n请逐项回忆：\n"+"- 长问题用于验证下一张始终从题目开头阅读。\n".repeat(40):""));c.setBack(i<=1?longAnswer:"完整参考答案：结合容量、负载因子和真实场景解释，不将自评等同于客观正确率。");c.setSourceType("MANUAL");cards.insert(c);ids.add(c.getId());}
        String rootPath="/api/learning/flashcards/"+ids.get(0);
        contract("GET",rootPath+"/schedule",null,401);contract("POST",rootPath+"/fsrs-review",null,401);
        String foreignToken=io.jsonwebtoken.Jwts.builder().claim("userId",owner.getId()).claim("username",owner.getUsername()).claim("role","USER").signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor((UUID.randomUUID().toString()+UUID.randomUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
        var foreignRequest=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+rootPath+"/schedule")).header("Authorization","Bearer "+foreignToken).GET().build();
        var foreignResponse=java.net.http.HttpClient.newHttpClient().send(foreignRequest,java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(401,foreignResponse.statusCode());assertEquals(401,json.readTree(foreignResponse.body()).path("code").asInt());
        contract("GET",rootPath+"/schedule",owner,200);
        for(var actor:List.of(other,admin)){contract("GET",rootPath+"/schedule",actor,403);contract("POST",rootPath+"/fsrs-review",actor,403);}
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='FLASHCARD_REVIEWED'",Integer.class,owner.getId()));
        var foreign=db.queryForList("SELECT id,user_id,front,back,review_count,next_review_time,fsrs_revision FROM learning_flashcard WHERE user_id<>? ORDER BY id",owner.getId());
        var fixture=new LinkedHashMap<String,Object>();fixture.put("backendUrl","http://127.0.0.1:"+port);fixture.put("username",owner.getUsername());fixture.put("password","Learning-ui-test-only!2026");fixture.put("emptyUsername",other.getUsername());fixture.put("userId",owner.getId());fixture.put("cardIds",ids);fixture.put("visualUsername",visual.getUsername());fixture.put("visualCardId",visualCard.getId());
        Path secret=RUN.resolve("fixture.json");Files.writeString(secret,json.writeValueAsString(fixture));Files.setPosixFilePermissions(secret,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        var root=Path.of(System.getProperty("user.dir")).getParent();
        boolean preview=Boolean.getBoolean("flash.visual.preview");
        var fb=new ProcessBuilder("node",root.resolve(preview ? "scripts/tests/flashcard_visual_preview.cjs" : "scripts/tests/today_actions_ui_server.cjs").toString(),RUN.toString());fb.directory(root.toFile());fb.redirectErrorStream(true);fb.redirectOutput(RUN.resolve("vite.log").toFile());
        FakeAiConfiguration.reset();FakeAiConfiguration.configured=false;var front=fb.start();
        try {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(!Files.exists(RUN.resolve("ready.json"))&&System.nanoTime()<deadline){assertTrue(front.isAlive());Thread.sleep(100);}assertTrue(Files.exists(RUN.resolve("ready.json")));
            if(preview) {
                // A separate opt-in preview process, not a completed verification run.
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3600);
                while(front.isAlive()&&!Files.exists(RUN.resolve("stop"))&&System.nanoTime()<until)Thread.sleep(250);
                return;
            }
            var bb=new ProcessBuilder("node",root.resolve("scripts/tests/flashcard_visual_ui_browser.cjs").toString(),RUN.toString());bb.directory(root.toFile());bb.environment().put("NODE_PATH","/Users/beiluo/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules");bb.redirectErrorStream(true);bb.redirectOutput(RUN.resolve("browser.log").toFile());var browser=bb.start();
            if(!browser.waitFor(300,TimeUnit.SECONDS)){browser.descendants().forEach(ProcessHandle::destroy);browser.destroy();fail("Owned browser timed out");}assertEquals(0,browser.exitValue(),"See private browser.log");
            assertEquals(foreign,db.queryForList("SELECT id,user_id,front,back,review_count,next_review_time,fsrs_revision FROM learning_flashcard WHERE user_id<>? ORDER BY id",owner.getId()));
            assertEquals(7,db.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='FLASHCARD_REVIEWED'",Integer.class,owner.getId()));
            var results=new ArrayList<Map<String,Object>>();for(long id:ids){var card=cards.selectById(id);assertEquals(1,db.queryForObject("SELECT fsrs_revision FROM learning_flashcard WHERE id=?",Integer.class,id));assertNotNull(card.getNextReviewTime());var memory=json.readTree(cards.fsrsState(id));results.add(Map.of("cardId",id,"rating",memory.path("rating").asInt(),"revision",1,"dueAt",card.getNextReviewTime().toString()));}
            assertEquals(0,FakeAiConfiguration.completeCalls);assertEquals(0,db.queryForObject("SELECT revision FROM learning_flashcard_settings WHERE user_id=?",Integer.class,owner.getId()));
            Files.writeString(OUT.resolve("database.json"),json.writeValueAsString(Map.of("scope","Private H2 only","events",7,"ratings",results,"otherUsersCardsUnchanged",true,"settingsRevision",0,"realAiCalls",0,"anonymous401",true,"otherAndAdmin403",true)));
        } finally {Files.writeString(RUN.resolve("stop"),"1");if(!front.waitFor(10,TimeUnit.SECONDS)){front.destroy();if(!front.waitFor(5,TimeUnit.SECONDS))front.destroyForcibly();}Files.deleteIfExists(secret);FakeAiConfiguration.reset();}
    }
}
