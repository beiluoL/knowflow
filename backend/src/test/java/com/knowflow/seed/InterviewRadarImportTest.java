package com.knowflow.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.exception.BusinessException;
import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties="knowflow.datasource.h2.url=jdbc:h2:mem:radar_import_v1;DB_CLOSE_DELAY=-1;MODE=MySQL")
class InterviewRadarImportTest {
 @Autowired InterviewRadarImportService service;
 @Autowired SysUserMapper users;
 @Autowired JdbcTemplate jdbc;
 @Autowired ObjectMapper json;
 @Autowired com.knowflow.service.LearningService learning;
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 @Autowired com.knowflow.utils.JwtUtils jwt;
 private SysUser user(){var u=new SysUser();u.setUsername("radar-"+UUID.randomUUID());u.setPassword("fixture-only");u.setRole("USER");users.insert(u);return u;}
 private com.fasterxml.jackson.databind.JsonNode catalog() throws Exception {
  try(var stream=new org.springframework.core.io.ClassPathResource("fixtures/radar-import-catalog.json").getInputStream()){return json.readTree(stream);}
 }
 @Test @Transactional void actualSourceImportIsPrivateIdempotentAndNeverCreatesScores() throws Exception {
  var owner=user();var other=user();var input=catalog();
  long before=jdbc.queryForObject("SELECT COUNT(*) FROM learning_user_chapter",Long.class);
  var preview=service.preview(owner.getId(),input);
  assertEquals(input.path("mergedQuestions").size(),preview.get("chapters"));
  var result=service.apply(owner.getId(),input);
  long path=((Number)result.get("pathId")).longValue();
  assertEquals(owner.getId(),jdbc.queryForObject("SELECT owner_user_id FROM learning_path WHERE id=?",Long.class,path));
  assertEquals(input.path("mergedQuestions").size(),jdbc.queryForObject("SELECT COUNT(*) FROM learning_chapter WHERE path_id=? AND deleted=0",Integer.class,path));
  assertTrue(learning.getPathList(owner.getId()).stream().anyMatch(p->p.getId().equals(path)));
  assertFalse(learning.getPathList(other.getId()).stream().anyMatch(p->p.getId().equals(path)));
  assertThrows(BusinessException.class,()->learning.getChapterList(path,other.getId()));
  long docs=jdbc.queryForObject("SELECT COUNT(*) FROM doc_document",Long.class);
  service.apply(owner.getId(),input);
  assertEquals(docs,jdbc.queryForObject("SELECT COUNT(*) FROM doc_document",Long.class));
  assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM learning_user_chapter",Long.class));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM quiz_question WHERE creator_id=? AND status=1",Integer.class,owner.getId()));
  var otherResult=service.apply(other.getId(),input);
  assertNotEquals(path,((Number)otherResult.get("pathId")).longValue());
  long doc=jdbc.queryForObject("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key LIKE '%/document/%' LIMIT 1",Long.class,InterviewRadarImportService.KEY+owner.getId());
  jdbc.update("UPDATE doc_document SET content='本人手工修改' WHERE id=?",doc);
  assertThrows(BusinessException.class,()->service.apply(owner.getId(),input));
 }
 @Test @Transactional void httpBoundaryRejectsAnonymousAndUsesAuthenticatedOwner() throws Exception {
  var actor=user();var input=catalog();String token="Bearer "+jwt.generateToken(actor.getId(),actor.getUsername(),"USER");
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/learning/interview-radar/import/preview").contentType("application/json").content(input.toString()))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/learning/interview-radar/import/preview").header("Authorization",token).contentType("application/json").content(input.toString()))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(200))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.chapters").value(input.path("mergedQuestions").size()));
 }
 @Test void anonymousCannotPreviewOrImport() throws Exception {
  var input=catalog();
  assertThrows(BusinessException.class,()->service.preview(null,input));
  assertThrows(BusinessException.class,()->service.apply(null,input));
 }
}
