package com.knowflow.learning.privatequiz;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.InitializingBean;
import javax.sql.DataSource;
import java.sql.*;

/** Opt-in additive schema. No changes to public questions or historical grades. */
@Configuration
@ConditionalOnProperty(name="knowflow.product.private-quiz.enabled",havingValue="true")
public class PrivateQuizSchema {
 @Bean InitializingBean privateQuizMigration(DataSource ds){return ()->{try(var c=ds.getConnection()){migrate(c);}};}
 public static void migrate(Connection c)throws SQLException{
  try(var st=c.createStatement()){
   st.execute("CREATE TABLE IF NOT EXISTS private_quiz (id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_id BIGINT NOT NULL,note_id BIGINT NOT NULL,source_revision BIGINT NOT NULL,source_hash VARCHAR(64) NOT NULL,title VARCHAR(200) NOT NULL,request_id VARCHAR(80) NOT NULL,state VARCHAR(20) NOT NULL,questions LONGTEXT NOT NULL,version INT NOT NULL DEFAULT 1,create_time TIMESTAMP(6) NOT NULL,UNIQUE(owner_id,request_id),INDEX idx_private_quiz_owner(owner_id,id))");
   st.execute("CREATE TABLE IF NOT EXISTS private_quiz_attempt (id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_id BIGINT NOT NULL,quiz_id BIGINT NOT NULL,request_id VARCHAR(80) NOT NULL,quiz_version INT NOT NULL,question_snapshot LONGTEXT NOT NULL,state VARCHAR(20) NOT NULL,user_answers LONGTEXT,result_json LONGTEXT,create_time TIMESTAMP(6) NOT NULL,submitted_at TIMESTAMP(6),UNIQUE(owner_id,request_id),INDEX idx_private_quiz_history(owner_id,quiz_id,id))");
  }
 }
}
