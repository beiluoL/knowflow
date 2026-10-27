package com.knowflow.interview.speech;

import com.knowflow.exception.BusinessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.Set;

/** 语音准入复用 V2 当前题目与状态；基础会话没有 V2 行时继续兼容。 */
@Component
public class SpeechV2Admission {
 public record Binding(Long version,Integer depth,String promptHash){}
 private final JdbcTemplate jdbc;
 public SpeechV2Admission(JdbcTemplate jdbc){this.jdbc=jdbc;}
 /** 调用者必须先锁定父会话，并在同一个事务内预留语音请求。 */
 public Binding requireAllowed(Long user,Long session,String question){
  boolean exists=Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection->{
   // 只允许“确实没有 V2 表”兼容，连接/权限/列错误必须原样传播。
   try(var tables=connection.getMetaData().getTables(connection.getCatalog(),connection.getSchema(),null,null)){
    while(tables.next())if("mock_interview_v2_state".equalsIgnoreCase(tables.getString("TABLE_NAME")))return true;
    return false;
   }
  }));
  if(!exists)return new Binding(null,null,null);
  var rows=jdbc.queryForList("SELECT user_id,state,current_question_id,version,followup_depth,current_prompt FROM mock_interview_v2_state WHERE session_id=? FOR UPDATE",session);
  if(rows.isEmpty())return new Binding(null,null,null);
  var state=rows.get(0);
  if(((Number)state.get("user_id")).longValue()!=user)throw new BusinessException(404,"面试记录不存在");
  if(!Set.of("ASKING","ANSWERING","FOLLOW_UP").contains(state.get("state")))throw new BusinessException(409,"当前面试状态不接受语音录入");
  if(!question.equals(state.get("current_question_id")))throw new BusinessException(409,"语音题目与当前面试题目不一致");
  return new Binding(((Number)state.get("version")).longValue(),((Number)state.get("followup_depth")).intValue(),promptHash((String)state.get("current_prompt")));
 }
 /** 对服务端完整题干按UTF-8做SHA-256，禁止trim或JSON序列化以免身份漂移。 */
 public static String promptHash(String prompt){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
