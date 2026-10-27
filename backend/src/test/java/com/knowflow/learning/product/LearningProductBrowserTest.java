package com.knowflow.learning.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.address=127.0.0.1","knowflow.datasource.h2.url=jdbc:h2:mem:learning_product_browser;MODE=MySQL;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@EnabledIfSystemProperty(named="learning.product.browser",matches="true")
class LearningProductBrowserTest {
 @org.springframework.boot.test.web.server.LocalServerPort int port;
 @Autowired SysUserMapper users; @Autowired LearningPathMapper paths; @Autowired LearningChapterMapper chapters;
 @Autowired DocDocumentMapper docs; @Autowired KgEntityMapper knowledge; @Autowired QuizQuestionMapper questions;
 @Autowired ResourceKnowledgeMappingMapper mappings; @Autowired PasswordEncoder encoder;
 @Autowired ObjectMapper json; @Autowired JdbcTemplate db;
 void map(String type,long resource,long kid){var m=new ResourceKnowledgeMapping();m.setResourceType(type);m.setResourceId(resource);m.setKnowledgeId(kid);m.setSource("MANUAL");m.setStatus("ACCEPTED");mappings.insert(m);}
 @Test void realLoginReadingRecoveryAndApprovedPractice()throws Exception {
  var user=new SysUser();user.setUsername("lp-"+UUID.randomUUID());user.setNickname("隔离学习者");user.setPassword(encoder.encode("Learning-test-only-2026!"));user.setRole("USER");users.insert(user);
  var path=new LearningPath();path.setTitle("Java + AI 工程师隔离路线");path.setOwnerUserId(0L);path.setStatus(1);path.setLevel("进阶");paths.insert(path);
  var doc=new DocDocument();doc.setTitle("HashMap隔离教材");doc.setStatus(1);doc.setContent("公开隔离测试教材");docs.insert(doc);
  var k=new KgEntity();k.setName("HashMap隔离概念");k.setType("CONCEPT");k.setDocId(doc.getId());knowledge.insert(k);
  var chapter=new LearningChapter();chapter.setTitle("HashMap 基础与边界");chapter.setPathId(path.getId());chapter.setSortOrder(1);chapter.setDocIds(doc.getId().toString());chapter.setContent("## 哈希定位\n"+"阅读过程不表示知识掌握。\n\n".repeat(28)+"## 边界总结\nHashMap不是线程安全的。\n\n"+"请先作答，再看解析。\n\n".repeat(20));chapters.insert(chapter);
  var next=new LearningChapter();next.setPathId(path.getId());next.setTitle("下一章的真实前置要求");next.setSortOrder(2);next.setPrerequisiteChapterIds(chapter.getId().toString());chapters.insert(next);
  var q=new QuizQuestion();q.setTags("lpv1.hashmap");q.setVersion(1);q.setCanonicalKey("lpv1-isolated-hashmap-question");q.setTitle("HashMap是否线程安全");q.setContent("HashMap在多线程共享写入时无需同步。");q.setQuestionType("TRUE_FALSE");q.setAnswer("false");q.setExplanation("共享HashMap并发写需要适当同步；读取章节不等于答对。");q.setDocId(doc.getId());q.setStatus(1);q.setReviewStatus("APPROVED");questions.insert(q);
  map("DOC",doc.getId(),k.getId());map("QUIZ",q.getId(),k.getId());
  var f=new LinkedHashMap<String,Object>();f.put("username",user.getUsername());f.put("password","Learning-test-only-2026!");f.put("userId",user.getId());f.put("pathId",path.getId());f.put("chapterId",chapter.getId());f.put("nextId",next.getId());f.put("questionId",q.getId());f.put("docId",doc.getId());f.put("knowledgeId",k.getId());f.put("backendUrl","http://127.0.0.1:"+port);
  Path cwd=Path.of(System.getProperty("user.dir"));Path root=Files.isDirectory(cwd.resolve("frontend"))?cwd:cwd.getParent();Path out=root.resolve("docs/learning-product/evidence/browser");Files.createDirectories(out);
  Path fixture=Files.createTempFile("knowflow-learning-product-",".json");Files.setPosixFilePermissions(fixture,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
  FakeAiConfiguration.reset();FakeAiConfiguration.configured=false;
  try {
   Files.writeString(fixture,json.writeValueAsString(f));var builder=new ProcessBuilder("node",root.resolve("scripts/tests/learning_product_browser.cjs").toString());builder.directory(root.toFile());builder.environment().put("LEARNING_PRODUCT_FIXTURE",fixture.toString());builder.environment().put("NODE_PATH","/Users/beiluo/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules");builder.redirectErrorStream(true);builder.redirectOutput(out.resolve("browser-run.log").toFile());var process=builder.start();
   if(!process.waitFor(240,TimeUnit.SECONDS)){process.descendants().forEach(ProcessHandle::destroy);process.destroy();fail("Owned browser test timed out");}assertEquals(0,process.exitValue(),"See learning-product browser evidence");
   assertEquals(1L,db.queryForObject("SELECT reading_revision FROM learning_user_chapter WHERE user_id=? AND chapter_id=?",Long.class,user.getId(),chapter.getId()));
   assertNull(db.queryForObject("SELECT complete_time FROM learning_user_chapter WHERE user_id=? AND chapter_id=?",java.sql.Timestamp.class,user.getId(),chapter.getId()));
   assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM quiz_answer_record WHERE user_id=?",Integer.class,user.getId()));
   assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='QUESTION_ANSWERED'",Integer.class,user.getId()));
   assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM exam_answer a JOIN exam_attempt t ON t.id=a.attempt_id WHERE t.user_id=?",Integer.class,user.getId()));
   assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='FLASHCARD_REVIEWED'",Integer.class,user.getId()));
   assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM learning_flashcard WHERE user_id=? AND fsrs_revision=1 AND next_review_time IS NOT NULL",Integer.class,user.getId()));
   assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='CHAPTER_COMPLETE'",Integer.class,user.getId()));assertEquals(0,FakeAiConfiguration.completeCalls);
   var links=new LinkedHashMap<String,Object>();links.put("userId",user.getId());links.put("pathId",path.getId());links.put("chapterId",chapter.getId());links.put("docId",doc.getId());links.put("questionId",q.getId());links.put("knowledgeId",k.getId());
   links.put("answers",db.queryForList("SELECT id,question_id,is_correct FROM quiz_answer_record WHERE user_id=?",user.getId()));
   links.put("attempts",db.queryForList("SELECT id,paper_id,status,total_score FROM exam_attempt WHERE user_id=?",user.getId()));
   links.put("events",db.queryForList("SELECT id,event_type,resource_type,resource_id FROM learning_event WHERE user_id=? ORDER BY id",user.getId()));
   links.put("cards",db.queryForList("SELECT id,chapter_id,doc_id,fsrs_revision,next_review_time FROM learning_flashcard WHERE user_id=?",user.getId()));
   links.put("legacyNullReviewPublishedCount",db.queryForObject("SELECT COUNT(*) FROM quiz_question WHERE status=1 AND review_status IS NULL AND deleted=0",Integer.class));
   links.put("tasks",db.queryForList("SELECT id,status,target_id FROM learning_task WHERE user_id=?",user.getId()));
   Files.writeString(out.resolve("identity-reconciliation.json"),json.writeValueAsString(links));
   Files.writeString(out.resolve("database.json"),json.writeValueAsString(Map.of("database","isolated H2 memory","readingRevision",1,"chapterCompleted",false,"answers",1,"questionEvents",2,"chapterCompleteEvents",0,"aiCalls",0)));
  } finally {Files.deleteIfExists(fixture);FakeAiConfiguration.reset();}
 }
}
