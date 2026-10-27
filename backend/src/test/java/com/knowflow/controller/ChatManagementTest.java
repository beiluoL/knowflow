package com.knowflow.controller;
import com.knowflow.service.ChatService;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local")
@TestPropertySource(properties={"knowflow.datasource.config-file=/tmp/chat-management-no-config.json","knowflow.datasource.type=h2","knowflow.datasource.h2.url=jdbc:h2:mem:chat_management;DB_CLOSE_DELAY=-1;MODE=MySQL"})
class ChatManagementTest {
 @Autowired MockMvc http; @Autowired ChatService service; @Autowired JwtUtils jwt;
 String auth(long id,String role){return "Bearer "+jwt.generateToken(id,"management",role);}
 @Test void stateOwnerOnlyAndEmptyBodyRejected()throws Exception{
 long id=service.createConversation(998001L,"安全对话").getId();String url="/api/chat/conversations/"+id+"/state";
 http.perform(patch(url).header("Authorization",auth(998001,"USER")).contentType("application/json").content("{\"pinned\":true,\"archived\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.archived").value(true));
 for(String role:new String[]{"USER","ADMIN"}) http.perform(patch(url).header("Authorization",auth(998002,role)).contentType("application/json").content("{\"pinned\":false}")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
 http.perform(patch(url).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
 http.perform(patch(url).header("Authorization",auth(998001,"USER")).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
 }

 @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @Autowired com.knowflow.service.ChatShareService shares;
 @Autowired com.knowflow.service.ChatManagementSchema schema;
 String createShare(long id)throws Exception{return http.perform(post("/api/chat/conversations/"+id+"/shares").header("Authorization",auth(998001,"USER")).contentType("application/json").content("{\"confirmed\":true,\"expiresInHours\":24}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();}
 @Test void publicSnapshotIsImmutableRevocableAndDeletedSourceInvalidatesIt()throws Exception{
  long id=service.createConversation(998001L,"公开文本").getId();
  jdbc.update("INSERT INTO chat_message(conversation_id,user_id,role,content,deleted) VALUES(?,?,?, ?,0)",id,998001,"user","safe text");
  var data=json.readTree(createShare(id)).path("data");String token=data.path("urlPath").asText().substring(12);
  http.perform(get("/api/public/chat-shares/"+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.messages[0].content").value("safe text")).andExpect(jsonPath("$.data.userId").doesNotExist());
  jdbc.update("UPDATE chat_message SET content='changed' WHERE conversation_id=?",id);
  http.perform(get("/api/public/chat-shares/"+token)).andExpect(jsonPath("$.data.messages[0].content").value("safe text"));
  String listing=http.perform(get("/api/chat/conversations/"+id+"/shares").header("Authorization",auth(998001,"USER"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();org.junit.jupiter.api.Assertions.assertFalse(listing.contains(token));
  for(String role:new String[]{"USER","ADMIN"})http.perform(delete("/api/chat/conversations/"+id+"/shares/"+data.path("id").asLong()).header("Authorization",auth(998002,role))).andExpect(status().isNotFound());
  shares.revoke(id,998001,data.path("id").asLong());http.perform(get("/api/public/chat-shares/"+token)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
  data=json.readTree(createShare(id)).path("data");token=data.path("urlPath").asText().substring(12);service.deleteConversation(id,998001L);http.perform(get("/api/public/chat-shares/"+token)).andExpect(status().isNotFound());
 }
 @Test void expiryConfirmationSecretsAndKnowledgeReferencesAreEnforced()throws Exception{
  long id=service.createConversation(998001L,"安全").getId();
  org.junit.jupiter.api.Assertions.assertThrows(com.knowflow.exception.BusinessException.class,()->shares.create(id,998001,24,false));
  org.junit.jupiter.api.Assertions.assertThrows(com.knowflow.exception.BusinessException.class,()->shares.create(id,998001,1,true));
  long kbId=service.createConversation(998001L,"KB history").getId();
  jdbc.update("INSERT INTO chat_message(conversation_id,user_id,role,content,doc_references,deleted) VALUES(?,?,?,?,?,0)",kbId,998001,"assistant","private knowledge","[{source:private}]");
  jdbc.update("INSERT INTO chat_message(conversation_id,user_id,role,content,deleted) VALUES(?,?,?,?,0)",kbId,998001,"assistant","later unreferenced answer");
  org.junit.jupiter.api.Assertions.assertThrows(com.knowflow.exception.BusinessException.class,()->shares.create(kbId,998001,24,true));
  jdbc.update("UPDATE chat_message SET deleted=1 WHERE conversation_id=? AND doc_references IS NOT NULL",kbId);
  org.junit.jupiter.api.Assertions.assertThrows(com.knowflow.exception.BusinessException.class,()->shares.create(kbId,998001,24,true));
  jdbc.update("INSERT INTO chat_message(conversation_id,user_id,role,content,deleted) VALUES(?,?,?,?,0)",id,998001,"user","show today's tasks");
  jdbc.update("INSERT INTO chat_message(conversation_id,user_id,role,content,deleted) VALUES(?,?,?,?,0)",id,998001,"assistant","{\"kind\":\"KNOWFLOW_LEARNING_ACTION\",\"planId\":1,\"confirmationToken\":\"private\"}");
  var data=shares.create(id,998001,24,true);var token=data.get("urlPath").toString().substring(12);var snapshot=shares.read(token);org.junit.jupiter.api.Assertions.assertEquals(1,((java.util.List<?>)snapshot.get("messages")).size());org.junit.jupiter.api.Assertions.assertFalse(snapshot.toString().contains("confirmationToken"));
  jdbc.update("UPDATE chat_public_share SET expires_at=? WHERE id=?",java.time.LocalDateTime.now().minusHours(1),data.get("id"));org.junit.jupiter.api.Assertions.assertThrows(com.knowflow.exception.BusinessException.class,()->shares.read(token));
  jdbc.update("INSERT INTO chat_message(conversation_id,user_id,role,content,deleted) VALUES(?,?,?,?,0)",id,998001,"user","api_key=do-not-publish");org.junit.jupiter.api.Assertions.assertThrows(com.knowflow.exception.BusinessException.class,()->shares.create(id,998001,24,true));
  schema.initialize();schema.initialize();
  org.junit.jupiter.api.Assertions.assertFalse(service.getById(id).getPinned());
  org.junit.jupiter.api.Assertions.assertFalse(service.getById(id).getArchived());
 }

 @Test void archivedFilteringPinOrderAndShareAuthMatrix()throws Exception{
  long first=service.createConversation(998011L,"first").getId(),second=service.createConversation(998011L,"second").getId();
  service.updateState(first,998011L,true,null);
  http.perform(get("/api/chat/conversations").header("Authorization",auth(998011,"USER"))).andExpect(jsonPath("$.data[0].id").value(first));
  service.updateState(first,998011L,null,true);
  http.perform(get("/api/chat/conversations").header("Authorization",auth(998011,"USER"))).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(second));
  http.perform(get("/api/chat/conversations?archived=true").header("Authorization",auth(998011,"USER"))).andExpect(jsonPath("$.data[0].id").value(first));
  String path="/api/chat/conversations/"+first+"/shares";
  http.perform(post(path).contentType("application/json").content("{\"confirmed\":true,\"expiresInHours\":24}")).andExpect(status().isUnauthorized());
  for(String role:new String[]{"USER","ADMIN"}){
   http.perform(post(path).header("Authorization",auth(998002,role)).contentType("application/json").content("{\"confirmed\":true,\"expiresInHours\":24}")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
   http.perform(get(path).header("Authorization",auth(998002,role))).andExpect(status().isNotFound());
  }
  org.junit.jupiter.api.Assertions.assertNotNull(service.getMessageList(first,998011L));
 }

 @Test void laterKnowledgeHistoryInvalidatesExistingPublicSnapshotIncludingDeletedHistory()throws Exception{
  long id=service.createConversation(998001L,"initial safe conversation").getId();
  jdbc.update("INSERT INTO chat_message(conversation_id,user_id,role,content,deleted) VALUES(?,?,?,?,0)",id,998001,"user","safe text");
  var share=shares.create(id,998001,24,true);String token=share.get("urlPath").toString().substring(12);
  http.perform(get("/api/public/chat-shares/"+token)).andExpect(status().isOk());
  jdbc.update("INSERT INTO chat_message(conversation_id,user_id,role,content,doc_references,deleted) VALUES(?,?,?,?,?,1)",id,998001,"assistant","private history","[{source:private}]");
  http.perform(get("/api/public/chat-shares/"+token)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
 }
}
