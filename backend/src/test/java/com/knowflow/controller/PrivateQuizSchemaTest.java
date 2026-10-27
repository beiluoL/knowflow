package com.knowflow.controller;

import com.knowflow.learning.privatequiz.PrivateQuizSchema;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class PrivateQuizSchemaTest {
 @Test void repeatUpgradeAndRestoreFileDatabasePreservesLegacyAndNewRows() throws Exception{
  var dir=Files.createTempDirectory("private-quiz-migration-");String base=dir.resolve("db").toString();
  String url="jdbc:h2:file:"+base+";MODE=MySQL";
  try(var c=DriverManager.getConnection(url,"sa","");var st=c.createStatement()){
   st.execute("CREATE TABLE learning_note(id BIGINT PRIMARY KEY,content LONGTEXT)");st.execute("INSERT INTO learning_note VALUES(1,'原始正文')");
   PrivateQuizSchema.migrate(c);PrivateQuizSchema.migrate(c);
   com.knowflow.learning.tutor.TutorSchema.migrate(c);com.knowflow.learning.tutor.TutorSchema.migrate(c);
   com.knowflow.learning.tutor.TutorSchema.coaching(c);com.knowflow.learning.tutor.TutorSchema.coaching(c);
   com.knowflow.learning.studypack.StudyPackSchema.migrate(c);com.knowflow.learning.studypack.StudyPackSchema.migrate(c);
   st.execute("INSERT INTO tutor_hint_usage(conversation_id,owner_id,request_id,hint_level,state,source_revision,created_at) VALUES(1,7,'restore-hint',2,'SUCCEEDED',1,CURRENT_TIMESTAMP)");
   st.execute("INSERT INTO study_pack_job(owner_id,source_hash,file_name,source_bytes,state,create_time,update_time) VALUES(7,'restore-hash','restore.txt',X'01','DRAFT_READY',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
   st.execute("INSERT INTO tutor_session(conversation_id,owner_id,note_id,active_mode,revision,create_time) VALUES(1,7,1,'SOCRATIC',1,CURRENT_TIMESTAMP)");
   st.execute("INSERT INTO private_quiz(owner_id,note_id,source_revision,source_hash,title,request_id,state,questions,create_time) VALUES(7,1,1,'sha','私题','restore-key','SAVED','[]',CURRENT_TIMESTAMP)");
  }
  Files.copy(Path.of(base+".mv.db"),dir.resolve("snapshot.mv.db"));
  try(var c=DriverManager.getConnection(url,"sa","");var st=c.createStatement()){
   PrivateQuizSchema.migrate(c);try(var rs=st.executeQuery("SELECT content FROM learning_note WHERE id=1")){assertTrue(rs.next());assertEquals("原始正文",rs.getString(1));}
   st.execute("UPDATE private_quiz SET title='模拟部署后变更'");
  }
  Files.copy(dir.resolve("snapshot.mv.db"),Path.of(base+".mv.db"),StandardCopyOption.REPLACE_EXISTING);
  try(var c=DriverManager.getConnection(url,"sa","");var st=c.createStatement()){
   try(var rs=st.executeQuery("SELECT title FROM private_quiz")){assertTrue(rs.next());assertEquals("私题",rs.getString(1));}
   try(var rs=st.executeQuery("SELECT hint_level,state FROM tutor_hint_usage WHERE request_id='restore-hint'")){assertTrue(rs.next());assertEquals(2,rs.getInt(1));assertEquals("SUCCEEDED",rs.getString(2));}
   try(var rs=st.executeQuery("SELECT active_mode FROM tutor_session WHERE conversation_id=1")){assertTrue(rs.next());assertEquals("SOCRATIC",rs.getString(1));}
   try(var rs=st.executeQuery("SELECT file_name,OCTET_LENGTH(source_bytes) FROM study_pack_job WHERE owner_id=7")){assertTrue(rs.next());assertEquals("restore.txt",rs.getString(1));assertEquals(1,rs.getInt(2));}
  }
 }
}
