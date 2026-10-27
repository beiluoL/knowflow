package com.knowflow.controller;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
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
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local") @Import(FakeAiConfiguration.class)
@TestPropertySource(properties={"knowflow.datasource.config-file=/tmp/knowflow-phase4e-tag-test-no-config.json","knowflow.datasource.type=h2","knowflow.datasource.h2.url=jdbc:h2:mem:phase4e_tags;DB_CLOSE_DELAY=-1;MODE=MySQL"})
class TaskTagEmptyHttpTest {
 @Autowired MockMvc http; @Autowired SysUserMapper users; @Autowired JwtUtils jwt;
 @Test void accountWithNoTagsReturnsRealEmptyCollection() throws Exception {
  SysUser u=new SysUser();u.setUsername("phase4e-tag-"+UUID.randomUUID());u.setPassword("unused");u.setRole("USER");users.insert(u);
  http.perform(get("/api/task-tags").header("Authorization","Bearer "+jwt.generateToken(u.getId(),u.getUsername(),"USER")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").isEmpty());
 }
 @Test void anonymousStillCannotReadTags() throws Exception {http.perform(get("/api/task-tags")).andExpect(status().isUnauthorized());}
}
