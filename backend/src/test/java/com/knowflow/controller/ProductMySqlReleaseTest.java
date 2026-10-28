package com.knowflow.controller;

import com.knowflow.learning.privatequiz.PrivateQuizSchema;
import com.knowflow.learning.tutor.TutorSchema;
import com.knowflow.learning.studypack.StudyPackSchema;
import com.knowflow.learning.feedback.FeedbackSchema;
import com.knowflow.learning.adaptive.AdaptiveSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit disposable database only; never falls back to H2 or a business datasource. */
@EnabledIfEnvironmentVariable(named="V1_MYSQL_URL", matches=".+")
class ProductMySqlReleaseTest {
 @Test void repeatedProductUpgradePreservesFrozenHistory() throws Exception {
  String url=System.getenv("V1_MYSQL_URL");
  if(!url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/knowflow_v1_(ci|restore)(\\?.*)?"))
   throw new IllegalArgumentException("Only loopback disposable knowflow_v1_ci/restore permitted");
  try(var c=DriverManager.getConnection(url,System.getenv("V1_MYSQL_USER"),System.getenv("V1_MYSQL_PASSWORD"));var s=c.createStatement()) {
   assertEquals("MySQL",c.getMetaData().getDatabaseProductName());
   System.out.println("V1 database version="+c.getMetaData().getDatabaseProductVersion());
   migrate(c);
   if(!"true".equals(System.getenv("V1_MYSQL_VERIFY_ONLY"))) {
    s.executeUpdate("INSERT INTO private_quiz(owner_id,note_id,source_revision,source_hash,title,request_id,state,questions,version,create_time) VALUES(70001,1,1,'synthetic-sha','合成历史📚','v1-frozen','SAVED','[{\"answer\":0}]',1,CURRENT_TIMESTAMP(6))");
    s.executeUpdate("INSERT INTO private_quiz_attempt(owner_id,quiz_id,request_id,quiz_version,question_snapshot,state,user_answers,result_json,create_time,submitted_at) VALUES(70001,LAST_INSERT_ID(),'v1-attempt',1,'[{\"answer\":0}]','SUBMITTED','[0]','{\"score\":0}',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))");
    s.executeUpdate("INSERT INTO tutor_session VALUES(1,70001,1,'TEACH_BACK',1,CURRENT_TIMESTAMP(6))");
    s.executeUpdate("INSERT INTO study_pack_job(owner_id,source_hash,file_name,source_bytes,state,create_time,update_time) VALUES(70001,'v1-source','合成.txt',X'0001FF','DRAFT_READY',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))");
   }
   migrate(c);migrate(c);
   try(var r=s.executeQuery("SELECT question_snapshot,user_answers,result_json,quiz_version FROM private_quiz_attempt WHERE owner_id=70001 AND request_id='v1-attempt'")) {
    assertTrue(r.next());assertEquals("[{\"answer\":0}]",r.getString(1));assertEquals("[0]",r.getString(2));assertEquals("{\"score\":0}",r.getString(3));assertEquals(1,r.getInt(4));assertFalse(r.next());
   }
   try(var r=s.executeQuery("SELECT title FROM private_quiz WHERE request_id='v1-frozen'")){assertTrue(r.next());assertEquals("合成历史📚",r.getString(1));}
   try(var r=s.executeQuery("SELECT HEX(source_bytes) FROM study_pack_job WHERE source_hash='v1-source'")){assertTrue(r.next());assertEquals("0001FF",r.getString(1));}
   assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO tutor_session VALUES(2,70001,1,'SOCRATIC',1,CURRENT_TIMESTAMP(6))"));
  }
 }
 private void migrate(Connection c)throws Exception {
  PrivateQuizSchema.migrate(c);TutorSchema.migrate(c);TutorSchema.coaching(c);
  StudyPackSchema.migrate(c);FeedbackSchema.migrate(c);AdaptiveSchema.migrate(c);
 }
}
