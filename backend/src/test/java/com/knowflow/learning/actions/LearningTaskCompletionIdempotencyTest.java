package com.knowflow.learning.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.*;
import com.knowflow.mapper.*;
import com.knowflow.reward.WalletService;
import com.knowflow.service.LearningService;
import com.knowflow.support.FakeAiConfiguration;
import com.knowflow.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 行锁、事件和主事务一起验证；每个用户/任务均为本类独立H2内的真实数据。 */
@SpringBootTest(properties="knowflow.datasource.h2.url=jdbc:h2:mem:phaseg_idempotency;DB_CLOSE_DELAY=-1;MODE=MySQL")
@AutoConfigureMockMvc @Import(FakeAiConfiguration.class)
class LearningTaskCompletionIdempotencyTest {
 @Autowired LearningService learning;@Autowired LearningTaskMapper tasks;@Autowired SysUserMapper users;
 @org.springframework.boot.test.mock.mockito.SpyBean JdbcTemplate db;@Autowired MockMvc http;@Autowired JwtUtils jwt;@Autowired ObjectMapper json;
 @Autowired WalletService wallets;@Autowired DailyLearningPlanService plans;
 record Fixture(long user,long task){}
 long user(){var u=new SysUser();u.setUsername("g2-"+UUID.randomUUID());u.setPassword("test-only");u.setRole("USER");users.insert(u);wallets.getOrCreate(u.getId());return u.getId();}
 Fixture fixture(){long uid=user();var t=new LearningTask();t.setTitle("G2学习活动");t.setType("REVIEW");t.setStatus(0);learning.createTask(t,uid);return new Fixture(uid,t.getId());}
 void write(Fixture f,int state,Long user,String role,int code)throws Exception{var r=put("/api/learning/tasks/"+f.task+"/status").param("source","LEARNING").param("status",state+"");if(user!=null)r.header("Authorization","Bearer "+jwt.generateToken(user,"g2",role));http.perform(r).andExpect(status().is(code)).andExpect(jsonPath("$.code").value(code));}
 long events(Fixture f){return db.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='LEARNING_TASK_COMPLETE' AND resource_type='TASK' AND resource_id=?",Long.class,f.user,f.task);}
 Map<String,Object> snapshot(Fixture f){var s=new LinkedHashMap<String,Object>();s.put("task",db.queryForMap("SELECT status,update_time FROM learning_task WHERE id=?",f.task));s.put("events",events(f));s.put("ledger",db.queryForObject("SELECT COUNT(*) FROM reward_ledger WHERE user_id=?",Long.class,f.user));s.put("wallet",db.queryForMap("SELECT xp,coin FROM wallet WHERE user_id=?",f.user));s.put("mastery",db.queryForObject("SELECT COUNT(*) FROM knowledge_mastery WHERE user_id=?",Long.class,f.user));s.put("proof",db.queryForObject("SELECT COUNT(*) FROM learning_task_evidence WHERE task_id IN(SELECT id FROM task WHERE user_id=?)",Long.class,f.user));return s;}
 void oneReward(Fixture f){assertEquals(10,db.queryForObject("SELECT xp FROM wallet WHERE user_id=?",Integer.class,f.user));assertEquals(2,db.queryForObject("SELECT coin FROM wallet WHERE user_id=?",Integer.class,f.user));assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM reward_ledger WHERE user_id=? AND reason='LEARNING_TASK_COMPLETE'",Integer.class,f.user));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM knowledge_mastery WHERE user_id=?",Integer.class,f.user));}
 // H2的默认隔离不复现MySQL RR旧快照；此查询合同配合mysql57-current.json中的双事务复现。
 @Test void rewardEligibilityUsesCurrentReadInsteadOfSnapshotCount() {
  var f=fixture();learning.updateTaskStatus(f.task,f.user,1);assertEquals(1,events(f));oneReward(f);
  org.mockito.Mockito.verify(db).queryForList("SELECT id FROM learning_event WHERE user_id=? AND event_type='LEARNING_TASK_COMPLETE' AND resource_type='TASK' AND resource_id=? LIMIT 1 FOR UPDATE",f.user,f.task);
 }
 @Test void firstCompletionAndSameStateRetryDoNotChangeTimestampOrEvents()throws Exception{var f=fixture();write(f,1,f.user,"USER",200);assertEquals(1,events(f));oneReward(f);var saved=snapshot(f);write(f,1,f.user,"USER",200);assertEquals(saved,snapshot(f));}
 @Test void tenSequentialCompletionsProduceOneEventAndOneReward()throws Exception{var f=fixture();for(int i=0;i<10;i++)write(f,1,f.user,"USER",200);assertEquals(1,events(f));oneReward(f);}
 @Test void tenBarrierConcurrentCompletionsProduceOneEventAndOneReward()throws Exception{var f=fixture();var pool=Executors.newFixedThreadPool(10);var ready=new CountDownLatch(10);var start=new CountDownLatch(1);try{var futures=new ArrayList<Future<?>>();for(int i=0;i<10;i++)futures.add(pool.submit(()->{ready.countDown();try{if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("barrier timeout");learning.updateTaskStatus(f.task,f.user,1);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}));assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();for(var future:futures)future.get(30,TimeUnit.SECONDS);}finally{pool.shutdownNow();}assertEquals(1,events(f));assertEquals(1,tasks.selectById(f.task).getStatus());oneReward(f);}
 @Test void differentTasksForSameOwnerAlsoSerializeRewardEligibility()throws Exception{
  long uid=user();var ids=new ArrayList<Long>();for(int i=0;i<5;i++){var t=new LearningTask();t.setTitle("不同并发任务"+i);t.setStatus(0);learning.createTask(t,uid);ids.add(t.getId());}
  var barrier=new CyclicBarrier(5);var pool=Executors.newFixedThreadPool(5);try{var futures=new ArrayList<Future<?>>();for(long id:ids)futures.add(pool.submit(()->{try{barrier.await(10,TimeUnit.SECONDS);learning.updateTaskStatus(id,uid,1);}catch(Exception e){throw new IllegalStateException(e);}}));for(var f:futures)f.get(30,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
  assertEquals(5,db.queryForObject("SELECT COUNT(*) FROM learning_event WHERE user_id=? AND event_type='LEARNING_TASK_COMPLETE'",Integer.class,uid));assertEquals(50,db.queryForObject("SELECT xp FROM wallet WHERE user_id=?",Integer.class,uid));assertEquals(10,db.queryForObject("SELECT coin FROM wallet WHERE user_id=?",Integer.class,uid));
  // MySQL RR奖励资格锁定读会锁住事件范围，先复用原owner锁，避免不同任务同时持有gap lock；主事务与奖励独立事务各取一次。
  org.mockito.Mockito.verify(db,org.mockito.Mockito.times(10)).queryForList("SELECT user_id FROM learning_action_owner WHERE user_id=? FOR UPDATE",uid);
 }
 @Test void undoAndRecompleteRecordTransitionsWithoutAdditionalRewardOrMastery()throws Exception{var f=fixture();write(f,1,f.user,"USER",200);var wallet=db.queryForMap("SELECT xp,coin FROM wallet WHERE user_id=?",f.user);write(f,0,f.user,"USER",200);assertEquals(0,tasks.selectById(f.task).getStatus());assertEquals(1,events(f));write(f,1,f.user,"USER",200);assertEquals(2,events(f));assertEquals(wallet,db.queryForMap("SELECT xp,coin FROM wallet WHERE user_id=?",f.user));oneReward(f);var metas=db.queryForList("SELECT metadata FROM learning_event WHERE user_id=? AND event_type='LEARNING_TASK_COMPLETE' ORDER BY id",String.class,f.user);assertTrue(json.readTree(metas.get(0)).path("rewardEligible").asBoolean());assertFalse(json.readTree(metas.get(1)).path("rewardEligible").asBoolean(true));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM quiz_answer_record WHERE user_id=?",Integer.class,f.user));}
 @Test void missingDeletedAndPrivateOwnershipRetainHttpContract()throws Exception{var f=fixture();var before=snapshot(f);write(f,1,null,"USER",401);long other=user();write(f,1,other,"USER",403);write(f,1,other,"ADMIN",403);write(new Fixture(f.user,Long.MAX_VALUE),1,f.user,"USER",404);assertEquals(before,snapshot(f));tasks.deleteById(f.task);write(f,1,f.user,"USER",404);assertEquals(0,events(f));}
 @ParameterizedTest @ValueSource(ints={-1,2,99}) void invalidStatusesAre400AndWriteNothing(int value)throws Exception{var f=fixture();var before=snapshot(f);write(f,value,f.user,"USER",400);assertEquals(before,snapshot(f));}
 @Test void nullStatusInServiceIsRejectedWithoutWrite(){var f=fixture();var before=snapshot(f);var e=assertThrows(com.knowflow.exception.BusinessException.class,()->learning.updateTaskStatus(f.task,f.user,null));assertEquals(400,e.getCode());assertEquals(before,snapshot(f));}
 public static class RejectEventInsert implements org.h2.api.Trigger{public void fire(java.sql.Connection c,Object[] old,Object[] row)throws java.sql.SQLException{throw new java.sql.SQLException("phaseg isolated event failure");}}
 @Test void eventFailureRollsBackStatusAndRetrySucceeds()throws Exception{var f=fixture();var before=snapshot(f);db.execute("CREATE TRIGGER phaseg_event_failure BEFORE INSERT ON learning_event FOR EACH ROW CALL 'com.knowflow.learning.actions.LearningTaskCompletionIdempotencyTest$RejectEventInsert'");try{assertThrows(Exception.class,()->learning.updateTaskStatus(f.task,f.user,1));assertEquals(before,snapshot(f));}finally{db.execute("DROP TRIGGER phaseg_event_failure");}write(f,1,f.user,"USER",200);assertEquals(1,events(f));oneReward(f);}
 @Test void taskAndLearningNumericIdNeverShareLifecycle()throws Exception{var f=fixture();long id=f.task;while(db.queryForObject("SELECT COUNT(*) FROM task WHERE id=?",Integer.class,id)>0){f=fixture();id=f.task;}db.update("INSERT INTO task(id,user_id,parent_id,title,status,stage,deleted) VALUES(?,?,0,'同数字Todo',0,0,0)",id,f.user);write(f,1,f.user,"USER",200);assertEquals(0,db.queryForObject("SELECT status FROM task WHERE id=?",Integer.class,id));write(f,0,f.user,"USER",200);http.perform(put("/api/learning/tasks/"+id+"/status").param("source","TASK").param("status","1").header("Authorization","Bearer "+jwt.generateToken(f.user,"g2","USER"))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));assertEquals(0,tasks.selectById(id).getStatus());assertEquals(1,events(f));}
 @Test void chatLegacyStatusConfirmationUsesSameTransitionContract()throws Exception{var f=fixture();var draft=plans.previewStatus(f.user,f.task,"LEARNING",1,new PlanModels.Request());var c=new PlanModels.ConfirmRequest();c.setVersion(draft.version());c.setConfirmationToken(draft.confirmationToken());c.setIdempotencyKey(UUID.randomUUID().toString());c.setTasks(draft.tasks().stream().map(i->new PlanModels.EditedItem(i.key(),i.title(),i.estimatedMinutes(),i.priority())).toList());plans.confirm(f.user,draft.planId(),c);assertEquals(1,events(f));write(f,1,f.user,"USER",200);assertEquals(1,events(f));oneReward(f);}
 @Test void writeSanitizedTransitionReconciliationEvidence()throws Exception{var f=fixture();for(int i=0;i<10;i++)write(f,1,f.user,"USER",200);write(f,0,f.user,"USER",200);write(f,1,f.user,"USER",200);assertEquals(2,events(f));oneReward(f);var root=Path.of(System.getProperty("user.dir")).getParent();var out=root.resolve("docs/roadmap/evidence/phase-g/g2-database.json");Files.createDirectories(out.getParent());Files.writeString(out,json.writeValueAsString(Map.of("taskId",f.task,"snapshot",snapshot(f),"eventLinks",db.queryForList("SELECT id,event_type,resource_type,resource_id,metadata FROM learning_event WHERE user_id=? AND event_type='LEARNING_TASK_COMPLETE' ORDER BY id",f.user))));}
}
