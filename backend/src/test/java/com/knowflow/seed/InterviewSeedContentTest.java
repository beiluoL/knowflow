package com.knowflow.seed;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class InterviewSeedContentTest {
 @Test void javaQuestionsHaveSpecificPitfallsInsteadOfTheSameGenericFooter() {
  Set<String> pitfalls=new HashSet<>();
  for(var p:new InterviewSeedCatalog(new ObjectMapper()).paths())if(Set.of("java-core","concurrency","jvm").contains(p.get("key").asText()))for(var c:p.get("chapters"))for(var q:c.get("questions")){
   assertFalse(q.get("answer").asText().contains("最后指出验证范围"));
   pitfalls.add(q.get("commonMistakes").toString());
  }
  assertEquals(36,pitfalls.size());
 }
 @Test void persistedTopicTagsUseOneCanonicalTaxonomy() {
  var allowed=Set.of("JAVA","CONCURRENCY","JVM","SPRING","MYSQL","REDIS","MQ","DISTRIBUTED","LLM","EMBEDDING","RAG","VECTOR_DB","MILVUS","AGENT","TOOL_CALLING","MCP","EVALUATION","INTERVIEW","CODE","PROJECT","S","A","B");
  for(var p:new InterviewSeedCatalog(new ObjectMapper()).paths())for(String tag:p.get("tag").asText().split(","))assertTrue(allowed.contains(tag),"noncanonical topic tag: "+tag);
 }
 @Test void llmSamplingIsTaughtAsPartOfTheActualLearningMaterial() {
  var llm=new InterviewSeedCatalog(new ObjectMapper()).paths().stream().filter(p->p.get("key").asText().equals("llm")).findFirst().orElseThrow();
  var chapter=llm.get("chapters").get(0);
  assertTrue(chapter.get("material").asText().contains("Temperature"));
  assertTrue(chapter.get("material").asText().contains("Top-P"));
  assertTrue(chapter.get("questions").toString().contains("采样"));
 }
 @Test void agentAndProjectQuestionsProvideTopicSpecificFollowups() {
  var catalog=new InterviewSeedCatalog(new ObjectMapper());Set<String> followups=new HashSet<>();int count=0;
  for(var path:catalog.paths())if(Set.of("agent","project").contains(path.get("key").asText()))for(var ch:path.get("chapters"))for(var q:ch.get("questions")){followups.add(q.get("followUps").toString());count++;assertTrue(q.get("answer").asText().length()>=150);}
  assertEquals(24,count);assertTrue(followups.size()>=18,"independent interview topics need distinct follow-up drills, not the same generic template");
 }
}
