package com.knowflow.controller;

import com.fasterxml.jackson.databind.*;
import com.knowflow.service.impl.AiServiceImpl;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"knowflow.product.private-quiz.enabled=true","knowflow.product.tutor.enabled=true","knowflow.product.study-pack.enabled=true"})
@AutoConfigureMockMvc @ActiveProfiles("local")
class StudyPackHttpTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JwtUtils jwt;@Autowired JdbcTemplate sql;
 @MockBean AiServiceImpl ai;
 final long owner=98501;
 String auth(long user){return "Bearer "+jwt.generateToken(user,"pack-"+user,user==98503?"ADMIN":"USER");}
 @BeforeEach void provider(){when(ai.isConfigured(anyLong())).thenReturn(true);when(ai.complete(anyString(),anyString(),isNull(String.class),anyLong())).thenAnswer(inv->{String system=inv.getArgument(0);if(system.contains("SOURCE_NOTE_V1"))return "{\"title\":\"课程学习笔记\",\"content\":\"# Java与Spring\\n\\n## JVM与GC\\nJVM提供GC。\\n\\n## IoC\\nSpring管理Bean。\",\"chapters\":[{\"title\":\"JVM与GC\",\"knowledgePoints\":[\"垃圾回收\"]},{\"title\":\"IoC\",\"knowledgePoints\":[\"依赖注入\"]}]}";if(system.contains("SOURCE_CARDS_V1"))return "{\"flashcards\":[{\"front\":\"GC是什么\",\"back\":\"垃圾回收\"},{\"front\":\"JVM是什么\",\"back\":\"Java虚拟机\"},{\"front\":\"IoC是什么\",\"back\":\"控制反转\"},{\"front\":\"Bean由谁管理\",\"back\":\"Spring容器\"},{\"front\":\"事务保证什么\",\"back\":\"原子性\"}]}";return "[{\"type\":\"TRUE_FALSE\",\"content\":\"JVM提供GC\",\"options\":[],\"answer\":\"true\",\"explanation\":\"原文说明\"},{\"type\":\"TRUE_FALSE\",\"content\":\"IoC管理Bean\",\"options\":[],\"answer\":\"true\",\"explanation\":\"原文说明\"},{\"type\":\"TRUE_FALSE\",\"content\":\"事务没有原子性\",\"options\":[],\"answer\":\"false\",\"explanation\":\"原文说明\"}]";});}
 JsonNode call(String method,String path,Object body,long user,int expected)throws Exception{var req=method.equals("POST")?post(path):get(path);return json.readTree(mvc.perform(req.header("Authorization",auth(user)).contentType("application/json").content(body==null?"":json.writeValueAsString(body))).andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(expected)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");}
 JsonNode upload(String filename,String text)throws Exception{return json.readTree(mvc.perform(multipart("/api/learning/study-packs").file(new MockMultipartFile("file",filename,"text/plain",text.getBytes(java.nio.charset.StandardCharsets.UTF_8))).header("Authorization",auth(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");}
 JsonNode terminal(long id)throws Exception{long until=System.currentTimeMillis()+20000;JsonNode job=null;while(System.currentTimeMillis()<until){job=call("GET","/api/learning/study-packs/"+id,null,owner,200);if(!Set.of("QUEUED","RUNNING").contains(job.path("state").asText()))return job;Thread.sleep(30);}fail("Study pack did not reach a real terminal state: "+job);return null;}
 @Test void uploadGeneratesPrivateDurableDraftsThenExplicitAcceptanceIsIdempotent()throws Exception{
  var text="JVM提供GC，Spring通过IoC管理Bean，事务保证原子性。完整包测试";var first=upload("course.md",text);long id=first.path("id").asLong();var job=terminal(id);assertEquals("DRAFT_READY",job.path("state").asText());assertEquals(id,upload("course.md",text).path("id").asLong());
  assertEquals(5,job.path("cardDrafts").size());assertTrue(job.path("note").path("content").asText().contains("##"));assertEquals(5,job.path("steps").size());
  long note=job.path("note").path("id").asLong(),quiz=job.path("quizId").asLong();assertTrue(note>0);assertTrue(quiz>0);assertTrue(job.path("conversationId").asLong()>0);
  for(long user:new long[]{98502,98503}){call("GET","/api/learning/study-packs/"+id,null,user,404);call("GET","/api/notes/"+note,null,user,404);call("GET","/api/docs/"+job.path("docId").asLong(),null,user,403);}
  var q=call("GET","/api/learning/private-quizzes/"+quiz,null,owner,200);call("POST","/api/learning/private-quizzes/"+quiz+"/confirm",Map.of("questions",q.path("questions")),owner,200);
  var body=Map.of("cards",job.path("cardDrafts"),"expectedRevision",job.path("revision").asLong());var accepted=call("POST","/api/learning/study-packs/"+id+"/accept",body,owner,200);assertEquals("READY",accepted.path("state").asText());assertEquals(5,accepted.path("flashcardIds").size());
  assertEquals(accepted.path("flashcardIds"),call("POST","/api/learning/study-packs/"+id+"/accept",body,owner,200).path("flashcardIds"));var changed=job.path("cardDrafts").deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)changed.get(0)).put("front","不同内容不得覆盖");call("POST","/api/learning/study-packs/"+id+"/accept",Map.of("cards",changed,"expectedRevision",job.path("revision").asLong()),owner,409);
  assertEquals(5,sql.queryForObject("SELECT COUNT(*) FROM learning_flashcard WHERE user_id=? AND doc_id=?",Integer.class,owner,job.path("docId").asLong()));
 }
 @Test void individualFailureKeepsSuccessfulNoteAndRetryDoesNotRegenerateIt()throws Exception{
  java.util.concurrent.atomic.AtomicBoolean failCards=new java.util.concurrent.atomic.AtomicBoolean(true);
  // The fixture delegates all other calls to the deterministic provider established above.
  var normal=ai.complete("SOURCE_NOTE_V1","",(String)null,owner);String normalCards=ai.complete("SOURCE_CARDS_V1","",(String)null,owner);String normalQuiz=ai.complete("QUIZ","",(String)null,owner);
  when(ai.complete(anyString(),anyString(),isNull(String.class),anyLong())).thenAnswer(inv->{String system=inv.getArgument(0);if(system.contains("SOURCE_CARDS_V1")){if(failCards.get())throw new IllegalStateException("controlled provider failure");return normalCards;}return system.contains("SOURCE_NOTE_V1")?normal:normalQuiz;});
  long id=upload("partial.txt","JVM提供GC，Spring管理Bean。部分失败测试").path("id").asLong();var partial=terminal(id);assertEquals("PARTIAL_READY",partial.path("state").asText());long note=partial.path("note").path("id").asLong();assertTrue(note>0);
  failCards.set(false);call("POST","/api/learning/study-packs/"+id+"/retry",Map.of("step","CARDS"),owner,200);var done=terminal(id);assertEquals("DRAFT_READY",done.path("state").asText());assertEquals(note,done.path("note").path("id").asLong());
  assertEquals(1,sql.queryForObject("SELECT run_count FROM study_pack_step WHERE job_id=? AND kind='NOTE'",Integer.class,id));assertEquals(2,sql.queryForObject("SELECT run_count FROM study_pack_step WHERE job_id=? AND kind='CARDS'",Integer.class,id));
 }
 @Test void unsupportedFilesAndAnonymousAreRejectedWithoutFakeJobs()throws Exception{
  mvc.perform(multipart("/api/learning/study-packs").file(new MockMultipartFile("file","x.txt","text/plain","x".getBytes()))).andExpect(status().isUnauthorized());
  mvc.perform(multipart("/api/learning/study-packs").file(new MockMultipartFile("file","x.exe","application/octet-stream","x".getBytes())).header("Authorization",auth(owner))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
 }

 @Autowired com.knowflow.learning.studypack.StudyPackService packService;
 @Test void committedArtifactsRecoverWithoutRegenerationAndCompleteJobsReconcileAfterRestart()throws Exception{
  long id=upload("recover.txt","JVM提供GC。恢复已提交产物测试").path("id").asLong();var ready=terminal(id);long note=ready.path("note").path("id").asLong();
  sql.update("UPDATE study_pack_step SET state='FAILED' WHERE job_id=? AND kind='NOTE'",id);sql.update("UPDATE study_pack_job SET state='PARTIAL_READY' WHERE id=?",id);
  call("POST","/api/learning/study-packs/"+id+"/retry",Map.of("step","NOTE"),owner,200);assertEquals("DRAFT_READY",terminal(id).path("state").asText());assertEquals(note,terminal(id).path("note").path("id").asLong());assertEquals(1,sql.queryForObject("SELECT run_count FROM study_pack_step WHERE job_id=? AND kind='NOTE'",Integer.class,id));
  sql.update("UPDATE study_pack_job SET state='RUNNING' WHERE id=?",id);org.springframework.test.util.ReflectionTestUtils.invokeMethod(packService,"initialize");assertEquals("DRAFT_READY",terminal(id).path("state").asText());
 }
 @Test void editingGeneratedNoteCanRegenerateOnlyQuizAndKeepsOldQuiz()throws Exception{
  long id=upload("edited-note.md","Spring通过IoC管理Bean。笔记编辑测试").path("id").asLong();var ready=terminal(id);long note=ready.path("note").path("id").asLong(),oldQuiz=ready.path("quizId").asLong();
  mvc.perform(put("/api/notes/"+note).header("Authorization",auth(owner)).contentType("application/json").content(json.writeValueAsString(Map.of("title","已编辑学习笔记","content","## 新解释\nSpring管理Bean。","revision",1)))).andExpect(status().isOk());
  call("POST","/api/learning/study-packs/"+id+"/retry",Map.of("step","QUIZ"),owner,200);var regenerated=terminal(id);assertEquals("DRAFT_READY",regenerated.path("state").asText());assertNotEquals(oldQuiz,regenerated.path("quizId").asLong());call("GET","/api/learning/private-quizzes/"+oldQuiz,null,owner,200);assertEquals(1,sql.queryForObject("SELECT run_count FROM study_pack_step WHERE job_id=? AND kind='NOTE'",Integer.class,id));
 }
 @Test void editingParsedSourceCannotSilentlyChangePackProvenance()throws Exception{
  long id=upload("source-edit.txt","JVM提供GC。资料修订测试").path("id").asLong();var ready=terminal(id);sql.update("UPDATE doc_document SET content='已变更的新资料' WHERE id=?",ready.path("docId").asLong());call("GET","/api/learning/study-packs/"+id,null,owner,409);
 }

 JsonNode uploadBytes(String filename,byte[] bytes)throws Exception{return json.readTree(mvc.perform(multipart("/api/learning/study-packs").file(new MockMultipartFile("file",filename,"application/pdf",bytes)).header("Authorization",auth(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");}
 @Test void realTextPdfParsesAndBlankPdfDoesNotBecomeFakeContent()throws Exception{
  byte[] text,blank;
  try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var bytes=new java.io.ByteArrayOutputStream()){
   var page=new org.apache.pdfbox.pdmodel.PDPage();pdf.addPage(page);try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf,page)){stream.beginText();stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA,12);stream.newLineAtOffset(50,700);stream.showText("Java JVM GC and Spring IoC course");stream.endText();}pdf.save(bytes);text=bytes.toByteArray();
  }
  try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var bytes=new java.io.ByteArrayOutputStream()){pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());pdf.save(bytes);blank=bytes.toByteArray();}
  var parsed=terminal(uploadBytes("text-course.pdf",text).path("id").asLong());assertEquals("DRAFT_READY",parsed.path("state").asText());assertTrue(sql.queryForObject("SELECT content FROM doc_document WHERE id=?",String.class,parsed.path("docId").asLong()).contains("Java JVM GC"));
  var rejected=terminal(uploadBytes("blank-course.pdf",blank).path("id").asLong());assertEquals("FAILED",rejected.path("state").asText());assertTrue(rejected.path("steps").get(0).path("errorMessage").asText().contains("正文提取失败"));assertTrue(rejected.path("note").isMissingNode());
 }
 @Test void acceptFailureRollsBackAllCardsAndConcurrentRetryCreatesOneSet()throws Exception{
  long id=upload("atomic.txt","事务原子性保证全部完成或回滚。整包事务测试").path("id").asLong();var ready=terminal(id);long quiz=ready.path("quizId").asLong(),doc=ready.path("docId").asLong();var draft=call("GET","/api/learning/private-quizzes/"+quiz,null,owner,200);call("POST","/api/learning/private-quizzes/"+quiz+"/confirm",Map.of("questions",draft.path("questions")),owner,200);
  JsonNode cards=ready.path("cardDrafts").deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)cards.get(1)).put("front","rollback-sentinel");var body=Map.of("cards",cards,"expectedRevision",ready.path("revision").asLong());
  sql.execute("ALTER TABLE learning_flashcard ADD CONSTRAINT pack_test_card_failure CHECK (doc_id <> "+doc+" OR front <> 'rollback-sentinel')");
  try{call("POST","/api/learning/study-packs/"+id+"/accept",body,owner,500);assertEquals(0,sql.queryForObject("SELECT COUNT(*) FROM learning_flashcard WHERE doc_id=? AND user_id=?",Integer.class,doc,owner));assertEquals("DRAFT_READY",call("GET","/api/learning/study-packs/"+id,null,owner,200).path("state").asText());}finally{sql.execute("ALTER TABLE learning_flashcard DROP CONSTRAINT pack_test_card_failure");}
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
  try{var results=new ArrayList<java.util.concurrent.Future<JsonNode>>();for(int i=0;i<2;i++)results.add(pool.submit(()->{gate.await();return call("POST","/api/learning/study-packs/"+id+"/accept",body,owner,200);}));gate.countDown();assertEquals(results.get(0).get().path("flashcardIds"),results.get(1).get().path("flashcardIds"));}finally{pool.shutdownNow();}
  assertEquals(5,sql.queryForObject("SELECT COUNT(*) FROM learning_flashcard WHERE doc_id=? AND user_id=?",Integer.class,doc,owner));assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM study_pack_event WHERE job_id=? AND kind='JOB' AND state='READY'",Integer.class,id));
 }
}
