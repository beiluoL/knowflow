package com.knowflow.flashcards;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.h2.url=jdbc:h2:mem:flashcard_exchange_4g;DB_CLOSE_DELAY=-1;MODE=MySQL", "knowflow.datasource.config-file=/tmp/knowflow-phase4g-exchange-no-config.json"})
class FlashcardExchangeHttpTest {
 @Autowired MockMvc mvc; @Autowired JwtUtils jwt; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
 String auth(long uid){return "Bearer "+jwt.generateToken(uid,"exchange"+uid,"USER");}
 Map<String,Object> input(String content){return new LinkedHashMap<>(Map.of("format","csv","content",content,"source","test-library","mapping",Map.of()));}
 JsonNode postData(String endpoint,long uid,Object body,int expected) throws Exception {
  var response=mvc.perform(post("/api/learning/my/flashcard-exchange/"+endpoint).header("Authorization",auth(uid)).contentType("application/json").content(json.writeValueAsBytes(body)))
    .andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(expected)).andReturn().getResponse();
  return json.readTree(response.getContentAsString()).get("data");
 }
 @Test void previewDoesNotWriteAndConfirmationIsIdempotentAndOwnerBound() throws Exception {
  long uid=912001;var request=input("type,front,back,externalId,tags\nBASIC,\"Question, one\",\"Answer\ncontinued\",n1,Java\nCLOZE,{{c1::a}} and {{c2::b}},,n2,cloze");
  int before=jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard WHERE user_id=?",Integer.class,uid);
  var preview=postData("preview",uid,request,200);assertEquals(2,preview.path("counts").path("NEW").asInt());assertEquals(3,preview.path("cardCount").asInt());
  assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard WHERE user_id=?",Integer.class,uid));
  var confirm=new LinkedHashMap<String,Object>(Map.of("request",request,"token",preview.get("token").asText(),"skipProblems",false));
  postData("confirm",uid+1,confirm,409);
  var result=postData("confirm",uid,confirm,200);assertEquals(2,result.path("insertedNotes").asInt());assertEquals(3,result.path("insertedCards").asInt());
  assertEquals(result,postData("confirm",uid,confirm,200));
  var again=postData("preview",uid,request,200);assertEquals(2,again.path("counts").path("DUPLICATE").asInt());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=?",Integer.class,uid));
  assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard WHERE user_id=?",Integer.class,uid));
 }

 @Test void originalCardAndFsrsRemainByteForByteAndOwnExportDoesNotDuplicate() throws Exception {
  long uid=912011;
  var response=mvc.perform(post("/api/learning/my/flashcard-notes").header("Authorization",auth(uid)).contentType("application/json").content("{\"front\":\"=a\",\"back\":\"answer\"}" )).andExpect(status().isOk()).andReturn();
  var n=json.readTree(response.getResponse().getContentAsString()).path("data");long card=n.path("cards").get(0).path("id").asLong();
  jdbc.update("UPDATE learning_flashcard SET next_review_time='2032-01-02 03:04:05',fsrs_state='preserved-memory',fsrs_revision=7,review_count=4 WHERE id=?",card);
  var before=jdbc.queryForMap("SELECT * FROM learning_flashcard WHERE id=?",card);
  var export=exportData(uid,"csv");assertTrue(export.path("content").asText().contains("'=a"));
  var request=input(export.path("content").asText());
  var preview=postData("preview",uid,request,200);assertEquals(1,preview.path("counts").path("DUPLICATE").asInt());
  assertEquals(before,jdbc.queryForMap("SELECT * FROM learning_flashcard WHERE id=?",card));
  var other=postData("preview",uid+1,request,200);assertEquals(1,other.path("counts").path("NEW").asInt());
  var imported=postData("confirm",uid+1,Map.of("request",request,"token",other.path("token").asText()),200);
  var otherCard=jdbc.queryForMap("SELECT * FROM learning_flashcard WHERE user_id=?",uid+1);assertEquals("=a",otherCard.get("front"));assertNull(otherCard.get("fsrs_state"));assertEquals(0L,((Number)otherCard.get("fsrs_revision")).longValue());
  assertEquals(1,imported.path("insertedCards").asInt());
 }
 JsonNode exportData(long uid,String format) throws Exception {return json.readTree(mvc.perform(get("/api/learning/my/flashcard-exchange/export").param("format",format).header("Authorization",auth(uid))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString()).path("data");}
 @Test void conflictInvalidSkipAndFullRollback() throws Exception {
  long uid=912021;var request=input("front,back,externalId\na,answer,n1\na,DIFFERENT,n1\n<script>x</script>,b,n2\nx,b,n3");
  var p=postData("preview",uid,request,200);assertEquals(2,p.path("counts").path("NEW").asInt());assertEquals(1,p.path("counts").path("CONFLICT").asInt());assertEquals(1,p.path("counts").path("INVALID").asInt());
  postData("confirm",uid,Map.of("request",request,"token",p.path("token").asText()),409);
  jdbc.execute("ALTER TABLE learning_flashcard ADD CONSTRAINT phase4g_failure CHECK (front <> 'x')");
  try {postData("confirm",uid,Map.of("request",request,"token",p.path("token").asText(),"skipProblems",true),500);}
  finally {jdbc.execute("ALTER TABLE learning_flashcard DROP CONSTRAINT phase4g_failure");}
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard WHERE user_id=?",Integer.class,uid));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard_note WHERE user_id=?",Integer.class,uid));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard_import_source WHERE user_id=?",Integer.class,uid));
  assertEquals(2,postData("confirm",uid,Map.of("request",request,"token",p.path("token").asText(),"skipProblems",true),200).path("insertedNotes").asInt());
 }
 @Test void editsAndLogicalDeletionBecomeConflictsRatherThanOverwrites() throws Exception {
  long uid=912031;var request=input("front,back,externalId\na,b,id1");var p=postData("preview",uid,request,200);
  var result=postData("confirm",uid,Map.of("request",request,"token",p.path("token").asText()),200);long id=result.path("noteIds").get(0).asLong();
  mvc.perform(put("/api/learning/my/flashcard-notes/"+id).header("Authorization",auth(uid)).contentType("application/json").content("{\"front\":\"edited\",\"back\":\"b\",\"revision\":1}")).andExpect(status().isOk());
  assertEquals(1,postData("preview",uid,request,200).path("counts").path("CONFLICT").asInt());
  jdbc.update("UPDATE learning_flashcard SET deleted=1 WHERE user_id=?",uid);
  assertEquals(1,postData("preview",uid,request,200).path("counts").path("CONFLICT").asInt());
 }
 @Test void mappingsSourceAndWholeFingerprintMatter() throws Exception {
  long uid=912041;var request=input("Q,A,id\nsame,one,x\nsame,two,y");request.put("mapping",Map.of("front",0,"back",1,"externalId",2));
  var p=postData("preview",uid,request,200);assertEquals(2,p.path("counts").path("NEW").asInt());
  postData("confirm",uid,Map.of("request",request,"token",p.path("token").asText()),200);
  request.put("content","Q,A,id\nsame,one,x\nsame,CHANGED,y");p=postData("preview",uid,request,200);assertEquals(1,p.path("counts").path("DUPLICATE").asInt());assertEquals(1,p.path("counts").path("CONFLICT").asInt());
  request.put("source","different-library");assertEquals(2,postData("preview",uid,request,200).path("counts").path("NEW").asInt());
 }
 @Test void changedRequestAndStalePreviewAreRejected() throws Exception {
  long uid=912051;var request=input("front,back\na,b");var p=postData("preview",uid,request,200);
  var changed=input("front,back\na,c");postData("confirm",uid,Map.of("request",changed,"token",p.path("token").asText()),409);
  var second=postData("preview",uid,request,200);
  postData("confirm",uid,Map.of("request",request,"token",p.path("token").asText()),200);
  // Two identical previews may share a token in the same second; both must remain safe to retry.
  var result=postData("confirm",uid,Map.of("request",request,"token",second.path("token").asText()),200);
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard WHERE user_id=?",Integer.class,uid));
 }
 @Test void anonymousUnsupportedFormatsAndExportIsolation() throws Exception {
  mvc.perform(post("/api/learning/my/flashcard-exchange/preview").contentType("application/json").content("{}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
  var invalid=input("front,back\na,b");invalid.put("format","apkg");postData("preview",912061,invalid,400);
  assertEquals(0,exportData(912062,"tsv").path("notes").asInt());
 }
 @Test void concurrentConfirmationCreatesOnlyOneSet() throws Exception {
  long uid=912071;var request=input("front,back\na,b");var p=postData("preview",uid,request,200);
  var body=Map.of("request",request,"token",p.path("token").asText());
  var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
  try {var tasks=java.util.List.<java.util.concurrent.Callable<JsonNode>>of(()->postData("confirm",uid,body,200),()->postData("confirm",uid,body,200));var results=executor.invokeAll(tasks);assertEquals(results.get(0).get(),results.get(1).get());}
  finally{executor.shutdownNow();}
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_flashcard WHERE user_id=?",Integer.class,uid));
 }

 @Test void codeExplanationHtmlAndOwnExportArchivedChildrenAreNotAcceptedAsSafeDuplicates() throws Exception {
  long uid=912081;
  var unsafe=input("type,front,back\nCODE_CLOZE,{{c1::method}},<img src='https://example.com/track.png'>");
  assertEquals(1,postData("preview",uid,unsafe,200).path("counts").path("INVALID").asInt());
 }
 @Test void ownExportArchivedChildrenAreConflicts() throws Exception {
  long uid=912082;
  var response=mvc.perform(post("/api/learning/my/flashcard-notes").header("Authorization",auth(uid)).contentType("application/json").content("{\"type\":\"CLOZE\",\"front\":\"{{c1::a}} {{c2::b}}\"}")).andExpect(status().isOk()).andReturn();
  var note=json.readTree(response.getResponse().getContentAsString()).path("data");
  var export=exportData(uid,"csv");
  jdbc.update("UPDATE learning_flashcard SET deleted=1 WHERE id=?",note.path("cards").get(0).path("id").asLong());
  var p=postData("preview",uid,input(export.path("content").asText()),200);
  assertEquals(1,p.path("counts").path("CONFLICT").asInt());
 }

 @Test void maximumLengthEscapedContentAndCrLfRoundTripWithoutConflict() throws Exception {
  long uid=912091;String front="="+"a".repeat(49999), back="first\r\nsecond\rthird";
  mvc.perform(post("/api/learning/my/flashcard-notes").header("Authorization",auth(uid)).contentType("application/json").content(json.writeValueAsBytes(Map.of("front",front,"back",back)))).andExpect(status().isOk());
  var exported=exportData(uid,"csv");var request=input(exported.path("content").asText());
  var p=postData("preview",uid,request,200);assertEquals(1,p.path("counts").path("DUPLICATE").asInt());
  assertEquals(front,p.path("rows").get(0).path("note").path("front").asText());assertEquals(back,p.path("rows").get(0).path("note").path("back").asText());
 }
 @Test void adminRoleCannotExportAnotherOwnersPrivateContents() throws Exception {
  String admin="Bearer "+jwt.generateToken(912099L,"exchange-admin","ADMIN");
  mvc.perform(get("/api/learning/my/flashcard-exchange/export").header("Authorization",admin))
   .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.notes").value(0));
 }
}
