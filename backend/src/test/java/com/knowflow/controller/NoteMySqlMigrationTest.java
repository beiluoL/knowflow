package com.knowflow.controller;
import com.knowflow.service.NoteSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
/** 仅连接显式启动的隔离 MySQL；常规套件不尝试访问生产数据库。 */
@EnabledIfSystemProperty(named="notes.mysql.enabled",matches="true")
class NoteMySqlMigrationTest {
 @Test void incrementalUpgradePreservesContentAndConditionalUpdateRejectsStaleRevision() throws Exception {
  try(var c=DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/notes_p0?useUnicode=true&characterEncoding=utf8&useSSL=false","root","");var st=c.createStatement()) {
   st.execute("CREATE TABLE IF NOT EXISTS learning_note(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,chapter_id BIGINT,path_id BIGINT,title VARCHAR(200),content LONGTEXT,create_time TIMESTAMP NULL,update_time TIMESTAMP NULL,deleted INT DEFAULT 0) DEFAULT CHARSET=utf8mb4");
   st.executeUpdate("INSERT INTO learning_note(user_id,title,content) VALUES(7,'隔离升级','MySQL原始中文')");
   long id;try(var rs=st.executeQuery("SELECT LAST_INSERT_ID()")){rs.next();id=rs.getLong(1);}
   NoteSchema.migrate(c);NoteSchema.migrate(c);
   try(var rs=st.executeQuery("SELECT content,revision FROM learning_note WHERE id="+id)){assertTrue(rs.next());assertEquals("MySQL原始中文",rs.getString(1));assertEquals(1,rs.getLong(2));}
   String update="UPDATE learning_note SET content='新版本',revision=revision+1 WHERE id="+id+" AND user_id=7 AND revision=1 AND deleted=0";
   assertEquals(1,st.executeUpdate(update));assertEquals(0,st.executeUpdate(update));
   assertEquals(0,st.executeUpdate("UPDATE learning_note SET deleted=1 WHERE id="+id+" AND user_id=8"));
  }
 }
}
