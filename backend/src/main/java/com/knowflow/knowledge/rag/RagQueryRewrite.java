package com.knowflow.knowledge.rag;

import java.text.Normalizer;
import java.util.List;

/** 生成一条规范化补充查询，始终保留原始查询。 */
public final class RagQueryRewrite {
  private RagQueryRewrite() {}

  public static List<String> variants(String query) {
    String original = query.strip();
    String normalized =
        Normalizer.normalize(original, Normalizer.Form.NFKC).replaceAll("\\s+", " ").strip();
    return normalized.isEmpty() || normalized.equals(original)
        ? List.of(original)
        : List.of(original, normalized);
  }
}
