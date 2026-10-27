package com.knowflow.service;
import java.sql.*;
import java.util.*;
import com.knowflow.exception.BusinessException;
/** 标签仅为learning_note.tags派生索引，不保存第二份正文。 */
public final class NoteTagIndex {
 private NoteTagIndex(){}
 public static List<String> parse(String raw){
  if(raw==null||raw.isBlank())return List.of();
  List<String> values;
  if(raw.trim().startsWith("["))try{values=new com.fasterxml.jackson.databind.ObjectMapper().readValue(raw,new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){});}catch(Exception e){throw new BusinessException(400,"标签格式不正确");}
  else values=Arrays.asList(raw.split(","));
  var unique=new LinkedHashMap<String,String>();
  for(String v:values){if(v==null||v.isBlank())continue;String name=v.trim();if(name.length()>100)throw new BusinessException(400,"单个标签不能超过100字");unique.putIfAbsent(name.toLowerCase(Locale.ROOT),name);}
  if(unique.size()>50)throw new BusinessException(400,"每篇笔记最多50个标签");return List.copyOf(unique.values());
 }
 public static long ensure(Connection c,Long user,String name)throws SQLException{
  String key=name.toLowerCase(Locale.ROOT);Long found=find(c,user,key);if(found!=null)return found;
  try(var ps=c.prepareStatement("INSERT INTO learning_note_tag(user_id,name,name_key) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)){ps.setLong(1,user);ps.setString(2,name);ps.setString(3,key);try{ps.executeUpdate();}catch(SQLException e){if(e.getSQLState()==null||!e.getSQLState().startsWith("23"))throw e;Long race=find(c,user,key);if(race!=null)return race;throw e;}try(var keys=ps.getGeneratedKeys()){keys.next();return keys.getLong(1);}}
 }
 private static Long find(Connection c,Long user,String key)throws SQLException{try(var ps=c.prepareStatement("SELECT id FROM learning_note_tag WHERE user_id=? AND name_key=? FOR UPDATE")){ps.setLong(1,user);ps.setString(2,key);try(var rs=ps.executeQuery()){return rs.next()?rs.getLong(1):null;}}}
 public static void sync(Connection c,Long user,Long note,String raw)throws SQLException{
  var names=parse(raw);try(var ps=c.prepareStatement("DELETE FROM learning_note_tag_ref WHERE user_id=? AND note_id=?")){ps.setLong(1,user);ps.setLong(2,note);ps.executeUpdate();}
  for(String name:names){long tag=ensure(c,user,name);try(var ps=c.prepareStatement("INSERT INTO learning_note_tag_ref(user_id,note_id,tag_id) VALUES(?,?,?)")){ps.setLong(1,user);ps.setLong(2,note);ps.setLong(3,tag);ps.executeUpdate();}}
 }
}
