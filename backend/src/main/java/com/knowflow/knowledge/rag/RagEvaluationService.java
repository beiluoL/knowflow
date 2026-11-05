package com.knowflow.knowledge.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** 对版本化公开候选运行确定性的离线重排评测。 */
@Service
public class RagEvaluationService {
  private final Dataset dataset;

  public RagEvaluationService(ObjectMapper mapper) throws IOException {
    try (var source = new ClassPathResource("rag/golden-v1.json").getInputStream()) {
      dataset = mapper.readValue(source, Dataset.class);
    }
  }

  /** 返回固定 K 的基线与重排结果。 */
  public Report evaluate() {
    return new Report(
        dataset.version(), 2, dataset.cases().stream().map(this::evaluateCase).toList());
  }

  private CaseResult evaluateCase(Case test) {
    List<String> variants = RagQueryRewrite.variants(test.query());
    var ranked =
        RagCoverageReranker.rerank(
            variants.get(variants.size() - 1), test.candidates(), Candidate::text);
    return new CaseResult(
        test.id(), metrics(test.candidates(), test.relevant()), metrics(ranked, test.relevant()));
  }

  private Metrics metrics(List<Candidate> candidates, Set<String> relevant) {
    int hits = 0;
    double reciprocal = 0;
    for (int index = 0; index < Math.min(2, candidates.size()); index++) {
      if (relevant.contains(candidates.get(index).id())) {
        if (hits == 0) reciprocal = 1.0 / (index + 1);
        hits++;
      }
    }
    return new Metrics((double) hits / relevant.size(), reciprocal);
  }

  public record Dataset(String version, List<Case> cases) {}

  public record Case(String id, String query, Set<String> relevant, List<Candidate> candidates) {}

  public record Candidate(String id, String text) {}

  public record Metrics(double recall, double reciprocalRank) {}

  public record CaseResult(String id, Metrics baseline, Metrics reranked) {}

  public record Report(String datasetVersion, int k, List<CaseResult> cases) {}
}
