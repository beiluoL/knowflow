package com.knowflow.interview.career;

import org.junit.jupiter.api.Test;
import com.knowflow.interview.service.career.CareerParsingService;
import static org.junit.jupiter.api.Assertions.*;

/** 明确标题的保守分段，不调用模型，不猜不存在的经历。 */
class ResumeHeadingStructureTest {
 @Test void groupsOnlyExplicitResumeHeadingsAndRetainsExactEvidence() {
  String source="姓名：测试工程师\n邮箱：test@example.invalid\n项目经历\n使用 Java 开发知识库\n教育经历：测试大学 2020-2024\n技能：Java、Spring Boot、MySQL";
  var sections=CareerParsingService.previewSections("RESUME",source);
  assertEquals("使用 Java 开发知识库",sections.get("projects").get(0).value());
  assertEquals("教育经历：测试大学 2020-2024",sections.get("education").get(0).quote());
  assertEquals(3,sections.get("skills").size());
  assertFalse(sections.containsKey("experience"));
  sections.values().forEach(claims -> CareerMatcher.validate(claims,source));
 }
 @Test void doesNotInventStructureForUnknownTextOrAlterJobs() {
  assertTrue(CareerParsingService.previewSections("RESUME","Unlabelled private document").isEmpty());
  assertTrue(CareerParsingService.previewSections("JOB","项目经历\nJava").isEmpty());
 }
 @Test void neverTruncatesLongParagraphIntoAFabricatedQuote() {
  String source="项目经历\n"+"原文".repeat(300);
  assertTrue(CareerParsingService.previewSections("RESUME",source).values().stream().allMatch(java.util.List::isEmpty));
 }
}
