package com.knowflow.seed;

import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.h2.url=jdbc:h2:mem:phase3a-qa-test;DB_CLOSE_DELAY=-1;MODE=MySQL", "knowflow.content-studio.qa.enabled=true", "server.address=127.0.0.1", "knowflow.datasource.allow-runtime-switch=false"})
class ContentStudioThreadPoolIntegrationTest {
 @org.springframework.test.context.DynamicPropertySource
 static void syntheticBundle(org.springframework.test.context.DynamicPropertyRegistry registry) {
  registry.add("knowflow.content-studio.qa.package", () -> {
   try { return new org.springframework.core.io.ClassPathResource("fixtures/content-studio/import/threadpool-qa.json").getFile().getParentFile().getParent(); }
   catch(java.io.IOException e) { throw new IllegalStateException(e); }
  });
 }

 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
 @Autowired SysUserMapper users; @Autowired JwtUtils jwt; @Autowired InterviewSeedService seed;
 String root="/api/learning/content-studio/qa/threadpool";
 SysUser user(String role){var u=new SysUser();u.setUsername("studio-"+UUID.randomUUID());u.setPassword("test-only");u.setNickname("Isolated QA");u.setRole(role);users.insert(u);return u;}
 String auth(SysUser u){return "Bearer "+jwt.generateToken(u.getId(),u.getUsername(),u.getRole());}
 Map<String,String> history(){var o=new LinkedHashMap<String,String>();for(String t:List.of("learning_event","knowledge_mastery","quiz_answer_record","learning_mistake","learning_user_chapter","learning_note","code_submit_record","wb_review_log")){var rows=new ArrayList<List<String>>();for(var row:jdbc.queryForList("SELECT * FROM "+t+" ORDER BY 1")){var values=new ArrayList<String>();for(var x:row.values()){try{values.add(x instanceof java.sql.Clob c?c.getSubString(1,(int)c.length()):Objects.toString(x,null));}catch(java.sql.SQLException e){throw new IllegalStateException(e);}}rows.add(values);}try{o.put(t,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(rows))));}catch(Exception e){throw new IllegalStateException(e);}}return o;}

 com.fasterxml.jackson.databind.JsonNode plan(SysUser u)throws Exception{return json.readTree(mvc.perform(get(root).header("Authorization",auth(u))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString()).get("data");}
 void apply(SysUser u,String hash)throws Exception{mvc.perform(post(root).header("Authorization",auth(u)).contentType("application/json").content(json.writeValueAsString(Map.of("expectedPlanHash",hash)))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));}
 @Test @org.springframework.transaction.annotation.Transactional void dryRunImportRepeatAndRollbackPreserveHistory()throws Exception{
  var u=user("USER");seed.activate(u.getId());var before=history();long n=jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class);
  var p=plan(u);assertTrue(p.get("dryRun").asBoolean());assertTrue(p.get("conflicts").isEmpty());assertEquals(n,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class));
  apply(u,p.get("planHash").asText());long added=jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class);apply(u,plan(u).get("planHash").asText());assertEquals(added,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class));assertEquals(before,history());
  long ch=p.get("chapterId").asLong();mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+ch).header("Authorization",auth(u))).andExpect(jsonPath("$.data.revision.version").value("java-ai-interview-v2/threadpool/2.0.3-qa")).andExpect(jsonPath("$.data.revision.questions.length()").value(30)).andExpect(jsonPath("$.data.revision.exercises.length()").value(4)).andExpect(jsonPath("$.data.revision.knowledge[0].id").isNumber()).andExpect(jsonPath("$.data.revision.knowledge[0].name").isString());
  mvc.perform(post(root+"/rollback").header("Authorization",auth(u))).andExpect(status().isOk());mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+ch).header("Authorization",auth(u))).andExpect(jsonPath("$.data.revision").doesNotExist());assertEquals(added,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class));assertEquals(before,history());
  apply(u,plan(u).get("planHash").asText());assertEquals(added,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class));
 }
 @Test @org.springframework.transaction.annotation.Transactional void stalePlanAndUserEditsAreConflictsRatherThanOverwrites()throws Exception{
  var u=user("USER");seed.activate(u.getId());var p=plan(u);mvc.perform(post(root).header("Authorization",auth(u)).contentType("application/json").content("{\"expectedPlanHash\":\"stale\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
  apply(u,p.get("planHash").asText());long doc=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key='shared/document'",Long.class,"java-ai-interview-v2/threadpool/2.0.3-qa");jdbc.update("UPDATE doc_document SET content='user edit' WHERE id=?",doc);
  var edited=plan(u);assertFalse(edited.get("conflicts").isEmpty());mvc.perform(post(root).header("Authorization",auth(u)).contentType("application/json").content(json.writeValueAsString(Map.of("expectedPlanHash",edited.get("planHash").asText())))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));assertEquals("user edit",jdbc.queryForObject("SELECT content FROM doc_document WHERE id=?",String.class,doc));
 }
 @Test @org.springframework.transaction.annotation.Transactional void anonymousAdminAndOtherUserCannotChooseOwnersRevision()throws Exception{
  mvc.perform(get(root)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));mvc.perform(get(root).header("Authorization",auth(user("ADMIN")))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  var u=user("USER");seed.activate(u.getId());var p=plan(u);apply(u,p.get("planHash").asText());var other=user("USER");mvc.perform(get("/api/learning/seed/java-ai-v1/chapters/"+p.get("chapterId").asLong()).header("Authorization",auth(other))).andExpect(jsonPath("$.data.revision").doesNotExist());
 }
 @Test @org.springframework.transaction.annotation.Transactional void brokenKnowledgeLinkStopsRepeatImport()throws Exception{
  var u=user("USER");seed.activate(u.getId());apply(u,plan(u).get("planHash").asText());long doc=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key='shared/document'",Long.class,"java-ai-interview-v2/threadpool/2.0.3-qa");jdbc.update("UPDATE resource_knowledge_mapping SET deleted=1 WHERE resource_type='DOC' AND resource_id=?",doc);assertFalse(plan(u).get("conflicts").isEmpty());
 }
 @Test @org.springframework.transaction.annotation.Transactional void secondUserGetsSeparatePrivateCardsAndEditedCardsAreProtected()throws Exception{
  var u=user("USER");var v=user("USER");seed.activate(u.getId());seed.activate(v.getId());apply(u,plan(u).get("planHash").asText());var p=plan(v);assertTrue(p.get("conflicts").isEmpty());apply(v,p.get("planHash").asText());
  long count=jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard WHERE tags=? AND user_id=?",Long.class,"java-ai-interview-v2/threadpool/2.0.3-qa",v.getId());assertEquals(8,count);
  jdbc.update("UPDATE learning_flashcard SET front='my card',review_count=9 WHERE tags=? AND user_id=?","java-ai-interview-v2/threadpool/2.0.3-qa",v.getId());assertFalse(plan(v).get("conflicts").isEmpty());
  mvc.perform(post(root+"/rollback").header("Authorization",auth(v))).andExpect(status().isOk());assertEquals(9,jdbc.queryForObject("SELECT MAX(review_count) FROM learning_flashcard WHERE tags=? AND user_id=?",Integer.class,"java-ai-interview-v2/threadpool/2.0.3-qa",v.getId()));assertTrue(plan(u).get("conflicts").isEmpty());
 }

 public static class RejectQuizInsert implements org.h2.api.Trigger {
  @Override public void fire(java.sql.Connection conn,Object[] oldRow,Object[] newRow)throws java.sql.SQLException {throw new java.sql.SQLException("deliberate isolated midimport failure");}
 }
 @Test void committedHttpImportFailureRollsBackDocumentAndLedger()throws Exception{
  var u=user("USER");seed.activate(u.getId());var before=history();long docs=jdbc.queryForObject("SELECT COUNT(*) FROM doc_document",Long.class),index=jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class);var p=plan(u);
  jdbc.execute("CREATE TRIGGER studio_midimport_failure BEFORE INSERT ON quiz_question FOR EACH ROW CALL 'com.knowflow.seed.ContentStudioThreadPoolIntegrationTest$RejectQuizInsert'");
  try{mvc.perform(post(root).header("Authorization",auth(u)).contentType("application/json").content(json.writeValueAsString(Map.of("expectedPlanHash",p.get("planHash").asText())))).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(500));assertEquals(docs,jdbc.queryForObject("SELECT COUNT(*) FROM doc_document",Long.class));assertEquals(index,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource",Long.class));assertEquals(before,history());}
  finally{jdbc.execute("DROP TRIGGER studio_midimport_failure");}
 }

}
