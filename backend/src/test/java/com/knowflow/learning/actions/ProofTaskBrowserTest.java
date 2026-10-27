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
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.address=127.0.0.1"})
@ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.config-file=/tmp/knowflow-phaseg-browser-no-config.json","knowflow.datasource.h2.url=jdbc:h2:mem:phaseg_browser;DB_CLOSE_DELAY=-1;MODE=MySQL"})
@EnabledIfSystemProperty(named="phaseg.browser",matches="true")
class ProofTaskBrowserTest {
 @org.springframework.boot.test.web.server.LocalServerPort int port;
 @Autowired DailyLearningPlanService plans;
 @Autowired com.knowflow.service.TaskService tasks;
 @Autowired SysUserMapper users;@Autowired DocCategoryMapper categories;@Autowired DocDocumentMapper docs;@Autowired KgEntityMapper knowledge;
 @Autowired LearningPathMapper paths;@Autowired LearningChapterMapper chapters;@Autowired LearningUserPathMapper enrollments;@Autowired LearningGoalMapper goals;
 @Autowired QuizQuestionMapper questions;@Autowired ResourceKnowledgeMappingMapper mappings;@Autowired ChatConversationMapper chats;
 @Autowired LearningService learning;@Autowired JwtUtils jwt;@Autowired ObjectMapper json;@Autowired JdbcTemplate sql;
 SysUser user(String role){var u=new SysUser();u.setUsername("g-browser-"+UUID.randomUUID());u.setNickname("隔离学习者");u.setPassword("not-a-production-login");u.setRole(role);users.insert(u);return u;}
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
  // 真实确认计划，仅选这两种资源；不伪造学习事件或证据。
  var req=new PlanModels.Request();req.setAvailableMinutes(60);req.setIncludeInterview(false);req.setUseAi(false);
  var draft=plans.preview(u.getId(),req);var selected=draft.tasks().stream().filter(i->"QUIZ".equals(i.resourceType())||"FLASHCARD".equals(i.resourceType())).toList();assertEquals(2,selected.size());
  var confirm=new PlanModels.ConfirmRequest();confirm.setVersion(draft.version());confirm.setConfirmationToken(draft.confirmationToken());confirm.setIdempotencyKey(UUID.randomUUID().toString());confirm.setTasks(selected.stream().map(i->new PlanModels.EditedItem(i.key(),i.title(),i.estimatedMinutes(),i.priority())).toList());
  var saved=plans.confirm(u.getId(),draft.planId(),confirm);
  var todo=new com.knowflow.dto.TaskDTO();todo.setTitle("G浏览器普通Todo");todo.setScheduledDate(LocalDate.now());long todoId=tasks.createTask(u.getId(),todo);
  var legacy=new LearningTask();legacy.setTitle("G浏览器旧学习活动");legacy.setStatus(0);legacy.setType("REVIEW");learning.createTask(legacy,u.getId());
  long quizTask=saved.createdTasks().stream().filter(t->t.title().contains(q.getTitle())).findFirst().orElseThrow().id();
  long cardTask=saved.createdTasks().stream().filter(t->t.id()!=quizTask).findFirst().orElseThrow().id();
  Map<String,Object> fixture=new LinkedHashMap<>();fixture.put("user",u);fixture.put("token",jwt.generateToken(u.getId(),u.getUsername(),"USER"));fixture.put("otherToken",jwt.generateToken(other.getId(),other.getUsername(),"USER"));fixture.put("adminToken",jwt.generateToken(admin.getId(),admin.getUsername(),"ADMIN"));fixture.put("questionId",q.getId());fixture.put("cardId",card.getId());fixture.put("knowledgeId",k.getId());fixture.put("chapterId",chapter.getId());fixture.put("conversationId",conv.getId());fixture.put("backendUrl","http://127.0.0.1:"+port);fixture.put("todoId",todoId);fixture.put("quizTaskId",quizTask);fixture.put("cardTaskId",cardTask);fixture.put("legacyTaskId",legacy.getId());
  Path temp=Files.createTempFile("knowflow-g-browser-",".json");Path cwd=Path.of(System.getProperty("user.dir"));Path root=Files.isDirectory(cwd.resolve("frontend"))?cwd:cwd.getParent();
  FakeAiConfiguration.reset();FakeAiConfiguration.configured=false;
  try{
   Files.createDirectories(root.resolve("docs/roadmap/evidence/phase-g/browser"));
   Files.setPosixFilePermissions(temp,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
   Files.writeString(temp,json.writeValueAsString(fixture));var builder=new ProcessBuilder("node",root.resolve("scripts/tests/phase_g_browser.cjs").toString());builder.directory(root.toFile());builder.environment().put("PHASEG_FIXTURE",temp.toString());builder.environment().put("NODE_PATH","/Users/beiluo/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules");builder.redirectErrorStream(true);builder.redirectOutput(root.resolve("docs/roadmap/evidence/phase-g/browser/browser-run.log").toFile());
   var process=builder.start();if(!process.waitFor(240,java.util.concurrent.TimeUnit.SECONDS)){process.descendants().forEach(ProcessHandle::destroy);process.destroy();fail("Browser timeout; owned subprocesses stopped");}assertEquals(0,process.exitValue(),"See docs/roadmap/evidence/phase-g/browser/browser-run.log");
   int answers=sql.queryForObject("SELECT COUNT(*) FROM quiz_answer_record WHERE user_id=?",Integer.class,u.getId());assertEquals(1,answers);
   assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='QUESTION_ANSWERED'",Integer.class,u.getId()));
   assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='FLASHCARD_REVIEWED'",Integer.class,u.getId()));
   assertEquals(0,sql.queryForObject("SELECT COUNT(*) FROM learning_mistake WHERE user_id=?",Integer.class,u.getId()));
   assertTrue(sql.queryForObject("SELECT COUNT(*) FROM knowledge_mastery WHERE user_id=? AND knowledge_id=?",Integer.class,u.getId(),k.getId())>0);
   assertTrue(sql.queryForObject("SELECT COUNT(*) FROM learning_task_evidence p JOIN task t ON t.id=p.task_id WHERE t.user_id=? AND t.status=1",Integer.class,u.getId())>=2);
   assertEquals(1L,sql.queryForObject("SELECT fsrs_revision FROM learning_flashcard WHERE id=?",Long.class,card.getId()));
   assertEquals(0,FakeAiConfiguration.completeCalls,"Rule loop must not invoke unavailable AI");
   assertEquals(1,sql.queryForObject("SELECT status FROM task WHERE id=?",Integer.class,todoId));
   assertEquals(1,sql.queryForObject("SELECT status FROM learning_task WHERE id=?",Integer.class,legacy.getId()));
   assertEquals(2,sql.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='LEARNING_TASK_COMPLETE' AND resource_id=?",Integer.class,u.getId(),legacy.getId()));
   assertEquals(2,sql.queryForObject("SELECT COUNT(*) FROM reward_ledger WHERE user_id=? AND reason='LEARNING_TASK_COMPLETE'",Integer.class,u.getId()));
   Files.writeString(root.resolve("docs/roadmap/evidence/phase-g/browser/proof-links.json"),json.writeValueAsString(sql.queryForList("SELECT t.id AS task_id,p.event_id,e.event_type,e.resource_id FROM task t JOIN learning_task_evidence p ON p.task_id=t.id JOIN learning_event e ON e.id=p.event_id WHERE t.user_id=? ORDER BY t.id",u.getId())));
   Files.writeString(root.resolve("docs/roadmap/evidence/phase-g/browser/browser-database.json"),json.writeValueAsString(Map.of("database","jdbc:h2:mem:phaseg_browser","objectiveAnswers",answers,"questionEvents",1,"fsrsEvents",1,"mistakes",0,"masteryPresent",true,"taskEvidenceAtLeast",2,"cardRevision",1,"aiCalls",0)));
  }finally{Files.deleteIfExists(temp);FakeAiConfiguration.reset();}
 }
}
