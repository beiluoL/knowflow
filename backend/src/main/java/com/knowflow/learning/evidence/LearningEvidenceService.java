package com.knowflow.learning.evidence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.*;

/** Immutable receipts, not another question bank, score calculator or mastery engine. */
@Service @RequiredArgsConstructor
public class LearningEvidenceService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final LearningEvidenceSources sources;
    private final LearningEvidenceSchema schema;

    @Transactional public Map<String,Object> capture(long actor,String type,long id) {return captureInternal(actor,type,id,true);}
    @Transactional public Map<String,Object> captureRequired(long actor,String type,long id) {return captureInternal(actor,type,id,false);}
    private Map<String,Object> captureInternal(long actor,String type,long id,boolean project) {
        // Lock the original row: concurrent requests serialize across app instances, without
        // assuming a test-only user row or releasing a UNIQUE identity through logical delete.
        var source=project?sources.read(actor,type,id,true):sources.readRequired(actor,type,id);
        var rows=receipts(actor,type,id);
        if(!rows.isEmpty())return withHistoryProjection(actor,decode(rows.get(0)),source);
        var data=new LinkedHashMap<>(source.data());
        data.put("sourceType",type);data.put("sourceId",id);data.put("resourceKey",source.resourceKey());
        data.put("firstSourceId",source.firstId());data.put("isFirstKnownAttempt",source.firstId()==id);
        data.put("knowledgeIds",source.knowledgeIds());data.put("knowledgeKeys",source.knowledgeIds().stream().map(k->"KG_ENTITY:"+k).toList());
        data.put("occurredAt",source.occurredAt().toString());
        data.put("masteryEffect","NONE_RECEIPT_ONLY");
        String snapshot=encode(data);
        jdbc.update("INSERT INTO learning_evidence_receipt(user_id,source_type,source_id,resource_key,first_source_id,snapshot_json,snapshot_sha,occurred_at) VALUES(?,?,?,?,?,?,?,?)",actor,type,id,source.resourceKey(),source.firstId(),snapshot,hash(snapshot),source.occurredAt());
        for(long kid:source.knowledgeIds()) jdbc.update("INSERT INTO learning_evidence_knowledge(user_id,source_type,source_id,knowledge_id) VALUES(?,?,?,?)",actor,type,id,kid);
        return withHistoryProjection(actor,decode(receipts(actor,type,id).get(0)),source);
    }
    @Transactional(readOnly=true) public Map<String,Object> get(long actor,String type,long id) {
        var source=sources.read(actor,type,id,false);
        var rows=receipts(actor,type,id);
        if(rows.isEmpty())throw new BusinessException(404,"尚未捕获此来源的证据回执");
        return withHistoryProjection(actor,decode(rows.get(0)),source);
    }
    @Transactional(readOnly=true) public Map<String,Object> page(long actor,int page,int size,String type,Long knowledgeId) {
        if(page<1||page>100000||size<1||size>100)throw new BusinessException(400,"page须为1–100000，size须为1–100");
        if(type!=null)sources.validate(type,1);
        if(knowledgeId!=null){if(knowledgeId<=0)throw new BusinessException(400,"知识点编号无效");sources.requireKnowledge(knowledgeId,actor);}
        String where=" WHERE r.user_id=?";var args=new ArrayList<Object>();args.add(actor);
        if(type!=null){where+=" AND r.source_type=?";args.add(type);}
        if(knowledgeId!=null){where+=" AND EXISTS (SELECT 1 FROM learning_evidence_knowledge k WHERE k.user_id=r.user_id AND k.source_type=r.source_type AND k.source_id=r.source_id AND k.knowledge_id=?)";args.add(knowledgeId);}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM learning_evidence_receipt r"+where,Long.class,args.toArray());
        var paged=new ArrayList<>(args);paged.add(size);paged.add((long)(page-1)*size);
        var rows=jdbc.queryForList("SELECT r.* FROM learning_evidence_receipt r"+where+" ORDER BY r.occurred_at DESC,r.source_type,r.source_id DESC LIMIT ? OFFSET ?",paged.toArray());
        var items=new ArrayList<Map<String,Object>>();int unavailable=0;
        for(var row:rows){try{sources.requireAccessible(actor,row.get("source_type").toString(),((Number)row.get("source_id")).longValue());var item=decode(row);sources.requireFrozenScope(actor,item);items.add(item);}
            catch(BusinessException ex){if(!Set.of(403,404,409).contains(ex.getCode()))throw ex;unavailable++;}}
        return Map.of("items",items,"page",page,"size",size,"total",total,"unavailableCount",unavailable,"hasMore",(long)page*size<total,"scannedCount",rows.size(),"totalSemantics","OWN_RECEIPTS_INCLUDING_UNAVAILABLE");
    }
    public record Retest(String baselineType, Long baselineId, String retestType, Long retestId) {}
    @Transactional public Map<String,Object> link(long actor, Retest input) {
        if(input==null||input.baselineId()==null||input.retestId()==null)throw new BusinessException(400,"请选择真实原答和复测记录");
        if(!Objects.equals(input.baselineType(),input.retestType())||Objects.equals(input.baselineId(),input.retestId()))throw new BusinessException(409,"复测必须来自相同来源且发生在原答之后");
        var first=capture(actor,input.baselineType(),input.baselineId());
        var next=capture(actor,input.retestType(),input.retestId());
        if(!after(first,next))throw new BusinessException(409,"复测发生时间必须晚于原答，不能用导入ID推断顺序");
        if(!Objects.equals(first.get("resourceKey"),next.get("resourceKey")))throw new BusinessException(409,"两条记录并非同一原题资源");
        if(next.get("baselineSourceId") instanceof Number n&&n.longValue()!=input.baselineId())throw new BusinessException(409,"原作答已绑定其他复测基线");
        var rows=jdbc.queryForList("SELECT baseline_type,baseline_id FROM learning_evidence_retest WHERE user_id=? AND retest_type=? AND retest_id=?",actor,input.retestType(),input.retestId());
        if(!rows.isEmpty()) {
            var r=rows.get(0);
            if(!input.baselineType().equals(r.get("baseline_type"))||((Number)r.get("baseline_id")).longValue()!=input.baselineId())throw new BusinessException(409,"复测关系已存在，不能覆盖基线");
        } else jdbc.update("INSERT INTO learning_evidence_retest(user_id,retest_type,retest_id,baseline_type,baseline_id) VALUES(?,?,?,?,?)",actor,input.retestType(),input.retestId(),input.baselineType(),input.baselineId());
        return compare(actor,input.baselineType(),input.baselineId(),input.retestType(),input.retestId());
    }
    @Transactional(readOnly=true) public Map<String,Object> compare(long actor,String baselineType,long baselineId,String retestType,long retestId) {
        var first=get(actor,baselineType,baselineId);var second=get(actor,retestType,retestId);
        var reasons=new ArrayList<String>();
        for(var receipt:List.of(first,second)){
            if(!(receipt.get("score") instanceof Number score)||!(receipt.get("maxScore") instanceof Number max)||max.doubleValue()<=0||score.doubleValue()<0||score.doubleValue()>max.doubleValue()){
                if(!reasons.contains("FROZEN_GRADE_UNAVAILABLE"))reasons.add("FROZEN_GRADE_UNAVAILABLE");
            }
            String rule=Objects.toString(receipt.get("gradingRule"),"");
            if(rule.isBlank()||rule.contains("UNKNOWN"))if(!reasons.contains("GRADING_RULE_VERSION_UNKNOWN"))reasons.add("GRADING_RULE_VERSION_UNKNOWN");
        }
        if(!"OBJECTIVE".equals(first.get("assessmentKind"))||!"OBJECTIVE".equals(second.get("assessmentKind")))reasons.add("ASSESSMENT_KIND_NOT_OBJECTIVE");
        if(!baselineType.equals(retestType)||!Objects.equals(first.get("resourceKey"),second.get("resourceKey")))reasons.add("DIFFERENT_RESOURCE");
        if(!after(first,second))reasons.add("RETEST_NOT_AFTER_BASELINE");
        for(String field:List.of("questionFingerprint","gradingRule","conditions","maxScore")) {
            if(first.get(field)==null||second.get(field)==null)reasons.add("MISSING_"+field.toUpperCase(Locale.ROOT));
            else if(!Objects.equals(first.get(field),second.get(field)))reasons.add(switch(field){case "questionFingerprint"->"QUESTION_VERSION_CHANGED";case "gradingRule"->"GRADING_RULE_CHANGED";case "conditions"->"CONDITIONS_CHANGED";default->"SCORE_SCALE_CHANGED";});
        }
        for(String field:List.of("hintCount","answerExposed","answerMode","questionVersion")) {
            if((first.get(field)!=null||second.get(field)!=null)&&!Objects.equals(first.get(field),second.get(field)))reasons.add(switch(field){case "hintCount"->"HINT_USAGE_CHANGED";case "answerExposed"->"ANSWER_EXPOSURE_CHANGED";case "answerMode"->"ANSWER_MODE_CHANGED";default->"QUESTION_VERSION_CHANGED";});
        }
        if(!"FROZEN".equals(first.get("snapshotState"))||!"FROZEN".equals(second.get("snapshotState")))reasons.add("LEGACY_SNAPSHOT_UNAVAILABLE");
        boolean linked=second.get("baselineSourceId") instanceof Number n&&n.longValue()==baselineId&&baselineType.equals(retestType);
        linked |= jdbc.queryForObject("SELECT COUNT(*) FROM learning_evidence_retest WHERE user_id=? AND retest_type=? AND retest_id=? AND baseline_type=? AND baseline_id=?",Integer.class,actor,retestType,retestId,baselineType,baselineId)>0;
        if(!linked)reasons.add("EXPLICIT_RETEST_LINK_MISSING");
        boolean comparable=reasons.isEmpty();
        var result=new LinkedHashMap<String,Object>();result.put("baseline",first);result.put("retest",second);result.put("explicitlyLinked",linked);result.put("comparable",comparable);result.put("comparability",comparable?"COMPARABLE":"NOT_COMPARABLE");result.put("reasons",reasons);
        if(comparable&&first.get("score") instanceof Number a&&second.get("score") instanceof Number b)result.put("scoreDifference",b.doubleValue()-a.doubleValue());
        result.put("note",comparable?"仅比较同题同规则同条件的两次客观作答，不代表能力提升。":"缺少显式复测关系、快照或相同评分条件；不计算成绩差异。");
        return result;
    }
    private Map<String,Object> withHistoryProjection(long actor,Map<String,Object> receipt,LearningEvidenceSources.Source source) {
        sources.requireFrozenScope(actor,receipt);
        var projection=new LinkedHashMap<String,Object>();
        for(String key:List.of("firstAttemptRef","firstTrackedAttemptRef","trackedHistoryCompleteness","firstKnownAttemptRef","firstKnownOrdering","historyCompleteness","historyReasons","latestQualifiedAttemptRef","bestScore","qualificationScope","qualificationComplete","qualificationReasons","qualificationScanLimit"))projection.put(key,source.data().get(key));
        receipt.put("historyProjection",projection);receipt.put("historyProjectionSemantics","READ_TIME");return receipt;
    }
    private boolean after(Map<String,Object> first,Map<String,Object> next) {
        try {return Timestamp.valueOf(next.get("occurredAt").toString()).after(Timestamp.valueOf(first.get("occurredAt").toString()));}
        catch(IllegalArgumentException|NullPointerException ex){return false;}
    }
    private List<Map<String,Object>> receipts(long actor,String type,long id){return jdbc.queryForList("SELECT * FROM learning_evidence_receipt WHERE user_id=? AND source_type=? AND source_id=?",actor,type,id);}
    private Map<String,Object> decode(Map<String,Object> row){
        try{var result=json.readValue(row.get("snapshot_json").toString(),new TypeReference<LinkedHashMap<String,Object>>(){});result.put("receiptSha",row.get("snapshot_sha"));result.put("capturedAt",row.get("captured_at").toString());result.put("historyProjectionSemantics","CAPTURE_TIME");return result;}
        catch(Exception ex){throw new IllegalStateException("Invalid persisted evidence receipt",ex);}
    }
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("Cannot encode evidence receipt",ex);}}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
