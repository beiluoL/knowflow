package com.knowflow.learning.studypack;
import java.sql.*;
public final class StudyPackSchema {
 private StudyPackSchema(){}
 public static boolean exists(Connection c)throws SQLException{try(var rs=c.getMetaData().getTables(c.getCatalog(),c.getSchema(),null,new String[]{"TABLE"})){while(rs.next())if("study_pack_job".equalsIgnoreCase(rs.getString("TABLE_NAME")))return true;}return false;}
 public static void migrate(Connection c)throws SQLException{try(var st=c.createStatement()){
  st.execute("CREATE TABLE IF NOT EXISTS study_pack_job(id BIGINT AUTO_INCREMENT PRIMARY KEY,owner_id BIGINT NOT NULL,source_hash VARCHAR(64) NOT NULL,file_name VARCHAR(200) NOT NULL,source_bytes LONGBLOB NOT NULL,state VARCHAR(24) NOT NULL,revision BIGINT NOT NULL DEFAULT 1,doc_id BIGINT,parsed_hash VARCHAR(64),create_time TIMESTAMP(6) NOT NULL,update_time TIMESTAMP(6) NOT NULL,UNIQUE(owner_id,source_hash),INDEX idx_study_pack_owner(owner_id,id),INDEX idx_study_pack_doc(doc_id,owner_id))");
  boolean parsedHash=false;
  try(var rs=st.executeQuery("SELECT * FROM study_pack_job WHERE 1=0")){var meta=rs.getMetaData();for(int i=1;i<=meta.getColumnCount();i++)if("parsed_hash".equalsIgnoreCase(meta.getColumnName(i)))parsedHash=true;}
  if(!parsedHash)st.execute("ALTER TABLE study_pack_job ADD COLUMN parsed_hash VARCHAR(64)");
  st.execute("CREATE TABLE IF NOT EXISTS study_pack_step(job_id BIGINT NOT NULL,kind VARCHAR(16) NOT NULL,state VARCHAR(20) NOT NULL,resource_id BIGINT,resource_json LONGTEXT,draft_json LONGTEXT,error_message VARCHAR(500),run_count INT NOT NULL DEFAULT 0,elapsed_ms BIGINT,PRIMARY KEY(job_id,kind))");
  st.execute("CREATE TABLE IF NOT EXISTS study_pack_event(id BIGINT AUTO_INCREMENT PRIMARY KEY,job_id BIGINT NOT NULL,kind VARCHAR(16) NOT NULL,state VARCHAR(24) NOT NULL,resource_id BIGINT,elapsed_ms BIGINT,create_time TIMESTAMP(6) NOT NULL,INDEX idx_pack_event(job_id,id))");
 }}
}
