package com.knowflow.interview.generation;

import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** 数据库租约与提交栅栏；不持有网络调用事务，不依赖实例内锁。 */
final class GenerationLease {
 static final int MAX_ATTEMPTS=3;
 static final int LEASE_SECONDS=180;
 private final JdbcTemplate jdbc;
 GenerationLease(JdbcTemplate jdbc) { this.jdbc=jdbc; }
 void initialize() {
  jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
   String dialect=connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2")?"h2":"mysql";
   org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ClassPathResource("db/"+dialect+"/migration/V20261102__interview_generation_lease.sql"));
   return null;
  });
 }
 void ensure(long id) {
  try { jdbc.update("INSERT INTO interview_generation_lease(task_id,deadline) VALUES(?,?)",id,Timestamp.from(Instant.now().plusSeconds(900))); }
  catch(org.springframework.dao.DuplicateKeyException existing) { /* 已持久化的预算不可重置。 */ }
 }
 String claim(long id) {
  String token=UUID.randomUUID().toString();
  int count=jdbc.update("UPDATE interview_generation_lease SET lease_owner=?,lease_until=?,attempt_count=attempt_count+1,version=version+1 WHERE task_id=? AND attempt_count<max_attempts AND deadline>CURRENT_TIMESTAMP AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP) AND (next_retry_at IS NULL OR next_retry_at<=CURRENT_TIMESTAMP)",token,Timestamp.from(Instant.now().plusSeconds(LEASE_SECONDS)),id);
  return count==1?token:null;
 }
 boolean fence(long id,String token) {
  return jdbc.update("UPDATE interview_generation_lease SET version=version+1 WHERE task_id=? AND lease_owner=? AND lease_until>CURRENT_TIMESTAMP AND deadline>CURRENT_TIMESTAMP",id,token)==1;
 }
 boolean retry(long id,String token,String code,Long retryAfter) {
  Integer attempt=jdbc.queryForObject("SELECT attempt_count FROM interview_generation_lease WHERE task_id=?",Integer.class,id);
  long delay=Math.max(retryAfter==null?0:Math.min(retryAfter,900000),Math.min(60000,1000L*(1L<<Math.min(attempt,6)))+ThreadLocalRandom.current().nextLong(1000));
  return jdbc.update("UPDATE interview_generation_lease SET lease_owner=NULL,lease_until=NULL,next_retry_at=?,last_error_code=? WHERE task_id=? AND lease_owner=? AND attempt_count<max_attempts AND deadline>?",Timestamp.from(Instant.now().plusMillis(delay)),code,id,token,Timestamp.from(Instant.now().plusMillis(delay)))==1;
 }
}
