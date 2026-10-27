package com.knowflow.learning.feedback;
import com.fasterxml.jackson.databind.*;import com.knowflow.exception.BusinessException;import com.knowflow.learning.tutor.TutorAssessmentService;import com.knowflow.learning.privatequiz.PrivateQuizService;import com.knowflow.learning.evidence.LearningEvidenceSources;import com.knowflow.service.NoteService;import com.knowflow.mapper.*;import com.knowflow.security.DocumentAccessPolicy;import lombok.RequiredArgsConstructor;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import java.util.*;
@Service @RequiredArgsConstructor @ConditionalOnProperty(name={"knowflow.product.interview-learning.enabled","knowflow.product.private-quiz.enabled","knowflow.product.tutor.enabled","knowflow.product.tutor-coaching.enabled"},havingValue="true")
public class FeedbackSourceService {
 private final JdbcTemplate sql;private final ObjectMapper json;private final TutorAssessmentService assessments;private final PrivateQuizService quizzes;private final LearningEvidenceSources evidence;private final NoteService notes;private final DocDocumentMapper docs;private final QuizQuestionMapper questions;private final DocumentAccessPolicy access;
 public record Source(String type,long id,int questionIndex,String question,String answer,String basis,JsonNode evaluation,Long noteId,Long conversationId,Long sessionId,String version){}
 private long n(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
 public Source read(long user,String type,long id,int index){var s=readForRetest(user,type,id,index);int active=sql.queryForObject("SELECT COUNT(*) FROM learning_interview_retest p JOIN mock_interview_v2_state v ON v.session_id=p.retest_session JOIN mock_interview_session m ON m.id=v.session_id AND m.deleted=0 WHERE p.owner_id=? AND v.state<>'COMPLETED' AND ((?='INTERVIEW_TURN' AND p.baseline_turn=?) OR p.note_id=?)",Integer.class,user,type,id,s.noteId());if(active>0||sameQuestionActive(user,s))throw new BusinessException(409,"同题闭卷复测进行中，参考、错因与变式暂时收起；请先完成复测");return s;}
 private boolean sameQuestionActive(long user,Source source){
  if(!"INTERVIEW_TURN".equals(source.type()))return false;
  String qid=sql.queryForObject("SELECT question_id FROM mock_interview_v2_turn WHERE id=? AND user_id=?",String.class,source.id(),user);
  var active=sql.queryForList("SELECT m.questions_json FROM mock_interview_v2_state v JOIN mock_interview_session m ON m.id=v.session_id AND m.deleted=0 WHERE v.user_id=? AND v.mode='STRICT' AND v.state<>'COMPLETED' AND (EXISTS(SELECT 1 FROM learning_interview_retest p WHERE p.retest_session=v.session_id AND p.owner_id=v.user_id) OR EXISTS(SELECT 1 FROM interview_radar_session_binding b WHERE b.session_id=v.session_id AND b.user_id=v.user_id AND b.source_review_status LIKE 'PRODUCT03_%'))",user);
  for(var row:active)try{for(var q:json.readTree(row.get("questions_json").toString()))if(qid.equals(q.path("id").asText()))return true;}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new BusinessException(500,"面试题版快照损坏");}return false;
 }
 // Package-private admission for the retest orchestrator, which suppresses all reference-bearing fields itself.
 Source readForRetest(long user,String type,long id,int index){
  if(id<1||index<0||index>2||type==null)throw new BusinessException(400,"作答来源参数无效");
  if("TUTOR_ASSESSMENT".equals(type)){
   var rows=sql.queryForList("SELECT conversation_id,source_revision FROM tutor_assessment WHERE id=? AND owner_id=?",id,user);if(rows.isEmpty())throw new BusinessException(404,"复述来源不存在");long conv=n(rows.get(0),"conversation_id");var result=assessments.list(conv,user).stream().filter(r->Objects.equals(r.get("id"),id)).findFirst().orElseThrow(()->new BusinessException(404,"复述来源不存在"));if(!"SUCCEEDED".equals(result.get("state")))throw new BusinessException(409,"请先完成真实评价");var refs=json.valueToTree(result.get("sourceReferences"));long note=refs.get(0).path("id").asLong(),revision=n(rows.get(0),"source_revision");
   var frozen=sql.queryForList("SELECT source_doc_id FROM learning_note_version WHERE note_id=? AND user_id=? AND revision=?",note,user,revision);String basis;
   if(frozen.isEmpty()){var current=notes.detail(note,user,false);if(!Objects.equals(current.getRevision(),revision))throw new BusinessException(409,"历史来源无法核定");basis=current.getContent();}
   else{if(frozen.get(0).get("source_doc_id")!=null)access.requireRead(docs.selectById(((Number)frozen.get(0).get("source_doc_id")).longValue()),user);basis=sql.queryForObject("SELECT content FROM learning_note_version WHERE note_id=? AND user_id=? AND revision=?",String.class,note,user,revision);}
   return new Source(type,id,index,Objects.toString(result.get("topic")),Objects.toString(result.get("originalText")),basis,json.valueToTree(result.get("result")),note,conv,null,"NOTE:"+note+":"+revision);
  }
  if("PRIVATE_ATTEMPT".equals(type)){
   var attempt=json.valueToTree(quizzes.getAttempt(user,id));if(!"SUBMITTED".equals(attempt.path("state").asText()))throw new BusinessException(409,"请先交卷");if(index>=attempt.path("items").size())throw new BusinessException(400,"题目位置无效");var item=attempt.path("items").get(index);var q=item.path("question");var quiz=quizzes.detail(user,attempt.path("quizId").asLong());return new Source(type,id,index,q.path("content").asText(),item.path("userAnswer").asText(),q.path("explanation").asText()+"\n题版答案："+q.path("answer").asText(),item,((Number)quiz.get("noteId")).longValue(),null,null,"PRIVATE:"+attempt.path("quizId").asLong()+":"+attempt.path("version").asLong());
  }
  if("INTERVIEW_TURN".equals(type)){
   var source=evidence.read(user,type,id,false);var d=source.data();var snap=json.valueToTree(d.get("questionSnapshot"));String key=snap.path("id").asText();
   if(key.matches("(?:RADAR|STUDIO)-[0-9]+")){long qid=Long.parseLong(key.substring(key.indexOf('-')+1));var q=questions.selectById(qid);if(q==null||!access.canReadCategory(q.getCategoryId(),user))throw new BusinessException(403,"原题来源不可读");if(q.getDocId()!=null)access.requireRead(docs.selectById(q.getDocId()),user);}
   var basis=json.valueToTree(d.get("gradingBasis"));if(basis.isNull()||basis.isMissingNode())throw new BusinessException(409,"原面试没有有效AI评价，不编造错因");
   var plans=sql.queryForList("SELECT note_id FROM learning_interview_retest WHERE baseline_turn=? AND owner_id=?",Long.class,id,user);Long note=plans.isEmpty()?null:plans.get(0);if(note!=null)notes.detail(note,user,false);
   return new Source(type,id,index,Objects.toString(d.get("prompt"),""),Objects.toString(d.get("answer"),""),snap.path("referencePoints").toString(),basis,note,null,((Number)d.get("sessionId")).longValue(),"INTERVIEW:"+source.resourceKey()+":"+Objects.hash(snap.toString()));
  }
  throw new BusinessException(400,"不支持的作答来源");
 }
}
