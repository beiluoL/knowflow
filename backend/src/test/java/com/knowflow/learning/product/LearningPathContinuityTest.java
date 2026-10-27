package com.knowflow.learning.product;
import com.fasterxml.jackson.databind.*;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.service.LearningService;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** 阅读/视频只保存过程，只有真实完成操作才能改变完成记录；隔离H2中的持久化合同。 */
@SpringBootTest(properties="knowflow.datasource.h2.url=jdbc:h2:mem:learning_product_a;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc @Import(FakeAiConfiguration.class)
class LearningPathContinuityTest {
 @Autowired MockMvc http;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;@Autowired JwtUtils jwt;
 @Autowired KbMemberMapper members; @Autowired DocCategoryMapper categories;
 @Autowired ResourceKnowledgeMappingMapper mappings; @Autowired DocDocumentMapper docs; @Autowired KgEntityMapper knowledge; @Autowired QuizQuestionMapper questions;
 @Autowired SysUserMapper users;@Autowired LearningPathMapper paths;@Autowired LearningChapterMapper chapters;@Autowired LearningService learning;
 record Fixture(long user,long path,long first,long next){}
 long user(String role){var u=new SysUser();u.setUsername("learning-a-"+UUID.randomUUID());u.setPassword("test-only");u.setRole(role);users.insert(u);return u.getId();}
 String token(long uid){return "Bearer "+jwt.generateToken(uid,"learning-a","USER");}
 Fixture fixture(boolean privatePath)throws Exception{long uid=user("USER");var p=new LearningPath();p.setTitle("Java + AI 路线·隔离验收");p.setStatus(1);p.setOwnerUserId(privatePath?uid:0L);p.setChapterCount(2);p.setEnrolledCount(0);paths.insert(p);var a=new LearningChapter();a.setPathId(p.getId());a.setTitle("HashMap基础");a.setSortOrder(1);a.setContent("## 哈希定位\n先独立阅读，再做真实练习。\n## 边界\n阅读不代表掌握。");chapters.insert(a);var n=new LearningChapter();n.setPathId(p.getId());n.setTitle("HashMap边界");n.setSortOrder(2);n.setPrerequisiteChapterIds(a.getId().toString());n.setContent("## 下一节");chapters.insert(n);return new Fixture(uid,p.getId(),a.getId(),n.getId());}
 JsonNode getData(String uri,long uid)throws Exception{return json.readTree(http.perform(get(uri).header("Authorization",token(uid))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString()).path("data");}
 void write(String uri,long uid,int code)throws Exception{http.perform(post(uri).header("Authorization",token(uid))).andExpect(status().is(code)).andExpect(jsonPath("$.code").value(code));}
 long events(long uid,String type){return db.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type=?",Long.class,uid,type);}
 @Test void partialVideoDoesNotCompleteOrUnlockChapterAndCanThenCompleteExactlyOnce()throws Exception{
  var f=fixture(false);write("/api/learning/paths/"+f.path+"/enroll",f.user,200);
  write("/api/learning/chapters/"+f.first+"/video-progress?progress=35",f.user,200);
  assertFalse(getData("/api/learning/chapters/"+f.first,f.user).path("completed").asBoolean());
  assertFalse(getData("/api/learning/paths/"+f.path+"/chapters",f.user).get(0).path("completed").asBoolean());
  assertTrue(getData("/api/learning/chapters/"+f.next,f.user).path("locked").asBoolean());
  assertEquals("available",getData("/api/learning/paths/"+f.path+"/dag",f.user).path("nodes").get(0).path("status").asText());
  assertEquals(0,events(f.user,"CHAPTER_COMPLETE"));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM knowledge_mastery WHERE user_id=?",Integer.class,f.user));
  write("/api/learning/chapters/"+f.first+"/complete",f.user,200);write("/api/learning/chapters/"+f.first+"/complete",f.user,200);
  assertTrue(getData("/api/learning/chapters/"+f.first,f.user).path("completed").asBoolean());assertEquals(1,events(f.user,"CHAPTER_COMPLETE"));assertEquals(50,getData("/api/learning/paths/"+f.path,f.user).path("progress").asInt());
 }
 @Test void staleSeededCountsAreNotShownAsActualLearnersOrChapterCounts()throws Exception{var f=fixture(false);db.update("UPDATE learning_path SET enrolled_count=999,chapter_count=88,total_duration=9999 WHERE id=?",f.path);var detail=getData("/api/learning/paths/"+f.path,f.user);assertEquals(0,detail.path("enrolledCount").asInt());assertEquals(2,detail.path("chapterCount").asInt());}
 @Test void duplicateEnrollmentIsIdempotentAndDoesNotIncreaseCountsOrResetProgress()throws Exception{
  var f=fixture(false);write("/api/learning/paths/"+f.path+"/enroll",f.user,200);write("/api/learning/chapters/"+f.first+"/complete",f.user,200);write("/api/learning/paths/"+f.path+"/enroll",f.user,200);
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM learning_user_path WHERE user_id=? AND path_id=?",Integer.class,f.user,f.path));assertEquals(1,paths.selectById(f.path).getEnrolledCount());assertEquals(50,getData("/api/learning/paths/"+f.path,f.user).path("progress").asInt());
 }
 @Test void otherUserCannotEnrollOrWriteProgressOnPrivatePath()throws Exception{
  var f=fixture(true);long other=user("USER");write("/api/learning/paths/"+f.path+"/enroll",other,403);write("/api/learning/chapters/"+f.first+"/video-progress?progress=20",other,403);assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM learning_user_path WHERE user_id=?",Integer.class,other));
 }
 @Test void hiddenPathCannotBeEnrolledOrStudied()throws Exception{var f=fixture(false);db.update("UPDATE learning_path SET status=0 WHERE id=?",f.path);write("/api/learning/paths/"+f.path+"/enroll",f.user,404);write("/api/learning/chapters/"+f.first+"/video-progress?progress=20",f.user,404);}
 @Test void videoRequiresEnrollmentAndValidProgress()throws Exception{var f=fixture(false);write("/api/learning/chapters/"+f.first+"/video-progress?progress=20",f.user,409);write("/api/learning/paths/"+f.path+"/enroll",f.user,200);write("/api/learning/chapters/"+f.first+"/video-progress?progress=-1",f.user,400);write("/api/learning/chapters/"+f.first+"/video-progress?progress=101",f.user,400);}
 @Test void incompletePrerequisiteReturns409AndDoesNotWriteCompletion()throws Exception{var f=fixture(false);write("/api/learning/paths/"+f.path+"/enroll",f.user,200);write("/api/learning/chapters/"+f.next+"/complete",f.user,409);assertEquals(0,events(f.user,"CHAPTER_COMPLETE"));}
 @Test void concurrentCompletionProducesOneChapterAndPathProgress()throws Exception{var f=fixture(false);learning.enrollPath(f.path,f.user);var pool=Executors.newFixedThreadPool(8);var start=new CyclicBarrier(8);try{var futures=new ArrayList<Future<?>>();for(int i=0;i<8;i++)futures.add(pool.submit(()->{try{start.await(10,TimeUnit.SECONDS);learning.completeChapter(f.first,f.user);}catch(Exception e){throw new IllegalStateException(e);}}));for(var x:futures)x.get(30,TimeUnit.SECONDS);}finally{pool.shutdownNow();}assertEquals(1,events(f.user,"CHAPTER_COMPLETE"));assertEquals(50,learning.getPathDetail(f.path,f.user).getProgress().intValue());assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM learning_user_chapter WHERE user_id=? AND chapter_id=?",Integer.class,f.user,f.first));}
 @Test void readingBookmarkPersistsWithoutCompletionAndRejectsStaleTab()throws Exception{
  var f=fixture(false);write("/api/learning/paths/"+f.path+"/enroll",f.user,200);
  http.perform(post("/api/learning/chapters/"+f.first+"/reading-position").header("Authorization",token(f.user)).contentType("application/json").content(json.writeValueAsString(Map.of("anchor","heading-1","expectedRevision",0,"expectedContentHash",learning.getChapterDetail(f.first,f.user).getContentHash()))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.readingRevision").value(1));
  var recovered=getData("/api/learning/chapters/"+f.first,f.user);assertEquals("heading-1",recovered.path("readingAnchor").asText());assertFalse(recovered.path("completed").asBoolean());assertEquals(0,events(f.user,"CHAPTER_COMPLETE"));
  http.perform(post("/api/learning/chapters/"+f.first+"/reading-position").header("Authorization",token(f.user)).contentType("application/json").content(json.writeValueAsString(Map.of("anchor","top","expectedRevision",0,"expectedContentHash",learning.getChapterDetail(f.first,f.user).getContentHash()))))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
  assertEquals("heading-1",getData("/api/learning/chapters/"+f.first,f.user).path("readingAnchor").asText());
  long other=user("USER");assertTrue(getData("/api/learning/chapters/"+f.first,other).path("readingAnchor").isNull());
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM knowledge_mastery WHERE user_id=?",Integer.class,f.user));
 }
 @Test void chapterPracticeUsesAcceptedKnowledgeAndOnlyApprovedReadableQuestions()throws Exception{
  var f=fixture(false);var d=new DocDocument();d.setTitle("原教材");d.setStatus(1);docs.insert(d);db.update("UPDATE learning_chapter SET doc_ids=? WHERE id=?",d.getId().toString(),f.first);
  var k=new KgEntity();k.setName("真实HashMap");k.setType("CONCEPT");k.setDocId(d.getId());knowledge.insert(k);
  var m=new ResourceKnowledgeMapping();m.setResourceType("DOC");m.setResourceId(d.getId());m.setKnowledgeId(k.getId());m.setStatus("ACCEPTED");m.setSource("MANUAL");mappings.insert(m);
  long approved=0;for(String review:List.of("APPROVED","PENDING_REVIEW")) {var q=new QuizQuestion();q.setTitle(review);q.setContent("HashMap是否线程安全");q.setQuestionType("TRUE_FALSE");q.setAnswer("false");q.setStatus(1);q.setReviewStatus(review);q.setDocId(d.getId());questions.insert(q);if(review.equals("APPROVED"))approved=q.getId();var qm=new ResourceKnowledgeMapping();qm.setResourceType("QUIZ");qm.setResourceId(q.getId());qm.setKnowledgeId(k.getId());qm.setStatus("ACCEPTED");qm.setSource("MANUAL");mappings.insert(qm);}
  var result=getData("/api/learning/chapters/"+f.first+"/knowledge",f.user);assertEquals(1,result.path("questions").size());assertEquals(approved,result.path("questions").get(0).path("id").asLong());assertFalse(result.path("questions").get(0).has("answer"));
 }
 @Test void anonymousAndAdminCannotWriteAnOthersPrivateStudyState()throws Exception{
  var f=fixture(true);http.perform(post("/api/learning/paths/"+f.path+"/enroll")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
  long admin=user("ADMIN");write("/api/learning/paths/"+f.path+"/enroll",admin,403);write("/api/learning/chapters/"+f.first+"/complete",admin,403);
  http.perform(post("/api/learning/chapters/"+f.first+"/reading-position").header("Authorization",token(admin)).contentType("application/json").content(json.writeValueAsString(Map.of("anchor","top","expectedRevision",0,"expectedContentHash",learning.getChapterDetail(f.first,f.user).getContentHash())))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
 }
 @Test void editorAndReaderSeeOnlyActuallySharedChapterKnowledge()throws Exception{
  var f=fixture(false);var category=new DocCategory();category.setName("本批私有知识库");category.setOwnerId(f.user);category.setStatus(1);categories.insert(category);
  var doc=new DocDocument();doc.setTitle("共享教材");doc.setCategoryId(category.getId());doc.setStatus(1);docs.insert(doc);db.update("UPDATE learning_chapter SET doc_ids=? WHERE id=?",doc.getId().toString(),f.first);
  var k=new KgEntity();k.setName("共享知识");k.setDocId(doc.getId());k.setCategoryId(category.getId());k.setType("CONCEPT");knowledge.insert(k);
  var m=new ResourceKnowledgeMapping();m.setResourceType("DOC");m.setResourceId(doc.getId());m.setKnowledgeId(k.getId());m.setSource("MANUAL");m.setStatus("ACCEPTED");mappings.insert(m);
  for(String role:List.of("EDITOR","READER")){long uid=user("USER");var member=new KbMember();member.setCategoryId(category.getId());member.setUserId(uid);member.setRole(role);member.setStatus(1);members.insert(member);assertEquals(1,getData("/api/learning/chapters/"+f.first+"/knowledge",uid).path("knowledge").size());}
  assertEquals(0,getData("/api/learning/chapters/"+f.first+"/knowledge",user("USER")).path("knowledge").size());assertEquals(1,getData("/api/learning/chapters/"+f.first+"/knowledge",f.user).path("knowledge").size());
 }
 @Test void oldRenderedChapterCannotSaveBookmarkAgainstChangedSource()throws Exception{
  var f=fixture(false);write("/api/learning/paths/"+f.path+"/enroll",f.user,200);String oldHash=getData("/api/learning/chapters/"+f.first,f.user).path("contentHash").asText();
  db.update("UPDATE learning_chapter SET content='## changed' WHERE id=?",f.first);
  http.perform(post("/api/learning/chapters/"+f.first+"/reading-position").header("Authorization",token(f.user)).contentType("application/json").content(json.writeValueAsString(Map.of("anchor","heading-1","expectedRevision",0,"expectedContentHash",oldHash))))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM learning_user_chapter WHERE user_id=? AND chapter_id=?",Integer.class,f.user,f.first));
 }
}
