package com.knowflow.interview.radar;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RadarRubricTest {
 @Test void exactFourDimensionScaleAndPassBoundary(){
  assertEquals(10.0,RadarRubric.grade(Map.of("TECHNICAL_CORRECTNESS",100,"KNOWLEDGE_COVERAGE",100,"ENGINEERING_PRACTICE",100,"EXPRESSION_STRUCTURE",100)).score());
  assertEquals(8.5,RadarRubric.grade(Map.of("TECHNICAL_CORRECTNESS",100,"KNOWLEDGE_COVERAGE",50,"ENGINEERING_PRACTICE",100,"EXPRESSION_STRUCTURE",100)).score());
  assertTrue(RadarRubric.grade(Map.of("TECHNICAL_CORRECTNESS",100,"KNOWLEDGE_COVERAGE",50,"ENGINEERING_PRACTICE",100,"EXPRESSION_STRUCTURE",100)).passed());
  assertEquals("L0",RadarRubric.grade(Map.of("TECHNICAL_CORRECTNESS",0,"KNOWLEDGE_COVERAGE",0,"ENGINEERING_PRACTICE",0,"EXPRESSION_STRUCTURE",0)).level());
 }
 @Test void missingEvidenceIsNotZeroAndDoesNotPass(){
  var dimensions=new HashMap<String,Object>();dimensions.put("TECHNICAL_CORRECTNESS",100);
  var grade=RadarRubric.grade(dimensions);assertNull(grade.score());assertFalse(grade.passed());assertNull(grade.level());
 }
 @Test void invalidScoresAreRejectedRatherThanClamped(){
  assertThrows(IllegalArgumentException.class,()->RadarRubric.grade(Map.of("TECHNICAL_CORRECTNESS",101,"KNOWLEDGE_COVERAGE",100,"ENGINEERING_PRACTICE",100,"EXPRESSION_STRUCTURE",100)));
 }
}
