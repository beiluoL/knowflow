package com.knowflow.learning.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import com.knowflow.learning.actions.PersonalLearningResourceScope;
import com.knowflow.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Read adapters over the original facts. No client-supplied grade, identity or knowledge mapping. */
@Component @RequiredArgsConstructor
public class LearningEvidenceSources {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PersonalLearningResourceScope scope;
    private final QuizQuestionMapper questions;
    private final DocDocumentMapper documents;
    private final KgEntityMapper knowledge;
    private final LearningFlashcardMapper cards;
    private final QuizAttemptProvenance provenance;
    public static final Set<String> TYPES = Set.of("QUIZ_ANSWER", "EXAM_ANSWER", "FLASHCARD_REVIEW", "INTERVIEW_TURN");
    public record Source(String type, long id, String resourceKey, long firstId, Object occurredAt, Map<String,Object> data, List<Long> knowledgeIds) {}

    public void validate(String type, long id) {
        if (type == null || !TYPES.contains(type) || id <= 0) throw new BusinessException(400, "证据来源或编号无效");
    }
    public Source read(long actor, String type, long id, boolean lock) {return read(actor,type,id,lock,true);}
    public Source readRequired(long actor,String type,long id){return read(actor,type,id,true,false);}
    private Source read(long actor, String type, long id, boolean lock,boolean includeStatistics) {
        validate(type,id);
        var source = switch(type) {
            case "QUIZ_ANSWER" -> quiz(actor,id,lock,includeStatistics);
            case "EXAM_ANSWER" -> exam(actor,id,lock);
            case "FLASHCARD_REVIEW" -> flashcard(actor,id,lock);
            case "INTERVIEW_TURN" -> interview(actor,id,lock);
            default -> throw new BusinessException(400,"证据来源无效");
        };
        var d=source.data();
        if("OBJECTIVE".equals(d.get("assessmentKind"))||"EXAM_ANSWER".equals(type)){
            if(!(d.get("score") instanceof Number score))throw new BusinessException(409,"原始评分尚未完成，请在原流程重试");
            if(!(d.get("maxScore") instanceof Number max)||max.doubleValue()<=0||score.doubleValue()<0||score.doubleValue()>max.doubleValue())throw new BusinessException(409,"原始评分量纲无效，请在原流程核对");
        }
        var snapshot=json.valueToTree(d.get("questionSnapshot"));var basis=json.valueToTree(d.get("gradingBasis"));
        d.put("ruleVersion",d.get("gradingRule"));d.put("rubricVersion",nullableText(basis,"rubricVersion"));d.put("modelVersion",nullableText(basis,"modelVersion"));d.put("modelIdentifier",nullableText(basis,"model"));d.put("promptVersion",nullableText(basis,"promptVersion"));d.put("questionVersion",snapshot.hasNonNull("version")?snapshot.get("version"):null);
        boolean visible="UNTIMED_ANSWER_VISIBLE_PRACTICE".equals(d.get("conditions"));
        d.put("answerExposed",visible?true:snapshot.has("answerExposed")?snapshot.get("answerExposed"):null);d.put("hintCount",snapshot.has("hintCount")?snapshot.get("hintCount"):null);d.put("answerMode",visible?"ANSWER_VISIBLE_PRACTICE":d.get("conditions"));
        d.put("assessmentStatus",d.get("score")!=null&&!"ACTIVITY_ONLY".equals(d.get("assessmentKind"))?"GRADED":"SELF_REPORTED".equals(d.get("assessmentKind"))?"SELF_REPORTED":"AI_SUGGESTION".equals(d.get("assessmentKind"))?"EVALUATED":"RECORDED");d.put("evaluatedAt",basis.hasNonNull("gradedAt")?basis.get("gradedAt").asText():null);
        d.putIfAbsent("latestQualifiedAttemptRef",null);d.putIfAbsent("bestScore",null);d.putIfAbsent("qualificationScope","UNVERIFIED_LEGACY_SOURCE");d.putIfAbsent("qualificationComplete",false);
        return source;
    }
    private Map<String,Object> row(String sql, long id, boolean lock) {
        var rows=jdbc.queryForList(sql+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty()) throw new BusinessException(404,"原始学习记录不存在");
        return rows.get(0);
    }
    private void owner(Map<String,Object> row,long actor) {
        if(number(row,"user_id")!=actor) throw new BusinessException(403,"无权读取他人的私人学习记录");
    }
    private boolean document(Long id,long actor) {
        var rows=jdbc.queryForList("SELECT id,category_id,status,deleted FROM doc_document WHERE id=? AND deleted=0",id);
        if(rows.isEmpty())return false;var row=rows.get(0);var doc=new com.knowflow.entity.DocDocument();doc.setId(id);doc.setCategoryId(row.get("category_id")==null?null:number(row,"category_id"));doc.setStatus(((Number)row.get("status")).intValue());doc.setDeleted(0);return scope.document(doc,actor);
    }
    private void question(long qid,long actor) {
        var rows=jdbc.queryForList("SELECT category_id,doc_id FROM quiz_question WHERE id=? AND deleted=0",qid);
        if(rows.isEmpty())throw new BusinessException(403,"学习来源已删除或阅读权限已变化");var q=rows.get(0);
        if(!scope.category(q.get("category_id")==null?null:number(q,"category_id"),actor)||q.get("doc_id")!=null&&!document(number(q,"doc_id"),actor))throw new BusinessException(403,"学习来源已删除或阅读权限已变化");
    }
    private void requireSnapshotScope(JsonNode snapshot,long actor) {
        if(snapshot.hasNonNull("categoryId")&&!scope.category(snapshot.path("categoryId").asLong(),actor)||snapshot.hasNonNull("docId")&&!document(snapshot.path("docId").asLong(),actor))throw new BusinessException(403,"原始快照来源阅读权限已变化");
    }
    public void requireFrozenScope(long actor,Map<String,Object> receipt){requireSnapshotScope(json.valueToTree(receipt.get("questionSnapshot")),actor);}
    /** Only authorization metadata, not answer text, grading JSON or complete source history. */
    public void requireAccessible(long actor,String type,long id) {
        validate(type,id);
        switch(type){
            case "QUIZ_ANSWER" -> {var r=row("SELECT id,user_id,question_id FROM quiz_answer_record WHERE id=? AND deleted=0",id,false);owner(r,actor);question(number(r,"question_id"),actor);}
            case "EXAM_ANSWER" -> {
                var r=row("SELECT a.user_id,e.paper_question_id,e.graded,e.score FROM exam_answer e JOIN exam_attempt a ON a.id=e.attempt_id WHERE e.id=? AND e.deleted=0 AND a.deleted=0",id,false);owner(r,actor);
                if(((Number)r.get("graded")).intValue()!=1||r.get("score")==null)throw new BusinessException(409,"原答卷尚未完成评分");
                var p=row("SELECT question_id,snapshot FROM exam_paper_question WHERE id=? AND deleted=0",number(r,"paper_question_id"),false);var snap=parse(p.get("snapshot"));
                if(p.get("question_id")!=null)question(number(p,"question_id"),actor);
                if(snap.hasNonNull("categoryId")&&!scope.category(snap.path("categoryId").asLong(),actor)||snap.hasNonNull("docId")&&!document(snap.path("docId").asLong(),actor))throw new BusinessException(403,"无权阅读原考试来源");
            }
            case "FLASHCARD_REVIEW" -> {
                var r=row("SELECT user_id,resource_id FROM learning_event WHERE id=? AND deleted=0 AND event_type='FLASHCARD_REVIEWED' AND resource_type='FLASHCARD'",id,false);owner(r,actor);
                var c=row("SELECT user_id,category_id,doc_id FROM learning_flashcard WHERE id=? AND deleted=0",number(r,"resource_id"),false);owner(c,actor);
                if(!scope.category(c.get("category_id")==null?null:number(c,"category_id"),actor)||c.get("doc_id")!=null&&!document(number(c,"doc_id"),actor))throw new BusinessException(403,"复习卡来源已不可访问");
            }
            case "INTERVIEW_TURN" -> {
                var r=row("SELECT user_id,session_id FROM mock_interview_v2_turn WHERE id=?",id,false);owner(r,actor);var session=row("SELECT user_id FROM mock_interview_session WHERE id=? AND deleted=0",number(r,"session_id"),false);owner(session,actor);
                var state=row("SELECT state,project_json FROM mock_interview_v2_state WHERE session_id=?",number(r,"session_id"),false);
                if(!"COMPLETED".equals(state.get("state")))throw new BusinessException(409,"面试尚未完成");
                for(var e:parse(state.get("project_json")).path("evidence"))if(e.path("documentId").isIntegralNumber()&&!document(e.path("documentId").asLong(),actor))throw new BusinessException(403,"项目资料阅读权限已变化");
            }
        }
        for(long kid:jdbc.queryForList("SELECT knowledge_id FROM learning_evidence_knowledge WHERE user_id=? AND source_type=? AND source_id=?",Long.class,actor,type,id))requireKnowledge(kid,actor);
    }
    public void requireKnowledge(long id,long actor) {
        var k=knowledge.selectById(id);
        if(k==null)throw new BusinessException(404,"知识点不存在");
        if(!scope.category(k.getCategoryId(),actor)||k.getDocId()!=null&&!document(k.getDocId(),actor))
            throw new BusinessException(403,"无权访问该知识点");
    }
    private List<Long> knowledge(JsonNode snapshot,long actor) {
        var ids=new TreeSet<Long>();
        var values=snapshot.path("knowledgeIds");
        if(values.isArray()) for(var value:values) if(value.isIntegralNumber()&&value.canConvertToLong()&&value.longValue()>0) ids.add(value.longValue());
        for(long kid:ids)requireKnowledge(kid,actor);
        return List.copyOf(ids);
    }
    private Source quiz(long actor,long id,boolean lock,boolean includeStatistics) {
        var r=row("SELECT * FROM quiz_answer_record WHERE id=? AND deleted=0",id,lock);owner(r,actor);
        long qid=number(r,"question_id");question(qid,actor);
        var snap=parse(r.get("question_snapshot"));
        var d=base(r,"user_answer",snap.isMissingNode()?"LEGACY_UNAVAILABLE":"FROZEN");
        d.put("questionSnapshot",snap.isMissingNode()?null:snap);
        d.put("questionFingerprint",nullableText(snap,"contentHash"));d.put("gradingRule",nullableText(snap,"gradingRule"));
        d.put("conditions",nullableText(snap,"conditions"));d.put("score",r.get("score"));d.put("maxScore",100);
        d.put("assessmentKind", "QUIZ_OBJECTIVE_V1".equals(nullableText(snap,"gradingRule")) ? "OBJECTIVE" : "ACTIVITY_ONLY");d.put("gradingBasis",snap.isMissingNode()?null:snap.path("answer"));
        d.put("baselineSourceId",snap.has("baselineAnswerRecordId")?snap.path("baselineAnswerRecordId").longValue():null);
        d.put("correct",r.get("is_correct"));
        long first=firstKnown("SELECT id,create_time AS occurred_at,deleted FROM quiz_answer_record WHERE user_id=? AND question_id=?",new Object[]{actor,qid},"QUIZ_ANSWER",d);
        d.put("firstKnownOrdering","DATABASE_TIME_ID_TIEBREAKER");
        if(includeStatistics)first=provenance.firstKnownId(actor,qid,first,d);
        var acl=new HashMap<List<JsonNode>,Boolean>();
        provenance.describe(actor,qid,id,snap,d,candidate->acl.computeIfAbsent(List.of(candidate.path("knowledgeIds"),candidate.path("categoryId"),candidate.path("docId")),key->{try{requireSnapshotScope(candidate,actor);knowledge(candidate,actor);return true;}catch(BusinessException e){if(Set.of(403,404).contains(e.getCode()))return false;throw e;}}),includeStatistics);
        return new Source("QUIZ_ANSWER",id,"QUIZ:"+qid,first,snap.path("answeredAt").isTextual()?java.sql.Timestamp.valueOf(java.time.LocalDateTime.parse(snap.path("answeredAt").asText())):r.get("create_time"),d,knowledge(snap,actor));
    }
    private Source exam(long actor,long id,boolean lock) {
        var r=row("SELECT * FROM exam_answer WHERE id=? AND deleted=0",id,lock);
        var a=row("SELECT * FROM exam_attempt WHERE id=? AND deleted=0",number(r,"attempt_id"),false);owner(a,actor);
        var pq=row("SELECT * FROM exam_paper_question WHERE id=? AND deleted=0",number(r,"paper_question_id"),false);
        if(!Integer.valueOf(1).equals(((Number)r.get("graded")).intValue()) || r.get("score")==null)throw new BusinessException(409,"原答卷尚未完成评分，请在原考试流程重试");
        var snap=parse(pq.get("snapshot"));
        if(snap.hasNonNull("categoryId")&&!scope.category(snap.path("categoryId").longValue(),actor))throw new BusinessException(403,"无权阅读原考试来源");
        if(snap.hasNonNull("docId")&&!document(snap.path("docId").longValue(),actor))throw new BusinessException(403,"无权阅读原考试来源");
        String source=pq.get("question_id")==null?"PERSONAL_PAPER":"QUIZ";
        if("QUIZ".equals(source)&&pq.get("question_id") instanceof Number n)question(n.longValue(),actor);
        if(!Set.of("RULE","AI","JUDGE").contains(Objects.toString(r.get("grade_type"),"")))throw new BusinessException(409,"原答卷评分类型尚未确认");
        var d=base(r,"user_answer",snap.isMissingNode()?"LEGACY_UNAVAILABLE":"FROZEN");
        d.put("questionSnapshot",snap.isMissingNode()?null:snap);d.put("gradingBasis",parse(r.get("grade_detail")));
        d.put("score",r.get("score"));d.put("maxScore",r.get("max_score"));d.put("assessmentKind","RULE".equals(r.get("grade_type"))?"OBJECTIVE":"AI".equals(r.get("grade_type"))?"AI_SUGGESTION":"JUDGE");
        d.put("gradingRule",r.get("grade_type")+":"+Objects.toString(pq.get("scoring_rule"),"UNKNOWN"));d.put("conditions",a.get("mode"));d.put("questionFingerprint",snap.isMissingNode()?null:encode(snap));
        d.put("attemptId",a.get("id"));d.put("baselineSourceId",null);
        String key="EXAM:"+source+":"+Objects.toString(pq.get("question_id"),"PAPER_QUESTION_"+pq.get("id"));
        long first=firstKnown("SELECT e.id,COALESCE(a.submit_time,e.create_time) AS occurred_at,CASE WHEN e.deleted=1 OR a.deleted=1 OR p.deleted=1 THEN 1 ELSE 0 END AS deleted FROM exam_answer e JOIN exam_attempt a ON a.id=e.attempt_id JOIN exam_paper_question p ON p.id=e.paper_question_id WHERE a.user_id=? AND "+(pq.get("question_id")==null?"p.id=?":"p.question_id=?"),new Object[]{actor,pq.get("question_id")==null?pq.get("id"):pq.get("question_id")},"EXAM_ANSWER",d);
        return new Source("EXAM_ANSWER",id,key,first,a.get("submit_time")==null?r.get("create_time"):a.get("submit_time"),d,knowledge(snap,actor));
    }
    private Source flashcard(long actor,long id,boolean lock) {
        var r=row("SELECT * FROM learning_event WHERE id=? AND deleted=0 AND event_type='FLASHCARD_REVIEWED' AND resource_type='FLASHCARD'",id,lock);owner(r,actor);
        long cardId=number(r,"resource_id");var c=cards.selectById(cardId);
        if(c==null||!Objects.equals(c.getUserId(),actor)||!scope.category(c.getCategoryId(),actor)||c.getDocId()!=null&&!document(c.getDocId(),actor))throw new BusinessException(403,"复习卡来源已不可访问");
        var meta=parse(r.get("metadata"));var d=base(r,null,"FROZEN");
        d.put("gradingBasis",meta);d.put("assessmentKind","SELF_REPORTED");d.put("selfRating",meta.get("rating"));d.put("score",null);d.put("maxScore",null);
        d.put("gradingRule",nullableText(meta,"algorithm"));d.put("conditions","FSRS_SELF_RECALL");d.put("questionFingerprint",null);d.put("baselineSourceId",null);
        var kids=json.createObjectNode();var array=kids.putArray("knowledgeIds");if(meta.path("knowledgeId").isIntegralNumber())array.add(meta.path("knowledgeId").longValue());
        long first=firstKnown("SELECT id,create_time AS occurred_at,deleted FROM learning_event WHERE user_id=? AND event_type='FLASHCARD_REVIEWED' AND resource_type='FLASHCARD' AND resource_id=?",new Object[]{actor,cardId},"FLASHCARD_REVIEW",d);
        return new Source("FLASHCARD_REVIEW",id,"FLASHCARD:"+cardId,first,r.get("create_time"),d,knowledge(kids,actor));
    }
    private Source interview(long actor,long id,boolean lock) {
        var r=row("SELECT * FROM mock_interview_v2_turn WHERE id=?",id,lock);owner(r,actor);
        var session=row("SELECT * FROM mock_interview_session WHERE id=? AND deleted=0",number(r,"session_id"),false);owner(session,actor);
        var states=jdbc.queryForList("SELECT project_json,mode,state FROM mock_interview_v2_state WHERE session_id=?",number(r,"session_id"));
        if(states.isEmpty()||!"COMPLETED".equals(states.get(0).get("state")))throw new BusinessException(409,"面试尚未完成，请先完成原会话");
        var project=parse(states.get(0).get("project_json"));
        for(var e:project.path("evidence")) if(e.path("documentId").isIntegralNumber()&&!document(e.path("documentId").longValue(),actor))throw new BusinessException(403,"项目资料阅读权限已变化");
        var evaluation=parse(r.get("evaluation_json"));var d=base(r,"answer","FROZEN");
        var questionSnapshot=parse(session.get("questions_json"));
        JsonNode main=json.missingNode();for(var q:questionSnapshot)if(Objects.equals(q.path("id").asText(),r.get("question_id")))main=q;
        d.put("questionSnapshot",main.isMissingNode()?null:main);d.put("prompt",r.get("prompt"));d.put("gradingBasis",evaluation.isMissingNode()?null:evaluation);
        d.put("assessmentKind",evaluation.isMissingNode()?"ACTIVITY_ONLY":"AI_SUGGESTION");d.put("selfRating",r.get("self_score"));d.put("score",null);d.put("maxScore",null);
        d.put("gradingRule",nullableText(evaluation,"rubricVersion"));d.put("conditions",states.get(0).get("mode"));d.put("questionFingerprint",main.isMissingNode()?null:encode(main)+":"+r.get("prompt"));d.put("baselineSourceId",null);d.put("sessionId",session.get("id"));
        boolean projectQuestion=!project.path("userDescription").asText("").isBlank()||project.path("evidence").size()>0;
        long first=firstKnown("SELECT id,created_at AS occurred_at,0 AS deleted FROM mock_interview_v2_turn WHERE user_id=? AND question_id=?"+(projectQuestion?" AND session_id=?":""),projectQuestion?new Object[]{actor,r.get("question_id"),session.get("id")}:new Object[]{actor,r.get("question_id")},"INTERVIEW_TURN",d);
        return new Source("INTERVIEW_TURN",id,(projectQuestion?"INTERVIEW_PROJECT:"+session.get("id")+":":"INTERVIEW_QUESTION:")+r.get("question_id"),first,r.get("created_at"),d,List.of());
    }

