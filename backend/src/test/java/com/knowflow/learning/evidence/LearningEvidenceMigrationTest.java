package com.knowflow.learning.evidence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LearningEvidenceMigrationTest {
 @Test void additiveMigrationCanRepeatWithoutChangingLegacyNotesCardsOrAnswers() {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:v7_migration;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
  var jdbc=new JdbcTemplate(ds);
  new ResourceDatabasePopulator(new ClassPathResource("db/h2/schema.sql")).execute(ds);
  jdbc.update("INSERT INTO learning_note(user_id,title,content) VALUES(97101,'旧笔记','旧正文与中文')");
  jdbc.update("INSERT INTO learning_flashcard(user_id,front,back) VALUES(97101,'旧卡正面','旧卡背面')");
  jdbc.update("INSERT INTO quiz_answer_record(user_id,question_id,user_answer,score) VALUES(97101,1,'首轮原答',0)");
  var before=new LinkedHashMap<String,List<Map<String,Object>>>();
  for(String table:List.of("learning_note","learning_flashcard","quiz_answer_record","exam_attempt","exam_answer"))before.put(table,jdbc.queryForList("SELECT * FROM "+table));
  for(int i=0;i<2;i++)new ResourceDatabasePopulator(new ClassPathResource("db/h2/migration/V20261025__learning_evidence_receipt.sql")).execute(ds);
  for(var entry:before.entrySet())assertEquals(entry.getValue(),jdbc.queryForList("SELECT * FROM "+entry.getKey()));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_evidence_receipt",Integer.class));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_evidence_knowledge",Integer.class));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_evidence_retest",Integer.class));
 }
}
