package com.knowflow.controller;
import com.fasterxml.jackson.databind.*;
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

/** 工作台组织操作必须走真实认证、数据库和revision。 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.h2.url=jdbc:h2:mem:note_organization;DB_CLOSE_DELAY=-1;MODE=MySQL","knowflow.datasource.config-file=/tmp/knowflow-note-organization-no-config.json"})
class NoteOrganizationHttpTest {
 @Autowired MockMvc mvc;@Autowired JwtUtils jwt;@Autowired ObjectMapper json;
 JsonNode call(String method,String path,Object body,long user,int code)throws Exception {
  var req=switch(method){case "POST"->post(path);case "PUT"->put(path);case "DELETE"->delete(path);default->get(path);};
  var r=mvc.perform(req.header("Authorization","Bearer "+jwt.generateToken(user,"org-"+user,"USER")).contentType("application/json").content(body==null?"":json.writeValueAsString(body))).andExpect(status().is(code)).andExpect(jsonPath("$.code").value(code)).andReturn();
  return json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
 }
 @Test void folderFavoriteSearchAndTagsAreRealAndIsolated()throws Exception {
  var f=call("POST","/api/notes/folders",java.util.Map.of("name","Java基础"),99221,200);long folder=f.path("id").asLong();
  var child=call("POST","/api/notes/folders",java.util.Map.of("name","并发","parentId",folder),99221,200);
  call("PUT","/api/notes/folders/"+folder,java.util.Map.of("name","循环","parentId",child.path("id").asLong(),"revision",1),99221,400);
  call("DELETE","/api/notes/folders/"+folder+"?revision=1",null,99222,404);
  var n=call("POST","/api/notes",java.util.Map.of("title","组织测试","content","真实正文","tags","[\"重点\",\"Java\"]","folderId",folder,"favorite",true),99221,200);long id=n.path("id").asLong();
  assertEquals(folder,n.path("folderId").asLong());assertTrue(n.path("favorite").asBoolean());
  assertEquals(1,call("GET","/api/notes?view=FAVORITES&keyword=重点",null,99221,200).path("total").asInt());
  assertEquals(0,call("GET","/api/notes?view=FAVORITES",null,99222,200).path("total").asInt());
  call("DELETE","/api/notes/folders/"+folder+"?revision=1",null,99221,409);
  var tags=call("GET","/api/notes/tags",null,99221,200);assertEquals(2,tags.size());long tagId=0;for(var t:tags)if(t.path("name").asText().equals("重点"))tagId=t.path("id").asLong();assertTrue(tagId>0);
  call("PUT","/api/notes/tags/"+tagId,java.util.Map.of("name","面试重点"),99221,200);
  var edited=call("GET","/api/notes/"+id,null,99221,200);assertEquals(2,edited.path("revision").asInt());assertTrue(edited.path("tags").asText().contains("面试重点"));
  call("DELETE","/api/notes/tags/"+tagId,null,99222,404);
  var stats=call("GET","/api/notes/stats",null,99221,200);assertEquals(1,stats.path("favorites").asInt());
 }
 @Test void folderMoveKeepsHistoryAndForeignRelationsAreDenied()throws Exception {
  var f=call("POST","/api/notes/folders",java.util.Map.of("name","移动目录"),99223,200);long folder=f.path("id").asLong();
  var n=call("POST","/api/notes",java.util.Map.of("title","原文","content","不可丢失"),99223,200);long id=n.path("id").asLong();
  call("PUT","/api/notes/"+id,java.util.Map.of("title","原文","content","不可丢失","revision",1,"folderId",folder),99224,404);
  var moved=call("PUT","/api/notes/"+id,java.util.Map.of("title","原文","content","不可丢失","revision",1,"folderId",folder),99223,200);assertEquals(2,moved.path("revision").asInt());
  assertEquals(2,call("GET","/api/notes/"+id+"/versions",null,99223,200).size());
  var other=call("POST","/api/notes",java.util.Map.of("title","他人","content","私有"),99224,200);
  call("POST","/api/notes/"+id+"/relations",java.util.Map.of("type","NOTE","targetId",other.path("id").asLong()),99223,404);
  var target=call("POST","/api/notes",java.util.Map.of("title","关联目标","content","内容"),99223,200);
  call("POST","/api/notes/"+id+"/relations",java.util.Map.of("type","NOTE","targetId",target.path("id").asLong()),99223,200);
  assertEquals(1,call("GET","/api/notes/"+target.path("id").asLong()+"/relations",null,99223,200).path("backlinks").size());
 }

 @Test void imageUploadIsAuthenticatedAndRejectsActiveContent()throws Exception {
  var svg=new org.springframework.mock.web.MockMultipartFile("file","unsafe.svg","image/svg+xml","<svg onload='alert(1)'/>".getBytes());
  mvc.perform(multipart("/api/notes/images").file(svg)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
  mvc.perform(multipart("/api/notes/images").file(svg).header("Authorization","Bearer "+jwt.generateToken(99225L,"image-user","USER"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
  var png=new org.springframework.mock.web.MockMultipartFile("file","safe.png","image/png",java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jx1sAAAAASUVORK5CYII="));
  mvc.perform(multipart("/api/notes/images").file(png).header("Authorization","Bearer "+jwt.generateToken(99225L,"image-user","USER"))).andExpect(status().isOk()).andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));
 }
}
