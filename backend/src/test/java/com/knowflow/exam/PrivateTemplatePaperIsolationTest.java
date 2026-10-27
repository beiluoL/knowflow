package com.knowflow.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.exam.entity.ExamTemplate;
import com.knowflow.exam.mapper.ExamTemplateMapper;
import com.knowflow.exam.mapper.ExamPaperMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.type=h2","knowflow.datasource.config-file=/tmp/knowflow-template-private-test-missing-config.json","knowflow.datasource.h2.url=jdbc:h2:mem:private_template_paper;DB_CLOSE_DELAY=-1;MODE=MySQL"})
class PrivateTemplatePaperIsolationTest {
 @Autowired MockMvc http; @Autowired JwtUtils jwt; @Autowired ObjectMapper json;
 @Autowired SysUserMapper users; @Autowired DocCategoryMapper categories; @Autowired DocDocumentMapper docs; @Autowired QuizQuestionMapper questions; @Autowired ExamTemplateMapper templates; @Autowired ExamPaperMapper papers;
 Long owner,other;
 @BeforeEach void setup(){owner=user();other=user();}
 Long user(){SysUser u=new SysUser();u.setUsername("tpl-"+UUID.randomUUID());u.setPassword("unused");u.setRole("USER");users.insert(u);return u.getId();}
 String auth(Long actor){return "Bearer "+jwt.generateToken(actor,"fixture","USER");}
 Long compose(boolean privateCategory,boolean privateDoc) throws Exception {
  String topic="TP_"+UUID.randomUUID();
  DocCategory c=new DocCategory();c.setName(topic);c.setParentId(0L);c.setOwnerId(privateCategory?owner:null);c.setStatus(1);categories.insert(c);
  Long docId=null;
  if(privateDoc){DocCategory dcat=new DocCategory();dcat.setName(topic+"doc");dcat.setParentId(0L);dcat.setOwnerId(owner);dcat.setStatus(1);categories.insert(dcat);DocDocument d=new DocDocument();d.setTitle(topic);d.setCategoryId(dcat.getId());d.setContent("private source");d.setStatus(1);docs.insert(d);docId=d.getId();}
  QuizQuestion q=new QuizQuestion();q.setTitle(topic);q.setContent("private exam prompt");q.setQuestionType("SHORT_ANSWER");q.setAnswer("answer");q.setDifficulty(2);q.setCategoryId(c.getId());q.setDocId(docId);q.setTopicCode(topic);q.setStatus(1);q.setReviewStatus("APPROVED");questions.insert(q);
  ExamTemplate t=new ExamTemplate();t.setTemplateKey(topic);t.setName(topic);t.setDomain("JAVA");t.setTopics(topic);t.setSpec(json.writeValueAsString(java.util.Map.of("topics",java.util.List.of(topic),"typeDistribution",java.util.Map.of("SHORT_ANSWER",1),"strategy","BALANCED")));t.setDurationMinutes(20);t.setTotalScore(100);t.setPassScore(60);t.setMode("PRACTICE");t.setStatus(1);t.setVersion("1");templates.insert(t);
  String response=http.perform(post("/api/exam/templates/"+topic+"/papers").header("Authorization",auth(owner)).contentType("application/json").content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString();return json.readTree(response).path("data").path("id").asLong();
 }
 void assertPrivate(Long id) throws Exception {
  http.perform(post("/api/exam/papers/"+id+"/attempts").header("Authorization",auth(other))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  assertEquals(owner,papers.selectById(id).getOwnerUserId());
  var rows=json.readTree(http.perform(get("/api/exam/papers").header("Authorization",auth(other))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");for(var row:rows)assertNotEquals(id.longValue(),row.path("id").asLong());
  http.perform(post("/api/exam/papers/"+id+"/attempts").header("Authorization",auth(other))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  http.perform(post("/api/exam/papers/"+id+"/attempts").header("Authorization",auth(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
 }
 @Test void privateCategorySnapshotStaysOwned() throws Exception {assertPrivate(compose(true,false));}
 @Test void privateDocumentSnapshotWithPublicCategoryStaysOwned() throws Exception {assertPrivate(compose(false,true));}
 @Test void entirelyPublicTemplatePaperRemainsPublic() throws Exception {Long id=compose(false,false);assertNull(papers.selectById(id).getOwnerUserId());http.perform(post("/api/exam/papers/"+id+"/attempts").header("Authorization",auth(other))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));}
}
