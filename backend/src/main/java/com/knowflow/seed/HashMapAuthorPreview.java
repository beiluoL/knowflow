package com.knowflow.seed;

import com.fasterxml.jackson.databind.*;
import com.knowflow.entity.*;
import com.knowflow.exception.BusinessException;
import com.knowflow.learning.actions.PersonalLearningResourceScope;
import com.knowflow.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Read-only author-package projection inside the existing guarded QA channel. Never approves or imports. */
@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="knowflow.content-studio.qa.enabled",havingValue="true")
public class HashMapAuthorPreview {
 private final ObjectMapper json;private final Environment env;private final JdbcTemplate jdbc;
 private final QuizQuestionMapper questions;private final KgEntityMapper knowledge;private final DocDocumentMapper documents;
 private final PersonalLearningResourceScope personal;
 public Map<String,Object> preview(long actor,long chapterId,List<Long> candidateIds) {
  try {
   Path root=Path.of(env.getProperty("knowflow.content-studio.author-root",Path.of(env.getRequiredProperty("knowflow.content-studio.qa.package")).resolveSibling("java-ai-interview").toString())).toRealPath();
   JsonNode pack=null;Path selected=null;
   try(var files=Files.list(root.resolve("99-maintenance/production/learning-product-v1"))){
    for(Path file:files.filter(p->p.toString().endsWith(".json")).toList()) {var item=json.readTree(Files.readAllBytes(file));if("JAVA-COLLECTIONS-HASHMAP".equals(item.path("topicId").asText())){if(pack!=null)throw new BusinessException(409,"Duplicate author topic identity");pack=item;selected=file;}}
   }
   if(pack==null)throw new BusinessException(404,"HashMap作者包不存在");
   Path body=root.resolve(pack.required("bodyPath").asText()).toRealPath();if(!body.startsWith(root))throw new BusinessException(409,"教材来源越界");
   List<String> conflicts=new ArrayList<>();if(!sha(Files.readAllBytes(body)).equals(pack.required("bodySha256").asText()))conflicts.add("BODY_HASH_CHANGED");
   var kg=new ArrayList<Map<String,Object>>();
   for(long id:candidateIds){var k=knowledge.selectById(id);if(k==null||!personal.category(k.getCategoryId(),actor)||(k.getDocId()!=null&&!personal.document(documents.selectById(k.getDocId()),actor)))throw new BusinessException(403,"候选知识来源无权引用");kg.add(Map.of("knowledgeId",id,"name",k.getName(),"decision","PENDING_SEMANTIC_REVIEW"));}
   var pool=questions.selectList(null).stream().filter(q->personal.category(q.getCategoryId(),actor)&&(q.getDocId()==null||personal.document(documents.selectById(q.getDocId()),actor))).toList();
   Path reviewFile=root.resolve("99-maintenance/reviews/hashmap-v1/technical-review.json");
   JsonNode review=Files.exists(reviewFile)?json.readTree(Files.readAllBytes(reviewFile)):json.createObjectNode();
   var rows=new ArrayList<Map<String,Object>>();
   for(var q:pack.required("questions")){
    var matches=pool.stream().filter(x->Objects.equals(x.getCanonicalKey(),q.path("canonicalKey").asText())).toList();
    var duplicates=pool.stream().filter(x->normalize(x.getContent()).equals(normalize(q.path("content").asText()))).map(QuizQuestion::getId).toList();
    var row=new LinkedHashMap<String,Object>();row.put("canonicalKey",q.path("canonicalKey").asText());row.put("authorVersion",q.path("version").asText());row.put("existingIds",matches.stream().map(QuizQuestion::getId).toList());row.put("exactStemDuplicateIds",duplicates);
    row.put("operation",matches.isEmpty()?"PENDING_NEW":matches.size()==1?"REVIEW_EXISTING_VERSION":"CANONICAL_CONFLICT");row.put("runtimeVersions",matches.stream().map(x->Map.of("id",x.getId(),"version",Objects.toString(x.getVersion(),"legacy"),"reviewStatus",Objects.toString(x.getReviewStatus(),"UNVERIFIED_LEGACY"),"published",Integer.valueOf(1).equals(x.getStatus()))).toList());
    var semantic=new ArrayList<Map<String,Object>>();
    for(var candidate:review.path("semanticReview"))if(q.path("canonicalKey").asText().equals(candidate.path("authorCanonicalKey").asText())){
     var ids=new LinkedHashSet<Long>();for(var live:pool)if(candidate.path("legacyCanonicalKey").asText().equals(live.getCanonicalKey()))ids.add(live.getId());
     for(long id:jdbc.queryForList("SELECT resource_id FROM interview_seed_resource WHERE seed_key=? AND resource_key=? AND resource_type='QUIZ'",Long.class,candidate.path("legacySeedKey").asText(),candidate.path("legacyResourceKey").asText()))if(pool.stream().anyMatch(live->live.getId().equals(id)))ids.add(id);
     semantic.add(Map.of("review",candidate,"readableRuntimeIds",ids,"identityState",ids.isEmpty()?"UNRESOLVED_NOT_GUESSED":"LIVE_CANONICAL_OR_ORIGINAL_LEDGER","decision","HUMAN_REUSE_MERGE_REQUIRED"));
    }
    row.put("semanticReviewCandidates",semantic);row.put("content",q);rows.add(row);
   }
   var out=new LinkedHashMap<String,Object>();out.put("dryRun",true);out.put("humanApproved",pack.path("humanApproved").asBoolean());out.put("publication","BLOCKED_BY_HUMAN_REVIEW");out.put("runtimeWrites",0);out.put("technicalReview",pack.path("technicalReview").asText());out.put("topicId",pack.path("topicId").asText());out.put("authorFile",root.relativize(selected).toString());out.put("authorSha256",sha(Files.readAllBytes(selected)));out.put("bodySha256",sha(Files.readAllBytes(body)));out.put("chapterIdentity",pack.path("chapterIdentity").asText());out.put("chapterId",chapterId);out.put("knowledgeCandidates",kg);out.put("knowledgeDecision","PENDING_NO_NEW_ACCEPTED_MAPPING");out.put("questions",rows);out.put("cards",pack.required("flashcards"));out.put("conflicts",conflicts);out.put("semanticDeduplication","EXACT_STEM_AND_DOCUMENTED_LEGACY_CANDIDATES_NOT_COMPLETE_CATALOG_SEMANTIC_REVIEW");out.put("existingChapterMappings",jdbc.queryForList("SELECT resource_type,resource_id,knowledge_id,source,status FROM resource_knowledge_mapping WHERE resource_type='DOC' AND resource_id IN (SELECT doc_id FROM kg_entity WHERE id IN ("+String.join(",",Collections.nCopies(candidateIds.size(),"?"))+")) AND deleted=0",candidateIds.toArray()));return out;
  } catch(java.io.IOException e){throw new BusinessException(409,"作者文件无法读取或来源已变更");}
 }
 private static String normalize(String s){return s==null?"":s.replaceAll("\\s+","").toLowerCase(Locale.ROOT);}
 private static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
}
