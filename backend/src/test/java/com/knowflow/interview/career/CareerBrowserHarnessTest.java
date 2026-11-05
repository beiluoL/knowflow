package com.knowflow.interview.career;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.file.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** 显式开启的真实浏览器夹具：仅内存H2+测试模型，正常测试不会启动端口或调用模型。 */
@EnabledIfEnvironmentVariable(named="KNOWFLOW_V22_BROWSER_HARNESS",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties={
 "server.port=18083","knowflow.datasource.type=h2","knowflow.datasource.h2.url=jdbc:h2:mem:v22_browser;DB_CLOSE_DELAY=-1;MODE=MySQL",
 "knowflow.datasource.config-file=/tmp/v22-browser-no-overrides.json","knowflow.datasource.allow-runtime-switch=false",
 "knowflow.seed.development-account.enabled=false","knowflow.ai.capability.enabled=true"})
@ActiveProfiles("local")
class CareerBrowserHarnessTest {
 @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
 @MockBean com.knowflow.ai.service.impl.AiServiceImpl ai;
 @Test void browserUsesRealAuthenticationPersistenceAndOriginalInterview()throws Exception{
  String url=jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)c->c.getMetaData().getURL());
  assertNotNull(url);assertTrue(url.startsWith("jdbc:h2:mem:"));
  jdbc.update("INSERT INTO sys_user(username,email,password,nickname,role) VALUES(?,?,?,?, 'user')","v22_fixture_user","v22_fixture@example.test",new BCryptPasswordEncoder().encode("SyntheticV22!2026"),"V22合成测试用户");
  long user=jdbc.queryForObject("SELECT id FROM sys_user WHERE username='v22_fixture_user'",Long.class);
  jdbc.update("INSERT INTO doc_category(name,status,owner_id) VALUES('V22合成测试课程',1,?)",user);
  long category=jdbc.queryForObject("SELECT MAX(id) FROM doc_category WHERE owner_id=?",Long.class,user);
  jdbc.update("INSERT INTO doc_document(title,content,category_id,status,deleted) VALUES('V22合成课程资料','工程边界：明确适用条件，通过测试验证故障恢复和权限边界。',?,1,0)",category);
  long document=jdbc.queryForObject("SELECT MAX(id) FROM doc_document WHERE category_id=?",Long.class,category);
  if(jdbc.queryForObject("SELECT COUNT(*) FROM kg_entity WHERE name='工程边界'",Integer.class)==0)jdbc.update("INSERT INTO kg_entity(name,type,category_id,doc_id,description) VALUES('工程边界','CONCEPT',?,?,'仅用于隔离浏览器回归的合成知识实体')",category,document);
  when(ai.isConfigured(anyLong())).thenReturn(true);when(ai.currentScope(anyLong())).thenReturn("V22_PROVIDER_TEST_DOUBLE_NOT_REAL_MODEL");
  when(ai.completeStructured(anyString(),anyString(),isNull(),anyLong())).thenAnswer(invocation->{
   String system=invocation.getArgument(0),source=invocation.getArgument(1);
   if(system.contains("个人求职资料")){
    String key=source.contains("岗位")?"requiredSkills":"skills";
    return json.writeValueAsString(Map.of(key,List.of(Map.of("value","Java 并发","quote","Java 并发"))));
   }
   var input=json.readTree(source);String answer=input.path("confirmedAnswer").asText();int depth=input.path("depth").asInt();
   var evaluation=new LinkedHashMap<String,Object>();evaluation.put("dimensions",Map.of("TECHNICAL_CORRECTNESS",80,"KNOWLEDGE_COVERAGE",70,"ENGINEERING_PRACTICE",65,"EXPRESSION_STRUCTURE",75,"FOLLOWUP_RESPONSE",70));
   evaluation.put("coveredKnowledge",List.of());evaluation.put("errors",List.of());evaluation.put("strengths",List.of("明确区分已知与未知（测试替身）"));evaluation.put("gaps",List.of("工程边界"));evaluation.put("citations",List.of(answer.substring(0,Math.min(12,answer.length()))));evaluation.put("suggestion","补充工程边界并通过实际练习验证");evaluation.put("confidence",0.5);
   return json.writeValueAsString(Map.of("decision",depth==0?"FOLLOW_UP":"NEXT_QUESTION","reason","隔离契约测试，不代表真实模型判断","missingKnowledge",List.of("工程边界"),"followupQuestion",depth==0?"工程边界的适用条件怎样验证？":"","targetDimension","ENGINEERING_PRACTICE","evaluation",evaluation));
  });
  Path signal=Path.of(System.getenv("KNOWFLOW_V22_FINISH_FILE"));long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(20);
  while(!Files.exists(signal)&&System.nanoTime()<deadline)Thread.sleep(500);
  assertTrue(Files.exists(signal),"浏览器夹具未收到完成信号");
  assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM interview_career_record WHERE user_id=?",Integer.class,user)>=2);
  assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM interview_career_session c JOIN mock_interview_v2_state s ON s.session_id=c.session_id WHERE c.user_id=? AND s.state='COMPLETED'",Integer.class,user)>=1);
  assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM interview_remediation WHERE user_id=?",Integer.class,user)>=1);
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM doc_document WHERE content LIKE '%合成测试简历%'",Integer.class));
 }
}
