package com.knowflow.interview.radar;
import com.knowflow.learning.feedback.FeedbackSchema;
import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
class ProductFeedbackMigrationTest {
 @Test void additiveMigrationIsRepeatableAndPreservesHistory()throws Exception {
  try(var c=DriverManager.getConnection("jdbc:h2:mem:feedback_migration;MODE=MySQL","sa","");var st=c.createStatement()){
   st.execute("CREATE TABLE learning_error_annotation(id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_id BIGINT NOT NULL,source_type VARCHAR(32) NOT NULL,source_id BIGINT NOT NULL,question_index INT NOT NULL,source_version VARCHAR(100) NOT NULL,request_id VARCHAR(80) NOT NULL,state VARCHAR(20) NOT NULL,revision BIGINT NOT NULL DEFAULT 0,model_scope VARCHAR(200),prompt_version VARCHAR(60) NOT NULL,error_message VARCHAR(500),created_at TIMESTAMP(6) NOT NULL,updated_at TIMESTAMP(6) NOT NULL,UNIQUE(owner_id,request_id))");
   st.execute("INSERT INTO learning_error_annotation(owner_id,source_type,source_id,question_index,source_version,request_id,state,revision,prompt_version,created_at,updated_at)VALUES(1,'INTERVIEW_TURN',3,0,'old','historical-001','SUCCEEDED',2,'v1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
   FeedbackSchema.migrate(c);FeedbackSchema.migrate(c);
   try(var r=st.executeQuery("SELECT state,revision FROM learning_error_annotation WHERE id=1")){assertTrue(r.next());assertEquals("SUCCEEDED",r.getString(1));assertEquals(2,r.getInt(2));}
   for(String table:new String[]{"learning_error_revision","learning_error_practice","learning_interview_retest","learning_interview_practice","learning_question_variant","learning_interview_retest_history"})try(var r=st.executeQuery("SELECT COUNT(*) FROM "+table)){assertTrue(r.next());assertEquals(0,r.getInt(1));}
  }
 }
}
