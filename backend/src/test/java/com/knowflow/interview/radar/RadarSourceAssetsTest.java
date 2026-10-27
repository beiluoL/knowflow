package com.knowflow.interview.radar;
import com.fasterxml.jackson.databind.*;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.seed.InterviewRadarImportService;
import com.knowflow.service.LearningService;
import com.knowflow.exception.BusinessException;
import com.knowflow.support.FakeAiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Import(FakeAiConfiguration.class)
@TestPropertySource(properties="knowflow.datasource.h2.url=jdbc:h2:mem:radar_assets;DB_CLOSE_DELAY=-1;MODE=MySQL")
class RadarSourceAssetsTest {
 @Autowired InterviewRadarImportService importer; @Autowired RadarSourceAssets assets;
 @Autowired SysUserMapper users; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired LearningService learning;
 private SysUser user(){var u=new SysUser();u.setUsername("radar-assets-"+UUID.randomUUID());u.setPassword("fixture");u.setRole("USER");users.insert(u);return u;}
 private String hash(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
 private JsonNode input()throws Exception {
  var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);byte[] bytes=out.toByteArray();
  String raw="# Q001 图片原理\n## 标准答案\n真实内容\n![图](assets/test.png)";String h=hash(raw.getBytes(StandardCharsets.UTF_8));
  return json.valueToTree(Map.of("schemaVersion",1,"files",List.of(Map.of("kind","markdown","sourceFile","test/Q001.md","sourceHash",h,"rawMarkdown",raw,"title","Q001 图片原理"),Map.of("kind","asset","sourceFile","test/assets/test.png","sourceHash",hash(bytes))),"mergedQuestions",List.of(Map.of("identity","interview-content/test:Q001","sourceNamespace","interview-content/test","sourceQuestionId","Q001","sources",List.of(Map.of("sourceFile","test/Q001.md","sourceHash",h)),"answerCandidates",List.of("真实内容"))),"imageAssets",List.of(Map.of("sourceFile","test/assets/test.png","sourceHash",hash(bytes),"mimeType","image/png","base64",Base64.getEncoder().encodeToString(bytes)))));
 }
 @Test @Transactional void storesPrivateAssetsIdempotentlyAndResolvesOnlyOwnedChapterImages()throws Exception{
  var owner=user();var other=user();var source=input();var result=importer.apply(owner.getId(),source);long path=((Number)result.get("pathId")).longValue();
  long chapter=jdbc.queryForObject("SELECT id FROM learning_chapter WHERE path_id=?",Long.class,path);
  var map=assets.forChapter(owner.getId(),chapter);assertTrue(map.get("assets/test.png").startsWith("data:image/png;base64,"));
  assertTrue(learning.getChapterDetail(chapter,owner.getId()).getContent().contains("![图](assets/test.png)"));
  assertEquals(map,learning.getChapterDetail(chapter,owner.getId()).getInlineSourceImages());
  assertTrue(assets.forChapter(other.getId(),chapter).isEmpty());
  long count=jdbc.queryForObject("SELECT COUNT(*) FROM doc_document",Long.class);importer.apply(owner.getId(),source);assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM doc_document",Long.class));
 }
 @Test @Transactional void retainsReferenceOnlyMarkdownWithoutTurningItIntoAQuestion()throws Exception{
  var owner=user();var source=(com.fasterxml.jackson.databind.node.ObjectNode)input();String raw="# 90题进度总览\n原文历史成绩8.5，不代表当前账号。";
  ((com.fasterxml.jackson.databind.node.ArrayNode)source.path("files")).add(json.valueToTree(Map.of("kind","markdown","sourceFile","test/overview.md","sourceHash",hash(raw.getBytes(StandardCharsets.UTF_8)),"rawMarkdown",raw,"title","90题进度总览")));
  var result=importer.apply(owner.getId(),source);assertEquals(1,result.get("referenceDocumentsCreated"));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM learning_chapter WHERE path_id=?",Integer.class,result.get("pathId")));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM doc_document WHERE source_path='test/overview.md' AND content LIKE ?",Integer.class,"%"+raw+"%"));
  assertEquals(3,jdbc.queryForObject("SELECT doc_count FROM doc_category WHERE code=?",Integer.class,"radar-private-"+owner.getId()));
 }
 @Test @Transactional void invalidImageFailsBeforeAnyPartialImport()throws Exception{
  var owner=user();var source=(com.fasterxml.jackson.databind.node.ObjectNode)input();((com.fasterxml.jackson.databind.node.ObjectNode)source.path("imageAssets").get(0)).put("base64","not image");
  assertThrows(BusinessException.class,()->importer.apply(owner.getId(),source));
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM interview_seed_resource WHERE seed_key=?",Integer.class,InterviewRadarImportService.KEY+owner.getId()));
 }
}