    /** Historical presence is not proof of complete provenance. Never promote remaining IDs to original first. */
    private long firstKnown(String query,Object[] args,String type,Map<String,Object> data) {
        var first=jdbc.queryForMap("SELECT * FROM ("+query+") history ORDER BY occurred_at,id LIMIT 1",args);
        long id=number(first,"id");
        long deleted=jdbc.queryForObject("SELECT COUNT(*) FROM ("+query+") history WHERE deleted=1",Long.class,args);
        data.put("firstAttemptRef",null);
        data.put("firstKnownAttemptRef",Map.of("type",type,"id",id));
        data.put("historyCompleteness",deleted>0?"INCOMPLETE":"UNKNOWN");
        data.put("historyReasons",List.of(deleted>0?"LOGICALLY_DELETED_ATTEMPTS":"LEGACY_PROVENANCE_UNVERIFIED"));
        return id;
    }

    private Map<String,Object> base(Map<String,Object> r,String answer,String state) {
        var d=new LinkedHashMap<String,Object>();d.put("schemaVersion",2);d.put("answer",answer==null?null:r.get(answer));d.put("snapshotState",state);return d;
    }
    private String nullableText(JsonNode node,String field){return node.path(field).isTextual()?node.path(field).textValue():null;}
    private long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private JsonNode parse(Object raw){if(raw==null)return json.missingNode();try{return json.readTree(raw.toString());}catch(Exception ex){throw new IllegalStateException("Invalid persisted learning source JSON",ex);}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("Cannot encode learning source",ex);}}
}
