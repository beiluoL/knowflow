package com.knowflow.interview.career;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 验证简历草稿的版本冲突、自动保存与用户隔离。
 */
@SpringBootTest(properties={"knowflow.datasource.h2.url=jdbc:h2:mem:resume_draft_v4;DB_CLOSE_DELAY=-1;MODE=MySQL", "knowflow.datasource.config-file=/tmp/resume-draft-v4-none.json", "knowflow.seed.development-account.enabled=false"})
@AutoConfigureMockMvc @ActiveProfiles("local")
class ResumeDraftHttpTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired JwtUtils jwt;
 private String token(long user){return "Bearer "+jwt.generateToken(user,"resume-draft","USER");}
 @Test void draftHasRevisionConflictAndUserIsolation() throws Exception {
  String path="/api/interview/career/studio/resumes/0/draft";
  var body=Map.of("revision",0,"baseVersion",0,"payload",Map.of("title","私人草稿","source","项目原文","sections",Map.of()));
  mvc.perform(put(path).header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.revision").value(1));
  mvc.perform(put(path).header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
  mvc.perform(get(path).header("Authorization",token(3))).andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(0)).andExpect(jsonPath("$.data.payload").doesNotExist());
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
  mvc.perform(delete(path+"?revision=1").header("Authorization",token(2)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(2)).andExpect(jsonPath("$.data.payload").doesNotExist());
  mvc.perform(put(path).header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("revision",1,"baseVersion",0,"payload",body.get("payload")))))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
 }
 @Test void autosaveDoesNotCreateVersionAndCannotReadOtherUsersResume() throws Exception {
  var edit=Map.of("title","Java AI 合成测试简历","source","使用Java开发知识库","sections",Map.of("projects",java.util.List.of(Map.of("value","使用Java开发知识库","quote","使用Java开发知识库"))));
  var response=mvc.perform(post("/api/interview/career/records/RESUME").header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit)))
   .andExpect(status().isOk()).andReturn();
  long id=json.readTree(response.getResponse().getContentAsString()).path("data").path("id").asLong();
  String path="/api/interview/career/studio/resumes/"+id+"/draft";
  mvc.perform(put(path).header("Authorization",token(2)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("revision",0,"baseVersion",1,"payload",edit))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(1));
  mvc.perform(get("/api/interview/career/records/"+id+"/versions").header("Authorization",token(2)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
  mvc.perform(get(path).header("Authorization",token(3))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
  mvc.perform(delete(path+"?revision=0").header("Authorization",token(2))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
  mvc.perform(get(path).header("Authorization",token(2))).andExpect(jsonPath("$.data.revision").value(1));
 }
}
