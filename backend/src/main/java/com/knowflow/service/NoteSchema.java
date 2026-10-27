package com.knowflow.service;
import java.sql.*;
import java.util.*;
/** 双方言增量迁移：启动及数据源切换时，在目标连接发布前执行。 */
public final class NoteSchema {
 private NoteSchema() {}
 public static void migrate(Connection c) throws SQLException {
  // 兼容数据源管理器的局部数据库；不存在笔记基础表时不创建孤立版本表。
  boolean exists=false;
  try(var tables=c.getMetaData().getTables(c.getCatalog(),c.getSchema(),null,new String[]{"TABLE"})){while(tables.next())if("learning_note".equalsIgnoreCase(tables.getString("TABLE_NAME")))exists=true;}
  if(!exists)return;
  Set<String> columns=new HashSet<>();
  try(var ps=c.prepareStatement("SELECT * FROM learning_note WHERE 1=0");var rs=ps.executeQuery()) {
   for(int i=1;i<=rs.getMetaData().getColumnCount();i++) columns.add(rs.getMetaData().getColumnName(i).toLowerCase(Locale.ROOT));
  }
  Map<String,String> additions=new LinkedHashMap<>();
  additions.put("folder_id","BIGINT COMMENT '所属笔记文件夹'");
  additions.put("favorite","BOOLEAN NOT NULL DEFAULT FALSE COMMENT '个人收藏'");
  additions.put("source_doc_id","BIGINT COMMENT '来源文档ID，读取时重新校验权限'");
  additions.put("revision","BIGINT NOT NULL DEFAULT 1 COMMENT '递增修订号'");
  additions.put("source_type","VARCHAR(20) NOT NULL DEFAULT 'GENERAL' COMMENT '普通或章节来源'");
  additions.put("content_format","VARCHAR(20) NOT NULL DEFAULT 'MARKDOWN' COMMENT '内容格式'");
  additions.put("status","VARCHAR(20) NOT NULL DEFAULT 'SAVED' COMMENT '草稿或已保存'");
  additions.put("tags","VARCHAR(2000) COMMENT '笔记标签'");
  additions.put("source_title","VARCHAR(500) COMMENT '来源标题提示，非授权关联'");
  additions.put("import_key","VARCHAR(64) COMMENT '服务端迁移指纹'");
  try(var st=c.createStatement()) {
   for(var e:additions.entrySet()) if(!columns.contains(e.getKey()))st.execute("ALTER TABLE learning_note ADD COLUMN "+e.getKey()+" "+e.getValue());
   if(!columns.contains("source_type"))st.execute("UPDATE learning_note SET source_type='CHAPTER' WHERE chapter_id IS NOT NULL OR path_id IS NOT NULL");
   boolean unique=false, list=false;
   try(var rs=c.getMetaData().getIndexInfo(c.getCatalog(),c.getSchema(),"learning_note",false,false)) {
    while(rs.next()) {String name=rs.getString("INDEX_NAME");if("uk_note_import".equalsIgnoreCase(name))unique=true;if("idx_note_owner_updated".equalsIgnoreCase(name))list=true;}
   }
   // H2 默认大写表名，元数据查询大小写需按数据库规则重查。
   if(c.getMetaData().storesUpperCaseIdentifiers())try(var rs=c.getMetaData().getIndexInfo(c.getCatalog(),c.getSchema(),"LEARNING_NOTE",false,false)) {
    while(rs.next()){String name=rs.getString("INDEX_NAME");if("uk_note_import".equalsIgnoreCase(name))unique=true;if("idx_note_owner_updated".equalsIgnoreCase(name))list=true;}
   }
   if(!unique)st.execute("CREATE UNIQUE INDEX uk_note_import ON learning_note(user_id,import_key)");
   if(!list)st.execute("CREATE INDEX idx_note_owner_updated ON learning_note(user_id,deleted,update_time,id)");
   st.execute("CREATE TABLE IF NOT EXISTS learning_note_version (id BIGINT AUTO_INCREMENT PRIMARY KEY,note_id BIGINT NOT NULL COMMENT '所属笔记',user_id BIGINT NOT NULL COMMENT '修改者及所有者',revision BIGINT NOT NULL COMMENT '修订号',title VARCHAR(200),content LONGTEXT,tags VARCHAR(2000),source_title VARCHAR(500),chapter_id BIGINT,path_id BIGINT,status VARCHAR(20),note_deleted INT NOT NULL DEFAULT 0,reason VARCHAR(40) COMMENT '版本来源',create_time TIMESTAMP NOT NULL,UNIQUE(note_id,revision))");
   Set<String> versionColumns=new HashSet<>();
   try(var ps=c.prepareStatement("SELECT * FROM learning_note_version WHERE 1=0");var rs=ps.executeQuery()){for(int i=1;i<=rs.getMetaData().getColumnCount();i++)versionColumns.add(rs.getMetaData().getColumnName(i).toLowerCase(Locale.ROOT));}
   if(!versionColumns.contains("note_deleted")){st.execute("ALTER TABLE learning_note_version ADD COLUMN note_deleted INT NOT NULL DEFAULT 0 COMMENT '笔记在该版本的删除状态'");if(versionColumns.contains("deleted"))st.execute("UPDATE learning_note_version SET note_deleted=deleted");}
   for(var entry:java.util.Map.of("folder_id","BIGINT COMMENT '历史文件夹'","favorite","BOOLEAN NOT NULL DEFAULT FALSE COMMENT '历史收藏状态'","source_doc_id","BIGINT COMMENT '历史来源文档'").entrySet())if(!versionColumns.contains(entry.getKey()))st.execute("ALTER TABLE learning_note_version ADD COLUMN "+entry.getKey()+" "+entry.getValue());
   st.execute("CREATE TABLE IF NOT EXISTS learning_note_folder(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL COMMENT '所属用户',parent_id BIGINT COMMENT '父文件夹',name VARCHAR(100) NOT NULL COMMENT '目录名',revision BIGINT NOT NULL DEFAULT 1 COMMENT '目录修订号',INDEX idx_note_folder_owner(user_id,parent_id))");
   st.execute("CREATE TABLE IF NOT EXISTS learning_note_tag(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,name VARCHAR(100) NOT NULL COMMENT '标签名',name_key VARCHAR(100) NOT NULL COMMENT '规范化唯一名',UNIQUE(user_id,name_key))");
   st.execute("CREATE TABLE IF NOT EXISTS learning_note_tag_ref(user_id BIGINT NOT NULL,note_id BIGINT NOT NULL,tag_id BIGINT NOT NULL,PRIMARY KEY(note_id,tag_id),INDEX idx_note_tag_owner(user_id,tag_id,note_id))");
   st.execute("CREATE TABLE IF NOT EXISTS learning_note_relation(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,note_id BIGINT NOT NULL,target_type VARCHAR(10) NOT NULL COMMENT 'DOC或NOTE',target_id BIGINT NOT NULL,UNIQUE(user_id,note_id,target_type,target_id))");
   // 为升级前的有效笔记补索引；不改变正文和原始标签字段。
   java.util.List<Object[]> legacy=new java.util.ArrayList<>();
   try(var rs=st.executeQuery("SELECT id,user_id,tags FROM learning_note WHERE deleted=0")){while(rs.next())legacy.add(new Object[]{rs.getLong(1),rs.getLong(2),rs.getString(3)});}
   for(var item:legacy)try{NoteTagIndex.sync(c,(Long)item[1],(Long)item[0],(String)item[2]);}catch(com.knowflow.exception.BusinessException e){org.slf4j.LoggerFactory.getLogger(NoteSchema.class).warn("旧笔记{}标签暂不能索引，原始字段保留：{}",item[0],e.getMessage());}
  }
 }
}
