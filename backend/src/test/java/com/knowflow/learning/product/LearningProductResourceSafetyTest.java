package com.knowflow.learning.product;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties="knowflow.datasource.h2.url=jdbc:h2:mem:learning_product_resources;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc @Import(FakeAiConfiguration.class)
class LearningProductResourceSafetyTest {
 @Autowired com.knowflow.service.LearningService learning;
 @Autowired MockMvc http;@Autowired SysUserMapper users;@Autowired JwtUtils jwt;@Autowired QuizQuestionMapper questions;@Autowired DocCategoryMapper categories;@Autowired DocDocumentMapper docs;@Autowired JdbcTemplate db;
 long user(){var u=new SysUser();u.setUsername("resource-"+UUID.randomUUID());u.setPassword("test-only");u.setRole("USER");users.insert(u);return u.getId();}
 String token(long id){return "Bearer "+jwt.generateToken(id,"resource-test","USER");}
 @Test void pendingAndRejectedQuestionCannotBeOpenedOrSubmittedAsPractice()throws Exception{
  long actor=user();for(String review:List.of("PENDING_REVIEW","REJECTED")){var q=new QuizQuestion();q.setTitle("未批准隔离题");q.setContent("HashMap线程安全");q.setQuestionType("TRUE_FALSE");q.setAnswer("false");q.setStatus(1);q.setReviewStatus(review);questions.insert(q);
   http.perform(get("/api/quiz/questions?questionId="+q.getId()).header("Authorization",token(actor))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
   http.perform(post("/api/quiz/submit").header("Authorization",token(actor)).contentType("application/json").content("{\"answers\":[{\"questionId\":"+q.getId()+",\"userAnswer\":\"false\"}]}" )).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
  }assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM quiz_answer_record WHERE user_id=?",Integer.class,actor));
 }
 @Test void privateDocumentCannotBeAttachedToAnOthersNewFlashcard()throws Exception{
  long owner=user(),other=user();var c=new DocCategory();c.setName("私有来源");c.setOwnerId(owner);c.setStatus(1);categories.insert(c);var d=new DocDocument();d.setCategoryId(c.getId());d.setTitle("不应泄漏的私有标题");d.setStatus(1);docs.insert(d);
  http.perform(post("/api/learning/my/flashcards").header("Authorization",token(other)).contentType("application/json").content("{\"front\":\"my question\",\"back\":\"my answer\",\"docId\":"+d.getId()+"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM learning_flashcard WHERE user_id=?",Integer.class,other));
 }
 @Test void updatingAnOwnedCardCannotAttachOthersPrivateSource()throws Exception{
  long owner=user(),actor=user();var c=new DocCategory();c.setName("私有编辑来源");c.setOwnerId(owner);c.setStatus(1);categories.insert(c);var d=new DocDocument();d.setCategoryId(c.getId());d.setTitle("私有文档");d.setStatus(1);docs.insert(d);
  var input=new com.knowflow.dto.FlashcardSaveDTO();input.setFront("my front");input.setBack("my back");long id=learning.createMyFlashcard(actor,input).getId();
  http.perform(put("/api/learning/my/flashcards/"+id).header("Authorization",token(actor)).contentType("application/json").content("{\"docId\":"+d.getId()+"}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
  assertNull(db.queryForObject("SELECT doc_id FROM learning_flashcard WHERE id=?",Long.class,id));
 }
}
