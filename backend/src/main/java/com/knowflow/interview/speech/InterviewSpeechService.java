package com.knowflow.interview.speech;
import com.knowflow.exception.BusinessException;
import com.knowflow.interview.service.MockInterviewService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** 只生成待确认转写，永不写答案、自评分、Evidence 或 Mastery。 */
@Service
public class InterviewSpeechService {
 private final JdbcTemplate jdbc;private final MockInterviewService sessions;private final InterviewAsrProvider provider;private final SpeechProperties config;private final TransactionTemplate tx;private final SpeechV2Admission admission;
 public InterviewSpeechService(JdbcTemplate jdbc,MockInterviewService sessions,InterviewAsrProvider provider,SpeechProperties config,PlatformTransactionManager manager,SpeechV2Admission admission){this.admission=admission;this.jdbc=jdbc;this.sessions=sessions;this.provider=provider;this.config=config;this.tx=new TransactionTemplate(manager);}
 public record SpeechTranscript(long id,String questionId,String status,String rawText,String error){}
 public Map<String,Object> capability(){return Map.of("enabled",provider.capability(),"supportedFormats",provider.supportedFormats(),"maxBytes",maxBytes(),"maxDurationSeconds",maxSeconds(),"reason",provider.capability()?"":"语音识别未启用或未配置，请使用文字输入");}
 private long maxBytes(){return Math.max(1024,Math.min(10*1024*1024,config.getMaxBytes()));}
 private int maxSeconds(){return Math.max(1,Math.min(120,config.getMaxDurationSeconds()));}
 public SpeechTranscript transcribe(Long user,Long sid,String question,String request,long claimedDuration,MultipartFile file){
  if(user==null)throw new BusinessException(401,"请先登录");
  var session=sessions.getOwn(user,sid);
  if(!"IN_PROGRESS".equals(session.getStatus()))throw new BusinessException(409,"面试已结束");
  if(question==null||session.getQuestions().stream().noneMatch(q->question.equals(q.getId())))throw new BusinessException(400,"题目不属于本场面试");
  if(request==null||!request.matches("[A-Za-z0-9_-]{8,80}")||claimedDuration<1||claimedDuration>(long)maxSeconds()*1000)throw new BusinessException(400,"录音请求参数无效");
  if(file==null||file.isEmpty())throw new BusinessException(400,"请选择音频");
  if(file.getSize()>maxBytes())throw new BusinessException(413,"音频文件超过限制");
  byte[] audio;try(var stream=file.getInputStream()){audio=stream.readNBytes((int)maxBytes()+1);}catch(java.io.IOException e){throw new BusinessException(400,"无法读取音频");}
  if(audio.length>maxBytes())throw new BusinessException(413,"音频文件超过限制");
  long actual=AudioValidation.durationMs(audio,file.getContentType(),maxSeconds());
  String hash=hash(sid+"\n"+question+"\n"+claimedDuration+"\n",audio);
  var previous=jdbc.queryForList("SELECT * FROM mock_interview_speech WHERE user_id=? AND request_id=?",user,request);
  if(!previous.isEmpty())return duplicate(previous.get(0),hash);
  if(!provider.capability())throw new BusinessException(503,"语音识别未启用或未配置，请使用文字输入");
  long id;try{id=tx.execute(status->{
   // 会话行锁与唯一键共同防止并发请求二次计费；事务在远端请求之前提交。
   if(jdbc.queryForList("SELECT id FROM mock_interview_session WHERE id=? AND user_id=? AND deleted=0 AND status='IN_PROGRESS' FOR UPDATE",Long.class,sid,user).isEmpty())throw new BusinessException(409,"面试已结束或不可用");
   var binding=admission.requireAllowed(user,sid,question);
   var key=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO mock_interview_speech(user_id,session_id,question_id,request_id,request_hash,status,duration_ms,claimed_duration_ms,provider,v2_version,v2_depth,v2_prompt_hash,created_at,updated_at) VALUES(?,?,?,?,?,'TRANSCRIBING',?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",new String[]{"id"});ps.setLong(1,user);ps.setLong(2,sid);ps.setString(3,question);ps.setString(4,request);ps.setString(5,hash);ps.setLong(6,actual);ps.setLong(7,claimedDuration);ps.setString(8,provider.auditName());ps.setObject(9,binding.version());ps.setObject(10,binding.depth());ps.setString(11,binding.promptHash());return ps;},key);return Objects.requireNonNull(key.getKey()).longValue();});
  }catch(org.springframework.dao.DuplicateKeyException e){return duplicate(jdbc.queryForList("SELECT * FROM mock_interview_speech WHERE user_id=? AND request_id=?",user,request).get(0),hash);}
  try{String raw=provider.transcribe(audio);jdbc.update("UPDATE mock_interview_speech SET status='TRANSCRIBED',raw_text=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='TRANSCRIBING'",raw,id);}
  catch(RuntimeException e){jdbc.update("UPDATE mock_interview_speech SET status='FAILED',error_message=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='TRANSCRIBING'","语音识别失败，请使用文字输入；如需重试请创建新录音请求",id);}
  finally{Arrays.fill(audio,(byte)0);}
  return row(jdbc.queryForMap("SELECT * FROM mock_interview_speech WHERE id=?",id));
 }
 private SpeechTranscript duplicate(Map<String,Object> record,String hash){if(!hash.equals(record.get("request_hash")))throw new BusinessException(409,"请求标识已用于不同录音");
  // 进程退出后的遗留任务只能标失败，绝不自动重发造成重复扣费。
  long id=((Number)record.get("id")).longValue();jdbc.update("UPDATE mock_interview_speech SET status='FAILED',error_message='转写任务已中断，请使用文字输入',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='TRANSCRIBING' AND updated_at<?",id,java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(180)));
  return row(jdbc.queryForMap("SELECT * FROM mock_interview_speech WHERE id=?",id));}
 private SpeechTranscript row(Map<String,Object> r){return new SpeechTranscript(((Number)r.get("id")).longValue(),(String)r.get("question_id"),(String)r.get("status"),(String)r.get("raw_text"),(String)r.get("error_message"));}
 private String hash(String context,byte[] bytes){try{var md=MessageDigest.getInstance("SHA-256");md.update(context.getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(md.digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
