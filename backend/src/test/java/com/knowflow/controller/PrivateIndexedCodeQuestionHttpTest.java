package com.knowflow.controller;

import com.knowflow.entity.CodeQuestion;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.CodeQuestionMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.type=h2","knowflow.datasource.config-file=/tmp/knowflow-private-code-test-missing-config.json","knowflow.datasource.h2.url=jdbc:h2:mem:private_indexed_code;DB_CLOSE_DELAY=-1;MODE=MySQL"})
class PrivateIndexedCodeQuestionHttpTest {
 @Autowired MockMvc http; @Autowired JwtUtils jwt; @Autowired JdbcTemplate sql;
 @Autowired CodeQuestionMapper questions; @Autowired SysUserMapper users;
 Long owner,other,admin,privateId,sharedId,legacyId; String keyword;
 @BeforeEach void fixture(){owner=user("USER");other=user("USER");admin=user("ADMIN");keyword="private-code-"+UUID.randomUUID();privateId=question();sharedId=question();legacyId=question();index(privateId,"user/"+owner+"/any-package/program");index(sharedId,"shared/any-package/program");}
 Long user(String role){SysUser u=new SysUser();u.setUsername("private-code-"+UUID.randomUUID());u.setPassword("unused");u.setRole(role);users.insert(u);return u.getId();}
 Long question(){CodeQuestion q=new CodeQuestion();q.setTitle(keyword);q.setDescription("private teaching source");q.setLanguage("java");q.setStatus(1);q.setSubmitCount(0);q.setPassCount(0);questions.insert(q);return q.getId();}
 void index(Long id,String key){sql.update("INSERT INTO interview_seed_resource(seed_key,resource_key,resource_type,resource_id) VALUES(?,?,?,?)","arbitrary-seed-"+UUID.randomUUID(),key,"CODE",id);}
 String auth(Long id){return "Bearer "+jwt.generateToken(id,"fixture","USER");}
 @Test void otherUserCannotDiscoverPrivateCode() throws Exception {
  String body=http.perform(get("/api/code-questions").param("keyword",keyword).header("Authorization",auth(other))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  com.fasterxml.jackson.databind.JsonNode rows=new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("data");assertEquals(2,rows.size());for(var row:rows)assertNotEquals(privateId.longValue(),row.path("id").asLong());
 }
 @Test void otherUserCannotReadPrivateCode() throws Exception {
  http.perform(get("/api/code-questions/"+privateId).header("Authorization",auth(other))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
 }
 @Test void otherUserCannotSubmitPrivateCodeOrChangeStatistics() throws Exception {
  http.perform(post("/api/code-questions/"+privateId+"/submit").header("Authorization",auth(other)).contentType("application/json").content("{\"total\":1,\"passCount\":1,\"code\":\"secret\"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  assertEquals(0,questions.selectById(privateId).getSubmitCount());assertEquals(0,questions.selectById(privateId).getPassCount());assertEquals(0,sql.queryForObject("SELECT COUNT(*) FROM code_submit_record WHERE question_id=?",Integer.class,privateId));assertEquals(0,sql.queryForObject("SELECT COUNT(*) FROM learning_event WHERE resource_id=? AND user_id=?",Integer.class,privateId,other));
 }
 @Test void ownerAndDatabaseAdminCanReadAndSubmit() throws Exception {
  for(Long actor:new Long[]{owner,admin}){
   http.perform(get("/api/code-questions").param("keyword",keyword).header("Authorization",auth(actor))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3));
   http.perform(get("/api/code-questions/"+privateId).header("Authorization",auth(actor))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
   http.perform(post("/api/code-questions/"+privateId+"/submit").header("Authorization",auth(actor)).contentType("application/json").content("{\"total\":1,\"passCount\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
  }assertEquals(2,questions.selectById(privateId).getSubmitCount());
 }
 @Test void anonymousCannotAccessCodeQuestions() throws Exception {
  http.perform(get("/api/code-questions")).andExpect(status().isUnauthorized());
  http.perform(get("/api/code-questions/"+privateId)).andExpect(status().isUnauthorized());
  http.perform(post("/api/code-questions/"+privateId+"/submit").contentType("application/json").content("{\"total\":1,\"passCount\":1}")).andExpect(status().isUnauthorized());
 }
 @Test void otherUserCannotAccessPrivateSubmissionEndpoint() throws Exception {
  http.perform(get("/api/code-questions/"+privateId+"/submissions").header("Authorization",auth(other))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
 }
 @Test void sharedAndUnindexedLegacyQuestionsRemainPublicToAuthenticatedUsers() throws Exception {
  for(Long id:new Long[]{sharedId,legacyId}){
   http.perform(get("/api/code-questions/"+id).header("Authorization",auth(other))).andExpect(status().isOk());
   http.perform(post("/api/code-questions/"+id+"/submit").header("Authorization",auth(other)).contentType("application/json").content("{\"total\":1,\"passCount\":1}")).andExpect(status().isOk());
  }
 }
}
