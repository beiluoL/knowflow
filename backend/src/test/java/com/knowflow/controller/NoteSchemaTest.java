package com.knowflow.controller;
import com.knowflow.service.NoteSchema;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
/** 已有表增量升级且重复执行不损失原数据。 */
class NoteSchemaTest {
 @Test void upgradeTwicePreservesLegacy() throws Exception {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:notes_upgrade;MODE=MySQL","sa","");
  try(var c=ds.getConnection();var st=c.createStatement()) {
   st.execute("CREATE TABLE learning_note(id BIGINT PRIMARY KEY,user_id BIGINT,chapter_id BIGINT,path_id BIGINT,title VARCHAR(200),content LONGTEXT,create_time TIMESTAMP,update_time TIMESTAMP,deleted INT)");
   st.execute("INSERT INTO learning_note VALUES(91,7,8,9,'旧章节','原始中文',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)");
   NoteSchema.migrate(c);NoteSchema.migrate(c);
   try(var rs=st.executeQuery("SELECT * FROM learning_note WHERE id=91")){assertTrue(rs.next());assertEquals("原始中文",rs.getString("content"));assertEquals("CHAPTER",rs.getString("source_type"));assertEquals(1,rs.getLong("revision"));}
  }
 }
}
