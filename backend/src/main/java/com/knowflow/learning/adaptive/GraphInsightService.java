package com.knowflow.learning.adaptive;
import com.knowflow.service.KnowledgeMasteryService;
import com.knowflow.learning.evidence.LearningEvidenceService;
import com.knowflow.security.DocumentAccessPolicy;
import com.knowflow.mapper.DocDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
/** A read adapter only. Knowledge identity, mastery synthesis and receipts remain in their original services. */
@Service @RequiredArgsConstructor @ConditionalOnProperty(name="knowflow.product.adaptive-learning.enabled",havingValue="true")
public class GraphInsightService {
 private final KnowledgeMasteryService mastery;private final LearningEvidenceService evidence;private final JdbcTemplate sql;private final DocumentAccessPolicy access;private final DocDocumentMapper documents;
 @org.springframework.beans.factory.annotation.Autowired(required=false)private com.knowflow.learning.feedback.InterviewRetestService retests;
 public Map<String,Object> detail(long user,long knowledge){
  var state=mastery.getDetail(user,knowledge); // authoritative current synthesis + knowledge/source authorization
  var receipts=evidence.page(user,1,10,null,knowledge);
  // Exact accepted DOC identity only; never infer a private note by title or keywords.
  var notes=new ArrayList<Map<String,Object>>();
  var mappings=sql.queryForList("SELECT DISTINCT resource_id FROM resource_knowledge_mapping WHERE knowledge_id=? AND resource_type='DOC' AND status='ACCEPTED' AND source IN ('MANUAL','IMPORT') AND deleted=0",Long.class,knowledge);
  for(long doc:mappings){if(!access.canRead(documents.selectById(doc),user))continue;notes.addAll(sql.query("SELECT id,title,revision FROM learning_note WHERE user_id=? AND source_doc_id=? AND deleted=0 AND status='SAVED' ORDER BY id DESC LIMIT 10",(rs,index)->Map.<String,Object>of("id",rs.getLong(1),"title",rs.getString(2),"revision",rs.getLong(3)),user,doc));}
  var recent=new ArrayList<Map<String,Object>>();if(retests!=null)for(long id:sql.queryForList("SELECT DISTINCT p.id FROM learning_interview_retest p JOIN learning_error_annotation a ON a.owner_id=p.owner_id AND a.source_type='INTERVIEW_TURN' AND a.source_id=p.baseline_turn JOIN resource_knowledge_mapping m ON m.resource_type='ERROR_ANNOTATION' AND m.resource_id=a.id AND m.knowledge_id=? AND m.status='ACCEPTED' AND m.source='MANUAL' AND m.deleted=0 WHERE p.owner_id=? ORDER BY p.id DESC LIMIT 5",Long.class,knowledge,user)){try{recent.add(retests.detail(user,id));}catch(com.knowflow.exception.BusinessException e){if(!Set.of(403,404,409).contains(e.getCode()))throw e;}}
  return Map.of("recentRetests",recent,"knowledgeId",knowledge,"mastery",state,"evidence",receipts,"practiceNotes",notes,"identityRule","KG_ENTITY_ID_AND_EXPLICIT_ACCEPTED_MAPPING","notice","没有真实映射时不关联；AI评价与活动次数不自动证明掌握");
 }
}
