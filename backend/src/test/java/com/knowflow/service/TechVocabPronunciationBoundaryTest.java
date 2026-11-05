package com.knowflow.service;

import com.knowflow.techenglish.service.TechVocabService;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.techenglish.dto.TechVocabDtos;
import com.knowflow.techenglish.entity.TechVocabPronunciation;
import com.knowflow.techenglish.mapper.TechVocabPronunciationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证技术词汇发音功能的访问边界。
 */
@SpringBootTest(properties={
 "knowflow.datasource.h2.url=jdbc:h2:mem:vocab_pron_boundary;DB_CLOSE_DELAY=-1;MODE=MySQL",
 "knowflow.datasource.config-file=/tmp/vocab-pron-boundary-no-overrides.json",
 "knowflow.datasource.allow-runtime-switch=false",
 "knowflow.seed.development-account.enabled=false"
})
class TechVocabPronunciationBoundaryTest {
 @Autowired TechVocabService service;
 @Autowired ObjectMapper json;
 @Autowired JdbcTemplate jdbc;
 @SpyBean TechVocabPronunciationMapper pronunciations;

 @Test void ordinaryVocabularyServiceDoesNotDependOnJdbcTemplate() {
  assertTrue(Arrays.stream(TechVocabService.class.getDeclaredFields())
    .noneMatch(field -> JdbcTemplate.class.isAssignableFrom(field.getType())),
    "Vocabulary business CRUD must delegate physical child deletion to its mapper");
 }
 private TechVocabDtos.TermInput input(String headword,Object rows,boolean supplied) {
  Map<String,Object> values=new HashMap<>(Map.of("headword",headword,"termType","WORD","domain","JAVA","senseKey","boundary-test","meaningCn","测试","contentStatus","DRAFT"));
  if(supplied)values.put("pronunciations",rows);
  return json.convertValue(values,TechVocabDtos.TermInput.class);
 }
 private long create(String word) {
  service.adminCreate(input(word,null,false));
  return jdbc.queryForObject("SELECT id FROM tech_vocab_term WHERE headword=?",Long.class,word);
 }
 private int count(long id) {return jdbc.queryForObject("SELECT COUNT(*) FROM tech_vocab_pronunciation WHERE term_id=?",Integer.class,id);}
 @Test void nullUpdateKeepsRowsAndEmptyUpdatePhysicallyRemovesAllIncludingDeleted() {
  String word="Boundary"+UUID.randomUUID().toString().replace("-","");
  long id=create(word),other=create(word+"Other");
  assertEquals(2,count(id));
  jdbc.update("UPDATE tech_vocab_pronunciation SET deleted=1 WHERE term_id=? AND variant='WORD_US'",id);
  service.adminUpdate(id,input(word,null,true));
  assertEquals(2,count(id));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tech_vocab_pronunciation WHERE term_id=? AND deleted=1",Integer.class,id));
  service.adminUpdate(id,input(word,List.of(),true));
  assertEquals(0,count(id));
  assertEquals(2,count(other));
  service.adminUpdate(id,input(word,List.of(),true));
  assertEquals(0,count(id));
 }
 @Test void failedRebuildRollsBackPhysicalDeleteAndTermUpdate() {
  String word="Rollback"+UUID.randomUUID().toString().replace("-","");long id=create(word);
  doThrow(new IllegalStateException("injected pronunciation insert failure")).when(pronunciations).insert(any(TechVocabPronunciation.class));
  try {
   var rows=List.of(Map.of("variant","WORD_US","readingText",word,"locale","en-US","readingMode","SPOKEN_WORD","verificationStatus","PENDING"));
   assertThrows(IllegalStateException.class,()->service.adminUpdate(id,input(word+"Changed",rows,true)));
   assertEquals(2,count(id));
   assertEquals(word,jdbc.queryForObject("SELECT headword FROM tech_vocab_term WHERE id=?",String.class,id));
  } finally {reset(pronunciations);}
 }
}
