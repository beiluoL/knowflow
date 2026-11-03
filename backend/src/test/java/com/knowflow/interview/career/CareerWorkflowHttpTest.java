package com.knowflow.interview.career;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** 真实鉴权/版本/匹配/方案/V2链路；模型替身关闭，不调用付费模型。 */
@SpringBootTest(properties={"knowflow.datasource.h2.url=jdbc:h2:mem:career_workflow;DB_CLOSE_DELAY=-1;MODE=MySQL","knowflow.datasource.config-file=/tmp/career-no-overrides.json","knowflow.seed.development-account.enabled=false","knowflow.datasource.allow-runtime-switch=false"})
@AutoConfigureMockMvc @ActiveProfiles("local")
class CareerWorkflowHttpTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JwtUtils jwt;@Autowired JdbcTemplate jdbc;
 @MockBean com.knowflow.service.impl.AiServiceImpl ai;
 String token(long id){return "Bearer "+jwt.generateToken(id,"career-fixture",id==1?"ADMIN":"USER");}
 JsonNode post(String path,Object body,long user,int expected)throws Exception{
  var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Authorization",token(user)).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(expected)).andReturn();
  return json.readTree(response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
 }
 @Test void confirmedVersionsMatchAndStartOriginalInterviewWithoutInventingExperience()throws Exception{
  String root="/api/interview/career";
  var resume=post(root+"/records/RESUME",Map.of("title","测试简历","source","曾完成 Java 多线程练习","sections",Map.of("skills",List.of(Map.of("value","Java 多线程","quote","Java 多线程练习")))),1,200);
  long rid=resume.path("id").asLong();assertTrue(rid>0);
  var job=post(root+"/records/JOB",Map.of("title","Java 并发岗位","source","必须 Java 并发","sections",Map.of("requiredSkills",List.of(Map.of("value","Java 并发","quote","必须 Java 并发")))),1,200);
  long jid=job.path("id").asLong();
  post(root+"/matches",Map.of("resumeId",rid,"resumeVersion",1,"jobId",jid,"jobVersion",1,"requestId",UUID.randomUUID().toString()),1,409);
  post(root+"/records/"+rid+"/confirm",Map.of("version",1),1,200);post(root+"/records/"+jid+"/confirm",Map.of("version",1),1,200);
  var match=post(root+"/matches",Map.of("resumeId",rid,"resumeVersion",1,"jobId",jid,"jobVersion",1,"requestId",UUID.randomUUID().toString()),1,200);
  assertEquals(100,match.path("score").asInt());
  var plan=post(root+"/plans",Map.of("matchId",match.path("id").asLong(),"projectCount",0,"coreCount",2,"gapCount",0,"designCount",1,"durationMinutes",25,"difficulty","HARD","requestId",UUID.randomUUID().toString()),1,200);
  assertEquals(3,plan.path("questions").size());assertNotEquals(plan.path("questions").get(0).path("title"),plan.path("questions").get(1).path("title"));
  var session=post(root+"/plans/"+plan.path("id").asLong()+"/start",Map.of("requestId",UUID.randomUUID().toString()),1,200);
  assertEquals("ASKING",session.path("state").asText());assertEquals(3,session.path("questionCount").asInt());assertEquals("HARD",session.path("difficulty").asText());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM interview_career_session WHERE user_id=1 AND plan_id=?",Integer.class,plan.path("id").asLong()));
  mvc.perform(get(root+"/records/"+rid).header("Authorization",token(2))).andExpect(status().isNotFound());
  mvc.perform(get(root+"/records/"+rid)).andExpect(status().isUnauthorized());
 }
 @Test void uploadIsPrivateAndDoesNotTrustExtension()throws Exception{
  String root="/api/interview/career";
  var file=new MockMultipartFile("file","../../resume.txt","application/octet-stream","Java 多线程练习，测试简历。".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  var response=mvc.perform(multipart(root+"/records/RESUME/upload").file(file).header("Authorization",token(1))).andExpect(status().isOk()).andReturn();
  long id=json.readTree(response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data").path("id").asLong();
  mvc.perform(get(root+"/records/"+id+"/file").header("Authorization",token(2))).andExpect(status().isNotFound());
  mvc.perform(get(root+"/records/"+id+"/file").header("Authorization",token(1))).andExpect(status().isOk());
  var invalid=new MockMultipartFile("file","resume.pdf","application/pdf",new byte[]{0,1,2,3});
  mvc.perform(multipart(root+"/records/RESUME/upload").file(invalid).header("Authorization",token(1))).andExpect(status().isBadRequest());
 }
 @Test void versionsAreImmutableAndAiCandidatesRequireConsentAndConfirmation()throws Exception{
  String root="/api/interview/career";
  var record=post(root+"/records/RESUME",Map.of("title","原始标题","source","学习 Java","sections",Map.of()),1,200);long id=record.path("id").asLong();
  post(root+"/records/"+id+"/parse",Map.of("version",1,"allowModelProcessing",false),1,400);
  org.mockito.Mockito.verify(ai,org.mockito.Mockito.never()).completeStructured(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.anyLong());
  org.mockito.Mockito.when(ai.isConfigured(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
  org.mockito.Mockito.when(ai.completeStructured(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.anyLong())).thenReturn("{\"skills\":[{\"value\":\"Java\",\"quote\":\"不存在的原文\"}]}");
  post(root+"/records/"+id+"/parse",Map.of("version",1,"allowModelProcessing",true),1,400);
  assertEquals(1,jdbc.queryForObject("SELECT current_version FROM interview_career_record WHERE id=?",Integer.class,id));
  org.mockito.Mockito.when(ai.completeStructured(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.anyLong())).thenReturn("{\"skills\":[{\"value\":\"Java\",\"quote\":\"Java\"}]}");
  var candidate=post(root+"/records/"+id+"/parse",Map.of("version",1,"allowModelProcessing",true),1,200);assertEquals(2,candidate.path("version").asInt());assertFalse(candidate.path("confirmed").asBoolean());
  mvc.perform(get(root+"/records/"+id+"?version=1").header("Authorization",token(1))).andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("原始标题")).andExpect(jsonPath("$.data.version").value(1));
  mvc.perform(put(root+"/records/"+id).header("Authorization",token(1)).contentType("application/json").content(json.writeValueAsString(Map.of("expectedVersion",1,"title","过期编辑","source","学习 Java","sections",Map.of())))).andExpect(status().isConflict());
  mvc.perform(delete(root+"/records/"+id).header("Authorization",token(1))).andExpect(status().isOk());
  mvc.perform(get(root+"/records/"+id).header("Authorization",token(1))).andExpect(status().isNotFound());
 }
}
