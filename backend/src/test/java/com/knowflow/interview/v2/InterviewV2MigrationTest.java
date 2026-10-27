package com.knowflow.interview.v2;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 只在独立内存库验证增量重复执行；保留旧BASIC快照，不触碰真实持久库。 */
class InterviewV2MigrationTest {
 @Test void additiveScriptsAreRepeatableAndPreserveLegacySessionWithChineseComments(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:phase4i_migration;DB_CLOSE_DELAY=-1;MODE=MySQL","sa","");var jdbc=new JdbcTemplate(ds);
  new ResourceDatabasePopulator(new ClassPathResource("db/h2/schema.sql")).execute(ds);
  // 模拟升级前数据库，只移除本测试中全新的V2辅助表。
  for(String table:List.of("interview_remediation","mock_interview_v2_command","mock_interview_v2_turn","mock_interview_v2_state","mock_interview_speech"))jdbc.execute("DROP TABLE "+table);
  jdbc.update("INSERT INTO mock_interview_session(user_id,track,mode,status,question_count,questions_json,answers_json) VALUES(1,'JAVA_BASIC','BASIC','FINISHED',1,'[{\"id\":\"legacy\"}]','[]')");
  Long legacy=jdbc.queryForObject("SELECT MAX(id) FROM mock_interview_session",Long.class);
  for(int repeat=0;repeat<2;repeat++)for(String script:List.of("V20261016__interview_speech.sql","V20261015__interview_v2.sql","V20261017__interview_remediation.sql"))new ResourceDatabasePopulator(new ClassPathResource("db/h2/migration/"+script)).execute(ds);
  assertEquals("[{\"id\":\"legacy\"}]",jdbc.queryForObject("SELECT questions_json FROM mock_interview_session WHERE id=?",String.class,legacy));
  assertEquals("FINISHED",jdbc.queryForObject("SELECT status FROM mock_interview_session WHERE id=?",String.class,legacy));
  for(String table:List.of("INTERVIEW_REMEDIATION","MOCK_INTERVIEW_V2_COMMAND","MOCK_INTERVIEW_V2_TURN","MOCK_INTERVIEW_V2_STATE","MOCK_INTERVIEW_SPEECH")){
   assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME=? AND (REMARKS IS NULL OR REMARKS='')",Integer.class,table));
   assertNotNull(jdbc.queryForObject("SELECT REMARKS FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME=?",String.class,table));
  }
 }
}
