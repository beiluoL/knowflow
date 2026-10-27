package com.knowflow.controller;

import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.config-file=/tmp/select-v2-tag-rel-no-config.json","knowflow.datasource.type=h2","knowflow.datasource.h2.url=jdbc:h2:mem:select_v2_tag_rel;DB_CLOSE_DELAY=-1;MODE=MySQL"})
class TaskTagRelationHttpTest {
 @Autowired MockMvc http; @Autowired SysUserMapper users; @Autowired TaskMapper tasks;
 @Autowired TaskTagMapper tags; @Autowired JdbcTemplate db; @Autowired JwtUtils jwt;
 long user(){var u=new SysUser();u.setUsername("select-tag-"+UUID.randomUUID());u.setPassword("test-only");u.setRole("USER");users.insert(u);return u.getId();}
 long task(long user){var t=new Task();t.setUserId(user);t.setTitle("标签关系回归");t.setStatus(0);t.setParentId(0L);t.setStage(0);tasks.insert(t);return t.getId();}
 long tag(long user){var t=new TaskTag();t.setUserId(user);t.setName("tag-"+UUID.randomUUID());tags.insert(t);return t.getId();}
 void set(long user,long task,String ids,int code)throws Exception {http.perform(put("/api/tasks/"+task+"/tags").header("Authorization","Bearer "+jwt.generateToken(user,"tag-test","USER")).contentType("application/json").content("{\"tagIds\":"+ids+"}")).andExpect(status().is(code)).andExpect(jsonPath("$.code").value(code));}
 String ids(long... ids){return Arrays.toString(ids);}
 long count(long task,boolean active){return db.queryForObject("SELECT COUNT(*) FROM task_tag_rel WHERE task_id=?"+(active?" AND deleted=0":""),Long.class,task);}
 long relId(long task,long tag){return db.queryForObject("SELECT id FROM task_tag_rel WHERE task_id=? AND tag_id=?",Long.class,task,tag);}
 @Test void repeatedSameSetReusesUniqueRelationship()throws Exception {long u=user(),t=task(u),a=tag(u);set(u,t,ids(a),200);long id=relId(t,a);set(u,t,ids(a),200);assertEquals(1,count(t,true));assertEquals(1,count(t,false));assertEquals(id,relId(t,a));}
 @Test void addRemoveClearRestoreKeepsRowsAndDeduplicatesIds()throws Exception {long u=user(),t=task(u),a=tag(u),b=tag(u);set(u,t,ids(a),200);long id=relId(t,a);set(u,t,ids(a,b,a,b),200);assertEquals(2,count(t,true));set(u,t,ids(b),200);assertEquals(1,count(t,true));assertEquals(2,count(t,false));set(u,t,"[]",200);assertEquals(0,count(t,true));set(u,t,ids(a,a),200);assertEquals(1,count(t,true));assertEquals(2,count(t,false));assertEquals(id,relId(t,a));}
 @Test void foreignAndDeletedTagsAreFilteredBeforeRestoringRelations()throws Exception {long u=user(),other=user(),t=task(u),a=tag(u),foreign=tag(other),deleted=tag(u);tags.deleteById(deleted);set(u,t,ids(a,foreign,deleted),200);assertEquals(1,count(t,true));assertEquals(1,count(t,false));set(u,t,"[null,0,-1]",200);assertEquals(0,count(t,true));assertEquals(1,count(t,false));}
 @Test void otherTaskAndAnonymousCannotWriteOrClearRelations()throws Exception {long u=user(),other=user(),t=task(u),a=tag(u);set(u,t,ids(a),200);set(other,t,"[]",404);assertEquals(1,count(t,true));assertEquals(1,count(t,false));http.perform(put("/api/tasks/"+t+"/tags").contentType("application/json").content("{\"tagIds\":[]}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));assertEquals(1,count(t,true));}
}
