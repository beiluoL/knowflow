package com.knowflow.learning.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.LearningFlashcard;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.LearningFlashcardMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.support.FakeAiConfiguration;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in CUA browser harness. All writes stay in this class's private H2 database. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.address=127.0.0.1","knowflow.datasource.h2.url=jdbc:h2:mem:today_actions_ui;MODE=MySQL;DB_CLOSE_DELAY=-1"})
@Import({FakeAiConfiguration.class,TodayActionsUiBrowserTest.Observation.class})
@EnabledIfSystemProperty(named="today.ui.browser",matches="true")
class TodayActionsUiBrowserTest {
 @org.springframework.boot.test.web.server.LocalServerPort int port;
 @Autowired ObjectMapper json; @Autowired JdbcTemplate db; @Autowired SysUserMapper users;
 @Autowired PasswordEncoder passwords; @Autowired LearningFlashcardMapper cards;
 static final Path RUN=Path.of(System.getProperty("today.ui.run-dir","/tmp/knowflow-today-ui-browser"));
 static final List<Map<String,Object>> HTTP=new ArrayList<>();
 static final Path OUT=Path.of(System.getProperty("user.dir")).getParent().resolve("docs/reports/evidence/today-actions-ui");
 @TestConfiguration static class Observation {
  @Bean FilterRegistrationBean<OncePerRequestFilter> browserHttpEvidence(ObjectMapper json) {
   var bean=new FilterRegistrationBean<OncePerRequestFilter>();bean.setOrder(Integer.MIN_VALUE);
   bean.setFilter(new OncePerRequestFilter(){
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws java.io.IOException,ServletException {
     var wrapped=new ContentCachingResponseWrapper(response);
     String uri=request.getRequestURI();String flag=uri.endsWith("/dashboard")?"fail-dashboard":uri.endsWith("/daily/preview")?"fail-preview":null;
     try {
      if(flag!=null&&Files.deleteIfExists(RUN.resolve(flag))){wrapped.setStatus(500);wrapped.setContentType("application/json");wrapped.getWriter().write("{\"code\":500,\"message\":\"隔离故障注入，请重试\",\"data\":null}");}
      else chain.doFilter(request,wrapped);
     } finally {
      if(uri.startsWith("/api/")){
       int code=-1;try{code=json.readTree(wrapped.getContentAsByteArray()).path("code").asInt(-1);}catch(Exception ignored){/* Non-JSON response remains explicitly unknown. */}
       synchronized(HTTP){HTTP.add(Map.of("path",uri,"method",request.getMethod(),"http",wrapped.getStatus(),"bodyCode",code));Files.createDirectories(OUT);Files.writeString(OUT.resolve("server-http.json"),json.writeValueAsString(HTTP));}
      }
      wrapped.copyBodyToResponse();
     }
    }
   });return bean;
  }
 }
 @Test void realPreviewConfirmationAndResourceEntryViaCua()throws Exception {
  Files.createDirectories(RUN);Files.createDirectories(OUT);
  var user=new SysUser();user.setUsername("today-ui-"+UUID.randomUUID());user.setPassword(passwords.encode("Today-ui-test-only!2026"));user.setNickname("今日行动隔离验收");user.setRole("USER");users.insert(user);
  for(String front:List.of("HashMap：桶长达到8就一定树化吗？","JDK8扩容为何不必重新计算所有hashCode？","长标题兼容检查：HashMap与ConcurrentHashMap的边界、迭代以及null值语义 / "+"LongTechnicalIdentifier".repeat(8))){var card=new LearningFlashcard();card.setUserId(user.getId());card.setFront(front);card.setBack("隔离测试卡片；不会替代正式课程或学习成绩。");card.setSourceType("MANUAL");card.setNextReviewTime(LocalDateTime.now().minusDays(1));cards.insert(card);}
  var others=db.queryForList("SELECT id,user_id,status FROM task WHERE user_id<>? ORDER BY id",user.getId());
  var fixture=Map.of("backendUrl","http://127.0.0.1:"+port,"username",user.getUsername(),"password","Today-ui-test-only!2026","userId",user.getId());
  Path fixtureFile=RUN.resolve("fixture.json");Files.writeString(fixtureFile,json.writeValueAsString(fixture));Files.setPosixFilePermissions(fixtureFile,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
  var root=Path.of(System.getProperty("user.dir")).getParent();var builder=new ProcessBuilder("node",root.resolve("scripts/tests/today_actions_ui_server.cjs").toString(),RUN.toString());builder.directory(root.toFile());builder.redirectErrorStream(true);builder.redirectOutput(RUN.resolve("vite.log").toFile());
  FakeAiConfiguration.reset();FakeAiConfiguration.configured=false;var frontend=builder.start();
  try {
   long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(15);
   while(!Files.exists(RUN.resolve("done"))&&System.nanoTime()<deadline){assertTrue(frontend.isAlive(),"Owned frontend stopped; private log only");Thread.sleep(250);}
   assertTrue(Files.exists(RUN.resolve("done")),"CUA verification did not finish before timeout");
   int count=db.queryForObject("SELECT COUNT(*) FROM task WHERE user_id=? AND learning_plan_ref IS NOT NULL",Integer.class,user.getId());assertEquals(1,count,"Only one explicitly confirmed plan item at the 15-minute budget");
   assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM task WHERE user_id=? AND status=1",Integer.class,user.getId()),"Opening resources does not complete learning tasks");
   assertEquals(others,db.queryForList("SELECT id,user_id,status FROM task WHERE user_id<>? ORDER BY id",user.getId()));assertEquals(0,FakeAiConfiguration.completeCalls);
   Files.writeString(OUT.resolve("database.json"),json.writeValueAsString(Map.of("scope","private H2 test fixtures, CUA operated real UI","userId",user.getId(),"createdTasks",db.queryForList("SELECT id,title,status,learning_plan_ref FROM task WHERE user_id=? ORDER BY id",user.getId()),"cards",db.queryForList("SELECT id,user_id,front FROM learning_flashcard WHERE user_id=? ORDER BY id",user.getId()),"otherTasksUnchanged",true,"realAiCalls",0)));
  } finally {Files.writeString(RUN.resolve("stop"),"stop");if(!frontend.waitFor(10,TimeUnit.SECONDS)){frontend.destroy();if(!frontend.waitFor(5,TimeUnit.SECONDS))frontend.destroyForcibly();}Files.deleteIfExists(fixtureFile);FakeAiConfiguration.reset();}
 }
}
