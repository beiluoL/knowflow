package com.knowflow.interview.radar;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.seed.InterviewRadarImportService;
import com.knowflow.exception.BusinessException;
import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Import(FakeAiConfiguration.class)
@TestPropertySource(properties="knowflow.datasource.h2.url=jdbc:h2:mem:radar_bank;DB_CLOSE_DELAY=-1;MODE=MySQL")
class RadarInterviewBankTest {
 @Autowired RadarInterviewBank bank;
 @Autowired InterviewRadarImportService importer;
 @Autowired SysUserMapper users;
 @Autowired ObjectMapper json;
 private SysUser user(){var u=new SysUser();u.setUsername("radar-bank-"+UUID.randomUUID());u.setPassword("fixture");u.setRole("USER");users.insert(u);return u;}
 private com.fasterxml.jackson.databind.JsonNode input(String file,String id)throws Exception {
  String body="# "+id+" HashMap 原理\n## 标准答案\n秘密参考内容 SecretRubric-431";
  String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
  return json.valueToTree(Map.of("schemaVersion",1,"files",List.of(Map.of("kind","markdown","sourceFile",file,"sourceHash",hash,"rawMarkdown",body,"title",id+" HashMap 原理")),"mergedQuestions",List.of(Map.of("identity","interview-content/test:"+id,"sourceNamespace","interview-content/test","sourceQuestionId",id,"sources",List.of(Map.of("sourceFile",file,"sourceHash",hash)),"answerCandidates",List.of("秘密参考内容 SecretRubric-431")))));
 }
 @Test @Transactional void missingAnswerRemainsInCatalogButCannotBeTrained()throws Exception {
  var actor=user();var source=(com.fasterxml.jackson.databind.node.ObjectNode)input("test/Q002.md","Q002");
  ((com.fasterxml.jackson.databind.node.ObjectNode)source.path("mergedQuestions").get(0)).set("answerCandidates",json.createArrayNode());
  importer.apply(actor.getId(),source);var rows=bank.catalog(actor.getId());assertEquals(1,rows.size());assertFalse(rows.get(0).eligible());
 }
 @Test @Transactional void privateCatalogHidesAnswersAndForeignMaterialAndFlagsIdentityConflicts()throws Exception {
  var actor=user();var other=user();importer.apply(actor.getId(),input("test/Q001.md","Q001"));
  var rows=bank.catalog(actor.getId());assertEquals(1,rows.size());assertTrue(rows.get(0).eligible());
  assertFalse(json.writeValueAsString(rows).contains("SecretRubric-431"));
  assertTrue(bank.catalog(other.getId()).isEmpty());
  assertThrows(BusinessException.class,()->bank.material(other.getId(),rows.get(0).questionId()));
  assertThrows(BusinessException.class,()->bank.catalog(null));
  importer.apply(other.getId(),input("test/Q014.md","Q088"));
  assertFalse(bank.catalog(other.getId()).get(0).eligible());assertTrue(bank.tracks(other.getId()).isEmpty());
 }
}
