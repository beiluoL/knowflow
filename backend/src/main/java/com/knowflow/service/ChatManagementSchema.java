package com.knowflow.service;
import java.sql.Connection;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Component;
import java.util.*;
/** Additive, idempotent migration for both configured database dialects. */
@Component @RequiredArgsConstructor
public class ChatManagementSchema {
 private final JdbcTemplate jdbc;
 public void initialize(){jdbc.execute((ConnectionCallback<Void>) connection->{migrate(connection);return null;});}
 public static void migrate(Connection connection) throws SQLException {
  Set<String> columns=columnsOf(connection,"chat_conversation");
  Set<String> messageColumns=columnsOf(connection,"chat_message");
  try(var stmt=connection.createStatement()){
   // 存量库可能只建了部分聊天表：缺表时交给基础脚本建表，这里只补列，不因缺表中断整个初始化。
   if(columns!=null)for(String column:List.of("pinned","archived"))if(!columns.contains(column))stmt.execute("ALTER TABLE chat_conversation ADD COLUMN "+column+" BOOLEAN NOT NULL DEFAULT FALSE");
   // 深度思考与执行轨迹：推理内容与最终回答分开存储，历史会话可还原时间线。
   if(messageColumns!=null){
    if(!messageColumns.contains("reasoning_content"))stmt.execute("ALTER TABLE chat_message ADD COLUMN reasoning_content TEXT");
    if(!messageColumns.contains("reasoning_kind"))stmt.execute("ALTER TABLE chat_message ADD COLUMN reasoning_kind VARCHAR(20)");
    if(!messageColumns.contains("reasoning_duration_ms"))stmt.execute("ALTER TABLE chat_message ADD COLUMN reasoning_duration_ms INT");
    if(!messageColumns.contains("trace_json"))stmt.execute("ALTER TABLE chat_message ADD COLUMN trace_json TEXT");
   }
   stmt.execute("CREATE TABLE IF NOT EXISTS chat_public_share (id BIGINT AUTO_INCREMENT PRIMARY KEY,conversation_id BIGINT NOT NULL,user_id BIGINT NOT NULL,token_hash VARCHAR(64) NOT NULL UNIQUE,snapshot_json LONGTEXT NOT NULL,created_at TIMESTAMP NOT NULL,expires_at TIMESTAMP NOT NULL,revoked BOOLEAN NOT NULL DEFAULT FALSE,INDEX idx_chat_share_owner(conversation_id,user_id))");
  }
 }
 /** 读取表现有列；表不存在返回 null（H2 42102 / MySQL 1146），其余 SQL 异常照常抛出。 */
 private static Set<String> columnsOf(Connection connection,String table) throws SQLException {
  try(var ps=connection.prepareStatement("SELECT * FROM "+table+" WHERE 1=0");var rs=ps.executeQuery()) {
   Set<String> columns=new HashSet<>();var meta=rs.getMetaData();
   for(int i=1;i<=meta.getColumnCount();i++)columns.add(meta.getColumnName(i).toLowerCase(Locale.ROOT));
   return columns;
  } catch (SQLException e) {
   if("42S02".equals(e.getSQLState())||e.getErrorCode()==42102||e.getErrorCode()==1146) return null;
   throw e;
  }
 }
}
