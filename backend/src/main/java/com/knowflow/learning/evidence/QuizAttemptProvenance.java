package com.knowflow.learning.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Predicate;

/** Provenance in the existing immutable snapshot, never a replacement grade/history table. */
@Component @RequiredArgsConstructor
public class QuizAttemptProvenance {
 private final JdbcTemplate jdbc; private final ObjectMapper json;
 private JsonNode parse(Object value){try{return value==null?json.missingNode():json.readTree(value.toString());}catch(Exception e){throw new IllegalStateException("Invalid quiz history snapshot",e);}}
 public String stamp(long actor,long question,String snapshot){
  var node=(com.fasterxml.jackson.databind.node.ObjectNode)parse(snapshot);
  var first=jdbc.queryForList("SELECT id,question_snapshot FROM quiz_answer_record WHERE user_id=? AND question_id=? ORDER BY create_time,id LIMIT 1",actor,question);
  long anchor=0;String completeness="COMPLETE_FROM_TRACKING";
  if(!first.isEmpty()){
   var previous=parse(first.get(0).get("question_snapshot"));
   if(previous.path("historyTrackingVersion").asInt()!=2||!previous.hasNonNull("firstTrackedAttemptId"))completeness="UNKNOWN";
   else{anchor=previous.path("firstTrackedAttemptId").asLong();if(anchor==0)anchor=((Number)first.get(0).get("id")).longValue();}
  }else{
   var receipts=jdbc.queryForList("SELECT snapshot_json FROM learning_evidence_receipt WHERE user_id=? AND source_type='QUIZ_ANSWER' AND resource_key=? ORDER BY occurred_at,source_id LIMIT 1",actor,"QUIZ:"+question);
   if(!receipts.isEmpty()){
    var ref=parse(receipts.get(0).get("snapshot_json")).path("firstTrackedAttemptRef");
    if(ref.path("id").asLong()>0)anchor=ref.path("id").asLong();else completeness="UNKNOWN";
   }
  }
  node.put("answeredAt",java.time.LocalDateTime.now().toString());
  node.put("historyTrackingVersion",2);node.put("trackedHistoryState",completeness);
  if("UNKNOWN".equals(completeness))node.putNull("firstTrackedAttemptId");else node.put("firstTrackedAttemptId",anchor);
  try{return json.writeValueAsString(node);}catch(Exception e){throw new IllegalStateException(e);}
 }
 /** Refine only an explicit read projection; necessary submission never scans a time bucket. */
 public long firstKnownId(long actor,long question,long coarse,Map<String,Object> data){
  String where=" FROM quiz_answer_record WHERE user_id=? AND question_id=? AND create_time=(SELECT MIN(create_time) FROM quiz_answer_record WHERE user_id=? AND question_id=?)";
  Object[] args={actor,question,actor,question};long count=jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,args);
  data.put("firstKnownOrdering","DATABASE_TIME_ID_TIEBREAKER");
  if(count<=1)return coarse;
  if(count>1000){data.put("firstKnownOrdering","AMBIGUOUS_TIME_BUCKET_LIMIT");return coarse;}
  Long earliest=null;java.time.LocalDateTime time=null;boolean tied=false;
  for(int offset=0;offset<count;offset+=100){
   var rows=jdbc.queryForList("SELECT id,question_snapshot,create_time"+where+" ORDER BY id LIMIT ? OFFSET ?",actor,question,actor,question,100,offset);
   for(var row:rows){
    JsonNode snap;java.time.LocalDateTime candidate;
    try{snap=parse(row.get("question_snapshot"));if(!snap.path("answeredAt").isTextual()){data.put("firstKnownOrdering","AMBIGUOUS_LEGACY_TIME_TIE");return coarse;}candidate=java.time.LocalDateTime.parse(snap.path("answeredAt").asText());}
    catch(IllegalStateException|java.time.DateTimeException invalid){data.put("firstKnownOrdering","AMBIGUOUS_LEGACY_TIME_TIE");return coarse;}
    if(time==null||candidate.isBefore(time)){time=candidate;earliest=((Number)row.get("id")).longValue();tied=false;}
    else if(candidate.equals(time))tied=true;
   }
  }
  data.put("firstKnownOrdering",tied?"PRECISE_TIME_TIE_ID_TIEBREAKER":"PRECISE_SERVER_TIME");
  if(earliest==null)return coarse;data.put("firstKnownAttemptRef",Map.of("type","QUIZ_ANSWER","id",earliest));return earliest;
 }
 public void describe(long actor,long question,long current,JsonNode snapshot,Map<String,Object> data,Predicate<JsonNode> accessible,boolean includeStatistics){
  boolean tracked=snapshot.path("historyTrackingVersion").asInt()==2&&snapshot.hasNonNull("firstTrackedAttemptId")&&"COMPLETE_FROM_TRACKING".equals(snapshot.path("trackedHistoryState").asText());
  long anchor=snapshot.path("firstTrackedAttemptId").asLong();if(anchor==0)anchor=current;
  data.put("firstTrackedAttemptRef",null);data.put("trackedHistoryCompleteness","UNKNOWN");
  if(tracked){
   var origins=jdbc.queryForList("SELECT deleted FROM quiz_answer_record WHERE id=? AND user_id=? AND question_id=?",anchor,actor,question);
   boolean missing=origins.isEmpty()||((Number)origins.get(0).get("deleted")).intValue()==1;
   data.put("firstTrackedAttemptRef",Map.of("type","QUIZ_ANSWER","id",anchor));
   data.put("trackedHistoryCompleteness",missing||"INCOMPLETE".equals(data.get("historyCompleteness"))?"INCOMPLETE":"COMPLETE_FROM_TRACKING");
   if(missing){data.put("historyCompleteness","INCOMPLETE");data.put("historyReasons",List.of("TRACKED_ORIGIN_UNAVAILABLE"));}
  }
  // A new tracking point never proves that older, physically missing history did not exist.
  // Original firstAttemptRef remains null unless a separately verified migration can prove it.
  data.put("latestQualifiedAttemptRef",null);data.put("bestScore",null);
  data.put("qualificationScope","READ_PROJECTION_NOT_COMPUTED");data.put("qualificationComplete",false);
  if(!includeStatistics)return;
  // Bounded server-side projection. It never labels a truncated scan as an all-history best.
  Map<String,Object> latest=null;Integer best=null;int scanned=0;boolean complete=true;boolean unreadable=false;java.time.LocalDateTime latestAt=null;boolean tied=false;
  outer: for(int offset=0;offset<=1000;offset+=100){
   var rows=jdbc.queryForList("SELECT a.id,a.score,a.create_time,a.question_snapshot,r.snapshot_json FROM quiz_answer_record a LEFT JOIN learning_evidence_receipt r ON r.user_id=a.user_id AND r.source_type='QUIZ_ANSWER' AND r.source_id=a.id WHERE a.user_id=? AND a.question_id=? AND a.deleted=0 ORDER BY a.create_time DESC,a.id DESC LIMIT ? OFFSET ?",actor,question,offset==1000?1:100,offset);
   for(var row:rows){
    if(++scanned>1000){complete=false;break outer;}
    JsonNode candidate,frozen;
    try{frozen=parse(row.get("snapshot_json"));candidate=frozen.isMissingNode()?parse(row.get("question_snapshot")):frozen.path("questionSnapshot");}
    catch(IllegalStateException malformed){unreadable=true;continue;}
    if(!frozen.isMissingNode()&&!"OBJECTIVE".equals(frozen.path("assessmentKind").asText()))continue;
    if(!sameConditions(snapshot,candidate)||!accessible.test(candidate))continue;
    Object raw=frozen.isMissingNode()?row.get("score"):frozen.path("score").isNumber()?frozen.path("score").numberValue():null;
    if(!(raw instanceof Number n)||n.intValue()<0||n.intValue()>100)continue;
    java.time.LocalDateTime occurred;
    try{occurred=candidate.path("answeredAt").isTextual()?java.time.LocalDateTime.parse(candidate.path("answeredAt").asText()):java.sql.Timestamp.valueOf(row.get("create_time").toString()).toLocalDateTime();}
    catch(java.time.DateTimeException|IllegalArgumentException malformedTime){unreadable=true;continue;}
    if(latestAt==null||occurred.isAfter(latestAt)){latestAt=occurred;latest=Map.of("type","QUIZ_ANSWER","id",((Number)row.get("id")).longValue());tied=false;}
    else if(occurred.equals(latestAt))tied=true;
    best=best==null?n.intValue():Math.max(best,n.intValue());
   }
   if(rows.size()<100)break;
  }
  data.put("latestQualifiedAttemptRef",complete&&!unreadable&&!tied?latest:null);data.put("latestOrderKnown",!tied);data.put("bestScore",complete&&!unreadable?best:null);
  data.put("qualificationScope","ACCESSIBLE_SAME_QUESTION_VERSION_RULE_CONDITIONS_OBJECTIVE");data.put("qualificationScanLimit",1000);data.put("qualificationComplete",complete&&!unreadable);
  data.put("qualificationReasons",unreadable?List.of("HISTORICAL_SNAPSHOT_UNREADABLE"):complete?List.of():List.of("HISTORY_SCAN_LIMIT_REACHED"));
 }
 private boolean sameConditions(JsonNode reference,JsonNode candidate){
  if(!"QUIZ_OBJECTIVE_V1".equals(reference.path("gradingRule").asText())||!"QUIZ_OBJECTIVE_V1".equals(candidate.path("gradingRule").asText()))return false;
  for(String key:List.of("contentHash","conditions"))if(!reference.path(key).isTextual()||reference.path(key).asText().isBlank()||!Objects.equals(reference.get(key),candidate.get(key)))return false;
  for(String key:List.of("hintCount","answerExposed","answerMode","version"))if(!Objects.equals(reference.get(key),candidate.get(key)))return false;
  return true;
 }
}
