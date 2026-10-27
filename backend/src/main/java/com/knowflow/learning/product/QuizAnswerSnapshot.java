package com.knowflow.learning.product;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.QuizQuestion;
import java.util.*;import java.security.MessageDigest;
/** Immutable server-side practice conditions, not a second question bank or mastery signal. */
public final class QuizAnswerSnapshot {
 public static final String RULE="QUIZ_OBJECTIVE_V1";
 private QuizAnswerSnapshot(){}
 public static Map<String,Object> question(QuizQuestion q){var m=new LinkedHashMap<String,Object>();m.put("questionId",q.getId());m.put("canonicalKey",q.getCanonicalKey());m.put("version",q.getVersion());m.put("title",q.getTitle());m.put("content",q.getContent());m.put("questionType",q.getQuestionType());m.put("options",q.getOptions());m.put("answer",q.getAnswer());m.put("explanation",q.getExplanation());m.put("docId",q.getDocId());m.put("categoryId",q.getCategoryId());return m;}
 public static String hash(ObjectMapper json,QuizQuestion q){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(question(q))));}catch(Exception e){throw new IllegalStateException(e);}}
 public static String freeze(ObjectMapper json,QuizQuestion q,Long baseline,List<Long> knowledge){var m=question(q);m.put("schemaVersion",1);m.put("contentHash",hash(json,q));m.put("gradingRule",RULE);m.put("conditions","UNTIMED_ANSWER_VISIBLE_PRACTICE");m.put("knowledgeIds",knowledge);if(baseline!=null)m.put("baselineAnswerRecordId",baseline);try{return json.writeValueAsString(m);}catch(Exception e){throw new IllegalStateException(e);}}
}
