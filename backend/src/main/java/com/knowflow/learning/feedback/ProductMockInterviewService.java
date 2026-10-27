package com.knowflow.learning.feedback;
import com.knowflow.exception.BusinessException;
import com.knowflow.interview.radar.*;
import com.knowflow.interview.v2.InterviewV2Service;
import com.knowflow.learning.actions.LearningWriteGuard;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor
@ConditionalOnProperty(name={"knowflow.product.interview-learning.enabled","knowflow.product.private-quiz.enabled","knowflow.product.tutor.enabled","knowflow.product.tutor-coaching.enabled"},havingValue="true")
public class ProductMockInterviewService {
 private final RadarInterviewBank bank;private final InterviewV2Service interviews;private final Environment environment;private final JdbcTemplate sql;private final LearningWriteGuard guard;
 public record Start(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,80}")String requestId,@NotBlank @Pattern(regexp="JAVA_BACKEND|JAVA_AI")String role,@NotBlank @Size(max=80)String topic,@Size(max=8)List<@NotNull @Positive Long> projectDocumentIds){}
 private boolean allowed(RadarInterviewBank.Question q){return q.eligible()&&("APPROVED".equals(q.reviewStatus())||(environment.acceptsProfiles(Profiles.of("product03"))&&environment.getProperty("knowflow.product.interview-learning.allow-synthetic-fixtures",Boolean.class,false)&&"interview-content/SYNTHETIC_PRODUCT03".equals(q.sourceNamespace())&&q.title().contains("合成测试")));}
 public List<Map<String,Object>> topics(long user){var grouped=new LinkedHashMap<String,List<RadarInterviewBank.Question>>();for(var q:bank.catalog(user))if(allowed(q))grouped.computeIfAbsent(q.domain(),k->new ArrayList<>()).add(q);return grouped.entrySet().stream().map(e->Map.<String,Object>of("topic",e.getKey(),"count",e.getValue().size(),"available",e.getValue().size()>=3,"syntheticFixture",e.getValue().stream().anyMatch(q->!"APPROVED".equals(q.reviewStatus())))).toList();}
 @Transactional public Map<String,Object> start(long user,Start input){guard.lock(user);var available=bank.catalog(user).stream().filter(this::allowed).filter(q->q.domain().equals(input.topic())).limit(3).toList();if(available.size()!=3)throw new BusinessException(409,"该专题需要三道已审核可用原题；待审正式资料不开放");
  var ids=available.stream().map(q->"RADAR-"+q.questionId()).toList();var pool=bank.tracks(user).stream().filter(t->t.code().equals(RadarInterviewBank.TRACK)).findFirst().orElseThrow(()->new BusinessException(409,"当前没有可用题源"));var questions=ids.stream().map(id->pool.questions().stream().filter(q->q.id().equals(id)).findFirst().orElseThrow(()->new BusinessException(409,"来源已失效"))).toList();
  String role=switch(input.role()){case "JAVA_BACKEND"->"高级Java后端开发（通用岗位模板）";case "JAVA_AI"->"Java+AI应用开发（通用岗位模板）";default->throw new BusinessException(400,"岗位方向无效");};
  var session=interviews.startCuratedRadar(user,new InterviewV2Service.Start(input.requestId(),RadarInterviewBank.domainTrack(input.topic()),3,"STRICT",role,20,1,"HARD",null,input.projectDocumentIds(),null),questions);long sid=((Number)session.get("sessionId")).longValue();
  var binding=sql.queryForList("SELECT source_review_status FROM interview_radar_session_binding WHERE session_id=? AND user_id=?",sid,user);String status=available.stream().allMatch(q->"APPROVED".equals(q.reviewStatus()))?"PRODUCT03_APPROVED_REFERENCE":"PRODUCT03_SYNTHETIC_PENDING";if(binding.isEmpty())sql.update("INSERT INTO interview_radar_session_binding(session_id,user_id,stage,interview_mode,source_review_status) VALUES(?,?,'MOCK','SPECIALTY',?)",sid,user,status);return session;
 }
}
