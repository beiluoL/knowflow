package com.knowflow.interview.v2;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Component;
/** 以真实V2关联行识别新会话，而不是靠可兼容的BASIC模式名称；旧库缺表时不破坏旧客户端。 */
@Component
public class InterviewV2Guard {
 private final JdbcTemplate jdbc;
 public InterviewV2Guard(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public boolean isV2(Long session){
  Boolean exists=jdbc.execute((ConnectionCallback<Boolean>)c->{try(var tables=c.getMetaData().getTables(c.getCatalog(),c.getSchema(),null,null)){while(tables.next())if("mock_interview_v2_state".equalsIgnoreCase(tables.getString("TABLE_NAME")))return true;return false;}});
  return Boolean.TRUE.equals(exists)&&!jdbc.queryForList("SELECT session_id FROM mock_interview_v2_state WHERE session_id=?",Long.class,session).isEmpty();
 }
}
