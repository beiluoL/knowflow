package com.knowflow.knowledge.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RagEvaluationServiceTest {
  @Test
  void versionedCasesCompareBaselineAndRerankedOrder() throws Exception {
    var report = new RagEvaluationService(new ObjectMapper()).evaluate();
    assertEquals("rag-golden-v1", report.datasetVersion());
    assertEquals(3, report.cases().size());
    assertTrue(
        report.cases().stream()
            .allMatch(
                result -> result.reranked().reciprocalRank() > result.baseline().reciprocalRank()));
  }
}
