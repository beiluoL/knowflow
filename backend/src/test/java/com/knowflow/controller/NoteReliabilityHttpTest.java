package com.knowflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 HTTP 安全链和 H2 持久化回归。 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.h2.url=jdbc:h2:mem:notes_reliability;DB_CLOSE_DELAY=-1;MODE=MySQL", "knowflow.datasource.config-file=/tmp/knowflow-notes-test-no-config.json"})
class NoteReliabilityHttpTest {
 @Autowired org.springframework.jdbc.core.JdbcTemplate sql;
 @Autowired com.knowflow.mapper.LearningPathMapper paths;
 @Autowired com.knowflow.mapper.LearningChapterMapper chapters;
 @Autowired MockMvc mvc; @Autowired JwtUtils jwt; @Autowired ObjectMapper json;
 String auth(long id) { return "Bearer " + jwt.generateToken(id,"notes-"+id,"USER"); }
 JsonNode write(String method,String path,String body,long user,int expected) throws Exception {
  var req=switch(method){case "POST"->post(path);case "PUT"->put(path);case "DELETE"->delete(path);default->get(path);};
  var result=mvc.perform(req.header("Authorization",auth(user)).contentType("application/json").content(body))
   .andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(expected)).andReturn();
  return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
 }
 @Test void persistenceConflictHistoryAndTrash() throws Exception {
  mvc.perform(get("/api/notes")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
  var n=write("POST","/api/notes","{\"title\":\"可靠笔记\",\"content\":\"原始正文\"}",99181,200);
  long id=n.path("id").asLong(); assertTrue(id>0); assertEquals(1,n.path("revision").asInt());
  assertEquals("原始正文",write("GET","/api/notes/"+id,"",99181,200).path("content").asText());
  write("PUT","/api/notes/"+id,"{\"title\":\"可靠笔记\",\"content\":\"新正文\",\"revision\":1}",99181,200);
  write("PUT","/api/notes/"+id,"{\"title\":\"旧标签页\",\"content\":\"覆盖\",\"revision\":1}",99181,409);
  assertEquals(2,write("GET","/api/notes/"+id+"/versions","",99181,200).size());
  write("POST","/api/notes/"+id+"/versions/1/restore","{\"revision\":2}",99181,200);
  assertEquals("原始正文",write("GET","/api/notes/"+id,"",99181,200).path("content").asText());
  assertEquals(3,write("GET","/api/notes/"+id+"/versions","",99181,200).size());
  write("DELETE","/api/notes/"+id+"?revision=3","",99181,200);
  write("GET","/api/notes/"+id,"",99181,404);
  assertEquals(4,write("GET","/api/notes/"+id+"/versions","",99181,200).size());
  write("POST","/api/notes/"+id+"/restore","{\"revision\":4}",99181,200);
  assertEquals(5,write("GET","/api/notes/"+id,"",99181,200).path("revision").asInt());
 }
 @Test void ownershipValidationAndIdempotentImport() throws Exception {
  String payload="{\"title\":\"导入\",\"content\":\"独立内容\",\"legacyKey\":\"note-list:77\"}";
  var n=write("POST","/api/notes/import",payload,99182,200); long id=n.path("id").asLong();
  assertEquals(id,write("POST","/api/notes/import",payload,99182,200).path("id").asLong());
  for(String suffix:new String[]{"","/versions"})write("GET","/api/notes/"+id+suffix,"",99183,404);
  write("PUT","/api/notes/"+id,"{\"title\":\"攻击\",\"revision\":1}",99183,404);
  write("DELETE","/api/notes/"+id+"?revision=1","",99183,404);
  write("POST","/api/notes/"+id+"/restore","{\"revision\":1}",99183,404);
  mvc.perform(get("/api/notes/"+id).header("Authorization","Bearer "+jwt.generateToken(99184L,"admin","ADMIN")))
   .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
  write("POST","/api/notes","{\"title\":\"非法引用\",\"chapterId\":-1}",99182,400);
  write("PUT","/api/notes/"+id,"{\"title\":\"无版本\"}",99182,400);
 }

 @Test void chapterCompatibilityAndPrivateContext() throws Exception {
  var path=new com.knowflow.entity.LearningPath();path.setTitle("隔离章节路径");path.setOwnerUserId(99185L);path.setStatus(1);paths.insert(path);
  var chapter=new com.knowflow.entity.LearningChapter();chapter.setPathId(path.getId());chapter.setTitle("隔离章节");chapters.insert(chapter);
  String body=json.writeValueAsString(java.util.Map.of("title","旧章节入口","content","上下文保留","chapterId",chapter.getId(),"pathId",path.getId()));
  var n=write("POST","/api/learning/notes",body,99185,200);assertEquals("CHAPTER",n.path("sourceType").asText());
  assertEquals(chapter.getId().longValue(),write("GET","/api/learning/notes/"+n.path("id").asLong(),"",99185,200).path("chapterId").asLong());
  write("POST","/api/notes",body,99186,403);
 }
 @Test void simultaneousUpdatesOnlyOneWins() throws Exception {
  long id=write("POST","/api/notes","{\"title\":\"并发测试\",\"content\":\"初始\"}",99187,200).path("id").asLong();
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
  try {
   java.util.List<java.util.concurrent.Future<Integer>> results=new java.util.ArrayList<>();
   for(int i=0;i<2;i++){final int ordinal=i;results.add(pool.submit(()->{gate.await();return mvc.perform(put("/api/notes/"+id).header("Authorization",auth(99187)).contentType("application/json").content("{\"title\":\"并发\",\"content\":\""+ordinal+"\",\"revision\":1}")).andReturn().getResponse().getStatus();}));}
   gate.countDown();var statuses=java.util.List.of(results.get(0).get(),results.get(1).get());assertTrue(statuses.contains(200));assertTrue(statuses.contains(409));
   assertEquals(2,write("GET","/api/notes/"+id+"/versions","",99187,200).size());
  }finally{pool.shutdownNow();}
 }
 @Test void identicalUpdateDoesNotCreateHistoryAndLegacyBaselineIsRecoverable() throws Exception {
  sql.update("INSERT INTO learning_note(user_id,title,content,revision,source_type,content_format,status,deleted,create_time,update_time) VALUES(99188,'升级前笔记','旧正文',1,'GENERAL','MARKDOWN','SAVED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
  long id=sql.queryForObject("SELECT id FROM learning_note WHERE user_id=99188",Long.class);
  write("PUT","/api/notes/"+id,"{\"title\":\"升级前笔记\",\"content\":\"新正文\",\"revision\":1}",99188,200);
  var baseline=write("GET","/api/notes/"+id+"/versions","",99188,200);assertEquals(2,baseline.size());assertEquals("旧正文",baseline.get(1).path("content").asText());
  write("PUT","/api/notes/"+id,"{\"title\":\"升级前笔记\",\"content\":\"新正文\",\"revision\":2}",99188,200);
  assertEquals(2,write("GET","/api/notes/"+id+"/versions","",99188,200).size());
 }
}
