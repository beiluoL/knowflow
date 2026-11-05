package com.knowflow.knowledge.rag;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** 在融合候选内按查询词覆盖排序，同分保持原有融合顺序。 */
public final class RagCoverageReranker {
  private RagCoverageReranker() {}

  public static <T> List<T> rerank(String query, List<T> candidates, Function<T, String> content) {
    var terms =
        Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
            .filter(term -> !term.isBlank())
            .distinct()
            .limit(16)
            .toList();
    return candidates.stream()
        .map(candidate -> new Ranked<>(candidate, coverage(terms, content.apply(candidate))))
        .sorted(Comparator.comparingLong((Ranked<T> value) -> value.coverage()).reversed())
        .map(Ranked::candidate)
        .toList();
  }

  private static long coverage(List<String> terms, String text) {
    String normalized = text.toLowerCase(Locale.ROOT);
    return terms.stream().filter(normalized::contains).count();
  }

  private record Ranked<T>(T candidate, long coverage) {}
}
