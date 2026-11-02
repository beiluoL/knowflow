package com.knowflow.interview.career;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class CareerMatcherTest {
 @Test void aliasesMatchButSubstringAndFabricatedEvidenceDoNot(){
  var claims=List.of(new CareerMatcher.Claim("Java 多线程","开发过 Java 多线程练习"));
  assertEquals("MATCHED",CareerMatcher.match(claims,List.of(new CareerMatcher.Claim("Java 并发","要求 Java 并发"))).get(0).matchStatus());
  assertEquals("MISSING",CareerMatcher.match(claims,List.of(new CareerMatcher.Claim("Java","Java"))).get(0).matchStatus());
  assertThrows(IllegalArgumentException.class,()->CareerMatcher.validate(claims,"未填写经历"));
 }
}
