package com.knowflow.interview.service.generation;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
/** 独立数据库验证领取互斥和租约回收，不调用模型。 */
class GenerationLeaseTest {
 @Test void onlyOneOwnerCanClaimAndExpiredOwnerCannotCommit() {
  var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:lease_test;DB_CLOSE_DELAY=-1;MODE=MySQL","sa",""));
  var lease=new GenerationLease(jdbc);
  lease.initialize(); lease.ensure(1L);
  String first=lease.claim(1L);
  assertNotNull(first); assertNull(lease.claim(1L));
  jdbc.update("UPDATE interview_generation_lease SET lease_until=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) WHERE task_id=1");
  String second=lease.claim(1L); assertNotNull(second); assertNotEquals(first,second);
  assertFalse(lease.fence(1L,first)); assertTrue(lease.fence(1L,second));
 }
 @Test void concurrentClaimsAndBudgetAreDatabaseControlled() throws Exception {
  var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:lease_concurrent;DB_CLOSE_DELAY=-1;MODE=MySQL","sa",""));
  var lease=new GenerationLease(jdbc); lease.initialize(); lease.ensure(2L);
  var pool=new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
  pool.setCorePoolSize(2); pool.setMaxPoolSize(2); pool.setQueueCapacity(2); pool.initialize();
  var start=new java.util.concurrent.CountDownLatch(1);
  try {
   var a=pool.submit(()->{start.await();return lease.claim(2L);});
   var b=pool.submit(()->{start.await();return lease.claim(2L);});
   start.countDown();
   assertEquals(1,(a.get(5,java.util.concurrent.TimeUnit.SECONDS)==null?0:1)+(b.get(5,java.util.concurrent.TimeUnit.SECONDS)==null?0:1));
   for(int attempt=1;attempt<3;attempt++) {
    jdbc.update("UPDATE interview_generation_lease SET lease_until=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) WHERE task_id=2");
    assertNotNull(lease.claim(2L));
   }
   lease.ensure(2L);
   jdbc.update("UPDATE interview_generation_lease SET lease_until=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) WHERE task_id=2");
   assertNull(lease.claim(2L));
   assertEquals(3,jdbc.queryForObject("SELECT attempt_count FROM interview_generation_lease WHERE task_id=2",Integer.class));
  } finally {pool.shutdown();}
 }
 @Test void retryAfterAndDeadlineCannotBeBypassedByAnotherWorker() {
  var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:lease_retry_budget;DB_CLOSE_DELAY=-1;MODE=MySQL","sa",""));
  var lease=new GenerationLease(jdbc);lease.initialize();lease.ensure(3L);
  String token=lease.claim(3L);
  assertFalse(lease.retry(3L,"foreign-worker","RATE_LIMITED",60000L));
  assertTrue(lease.retry(3L,token,"RATE_LIMITED",60000L));
  assertNull(lease.claim(3L));
  assertTrue(jdbc.queryForObject("SELECT next_retry_at>CURRENT_TIMESTAMP FROM interview_generation_lease WHERE task_id=3",Boolean.class));
  jdbc.update("UPDATE interview_generation_lease SET next_retry_at=NULL,deadline=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) WHERE task_id=3");
  assertNull(lease.claim(3L));
 }
}
