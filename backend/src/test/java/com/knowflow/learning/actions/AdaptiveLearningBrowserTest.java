package com.knowflow.learning.actions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.dto.FlashcardSaveDTO;
import com.knowflow.service.LearningService;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties={"server.address=127.0.0.1","server.port=18099"})
@ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.config-file=/tmp/knowflow-phase4h-browser-no-config.json","knowflow.datasource.h2.url=jdbc:h2:mem:phase4h_browser;DB_CLOSE_DELAY=-1;MODE=MySQL"})
@EnabledIfSystemProperty(named="phase4h.browser",matches="true")
class AdaptiveLearningBrowserTest {
 @Autowired SysUserMapper users;@Autowired DocCategoryMapper categories;@Autowired DocDocumentMapper docs;@Autowired KgEntityMapper knowledge;
 @Autowired LearningPathMapper paths;@Autowired LearningChapterMapper chapters;@Autowired LearningUserPathMapper enrollments;@Autowired LearningGoalMapper goals;
 @Autowired QuizQuestionMapper questions;@Autowired ResourceKnowledgeMappingMapper mappings;@Autowired ChatConversationMapper chats;
 @Autowired LearningService learning;@Autowired JwtUtils jwt;@Autowired ObjectMapper json;@Autowired JdbcTemplate sql;
 SysUser user(String role){var u=new SysUser();u.setUsername("4h-browser-"+UUID.randomUUID());u.setNickname("隔离学习者");u.setPassword("not-a-production-login");u.setRole(role);users.insert(u);return u;}
 void map(String type,long resource,long kid){var m=new ResourceKnowledgeMapping();m.setResourceType(type);m.setResourceId(resource);m.setKnowledgeId(kid);m.setSource("MANUAL");m.setStatus("ACCEPTED");m.setConfidence(java.math.BigDecimal.ONE);mappings.insert(m);}
 @Test void realBrowserAndDatabaseFinalState()throws Exception{
  var u=user("USER");var other=user("USER");var admin=user("ADMIN");
  var category=new DocCategory();category.setName("私有Java并发");category.setOwnerId(u.getId());category.setStatus(1);categories.insert(category);
  var doc=new DocDocument();doc.setCategoryId(category.getId());doc.setTitle("volatile与CAS真实教材");doc.setContent("volatile保证可见性与有序性，不保证复合操作原子性。CAS是原子比较交换，须考虑ABA与重试。");doc.setStatus(1);docs.insert(doc);
  var k=new KgEntity();k.setName("volatile CAS "+UUID.randomUUID());k.setType("CONCEPT");k.setDocId(doc.getId());k.setCategoryId(category.getId());knowledge.insert(k);
  var path=new LearningPath();path.setTitle("Java并发最小闭环");path.setStatus(1);path.setOwnerUserId(u.getId());path.setChapterCount(1);paths.insert(path);
  var chapter=new LearningChapter();chapter.setPathId(path.getId());chapter.setTitle("volatile和CAS的边界");chapter.setContent(doc.getContent());chapter.setSortOrder(1);chapter.setDuration(10);chapter.setDocIds(doc.getId().toString());chapters.insert(chapter);
  var enrollment=new LearningUserPath();enrollment.setUserId(u.getId());enrollment.setPathId(path.getId());enrollment.setProgress(java.math.BigDecimal.ZERO);enrollment.setCompletedChapters(0);enrollments.insert(enrollment);
  var goal=new LearningGoal();goal.setUserId(u.getId());goal.setTitle("掌握Java并发中的volatile和CAS");goal.setStatus("ACTIVE");goal.setPathId(path.getId());goal.setDailyMinutes(30);goals.insert(goal);
  var q=new QuizQuestion();q.setTitle("volatile能否保证复合操作原子性");q.setContent("volatile可以保证i++的原子性。");q.setQuestionType("TRUE_FALSE");q.setAnswer("false");q.setStatus(1);q.setReviewStatus("APPROVED");q.setCategoryId(category.getId());q.setDocId(doc.getId());q.setExplanation("volatile保证可见性/有序性；i++是复合操作，需原子类CAS或锁。");questions.insert(q);
  var input=new FlashcardSaveDTO();input.setFront("volatile与CAS如何保证并发安全？");input.setBack(doc.getContent());input.setDocId(doc.getId());input.setCategoryId(category.getId());input.setChapterId(chapter.getId());input.setPathId(path.getId());var card=learning.createMyFlashcard(u.getId(),input);
  sql.update("UPDATE learning_flashcard SET next_review_time=? WHERE id=?",java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(1)),card.getId());
  map("QUIZ",q.getId(),k.getId());map("FLASHCARD",card.getId(),k.getId());map("DOC",doc.getId(),k.getId());
  var conv=new ChatConversation();conv.setUserId(u.getId());conv.setTitle("学习教练隔离验收");conv.setMessageCount(0);chats.insert(conv);
  Map<String,Object> fixture=new LinkedHashMap<>();fixture.put("user",u);fixture.put("token",jwt.generateToken(u.getId(),u.getUsername(),"USER"));fixture.put("otherToken",jwt.generateToken(other.getId(),other.getUsername(),"USER"));fixture.put("adminToken",jwt.generateToken(admin.getId(),admin.getUsername(),"ADMIN"));fixture.put("questionId",q.getId());fixture.put("cardId",card.getId());fixture.put("knowledgeId",k.getId());fixture.put("chapterId",chapter.getId());fixture.put("conversationId",conv.getId());
  Path temp=Files.createTempFile("knowflow-4h-browser-",".json");Path cwd=Path.of(System.getProperty("user.dir"));Path root=Files.isDirectory(cwd.resolve("frontend"))?cwd:cwd.getParent();
  FakeAiConfiguration.reset();FakeAiConfiguration.configured=false;
  try{
   Files.writeString(temp,json.writeValueAsString(fixture));var builder=new ProcessBuilder("node",root.resolve("scripts/tests/adaptive_learning_browser.cjs").toString());builder.directory(root.toFile());builder.environment().put("PHASE4H_FIXTURE",temp.toString());builder.environment().put("NODE_PATH","/Users/beiluo/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules");builder.redirectErrorStream(true);builder.redirectOutput(root.resolve("reports/phase4h/browser-run.log").toFile());
   var process=builder.start();assertTrue(process.waitFor(180,java.util.concurrent.TimeUnit.SECONDS),"Browser timeout");assertEquals(0,process.exitValue(),"See reports/phase4h/browser-run.log");
   int answers=sql.queryForObject("SELECT COUNT(*) FROM quiz_answer_record WHERE user_id=?",Integer.class,u.getId());assertEquals(2,answers);
   assertEquals(2,sql.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='QUESTION_ANSWERED'",Integer.class,u.getId()));
   assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='FLASHCARD_REVIEWED'",Integer.class,u.getId()));
   assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM learning_mistake WHERE user_id=?",Integer.class,u.getId()));
   assertTrue(sql.queryForObject("SELECT COUNT(*) FROM knowledge_mastery WHERE user_id=? AND knowledge_id=?",Integer.class,u.getId(),k.getId())>0);
   assertTrue(sql.queryForObject("SELECT COUNT(*) FROM learning_task_evidence p JOIN task t ON t.id=p.task_id WHERE t.user_id=? AND t.status=1",Integer.class,u.getId())>=2);
   assertEquals(1L,sql.queryForObject("SELECT fsrs_revision FROM learning_flashcard WHERE id=?",Long.class,card.getId()));
   assertEquals(0,FakeAiConfiguration.completeCalls,"Rule loop must not invoke unavailable AI");
   Files.writeString(root.resolve("reports/phase4h/browser-database.json"),json.writeValueAsString(Map.of("database","jdbc:h2:mem:phase4h_browser","objectiveAnswers",answers,"questionEvents",2,"fsrsEvents",1,"mistakes",1,"masteryPresent",true,"taskEvidenceAtLeast",2,"cardRevision",1,"aiCalls",0)));
  }finally{Files.deleteIfExists(temp);FakeAiConfiguration.reset();}
 }
}
