package com.knowflow.interview.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 面试报告派生聚合：只从已校验的轮次评价中提炼薄弱点、优势与改进建议。
 *
 * <p>纯函数、不访问数据库、不调用模型；没有证据的维度不补默认分数，也不生成知识点到章节的假映射。</p>
 */
public final class InterviewReportAggregator {

    private static final Logger log = LoggerFactory.getLogger(InterviewReportAggregator.class);

    private InterviewReportAggregator() {
    }

    public record Result(
            List<Map<String, Object>> weaknesses,
            List<String> strengths,
            List<String> improvements,
            List<String> missedKnowledgePoints,
            List<Long> recommendedPracticeQuestionIds) {
    }

    /**
     * @param turns 已确认轮次；只读取 questionId 与已校验的 evaluation
     * @param dimensionMeans 当前报告的维度均分，缺失维度为 null
     */
    public static Result aggregate(List<Map<String, Object>> turns, Map<String, Object> dimensionMeans) {
        List<String> gaps = new ArrayList<>();
        List<String> strengths = new ArrayList<>();
        List<String> improvements = new ArrayList<>();
        List<Long> practice = new ArrayList<>();
        if (turns == null) return new Result(List.of(), List.of(), List.of(), List.of(), List.of());

        for (Map<String, Object> turn : turns) {
            Object evaluation = turn.get("evaluation");
            if (!(evaluation instanceof Map<?, ?> raw)) continue;
            if (raw.get("gaps") instanceof List<?> list) {
                for (Object item : list) {
                    String text = item == null ? "" : String.valueOf(item).trim();
                    if (!text.isEmpty()) gaps.add(text);
                }
            }
            if (raw.get("strengths") instanceof List<?> list) {
                for (Object item : list) {
                    String text = item == null ? "" : String.valueOf(item).trim();
                    if (!text.isEmpty() && !strengths.contains(text)) strengths.add(text);
                }
            }
            if (raw.get("suggestion") != null) {
                String text = String.valueOf(raw.get("suggestion")).trim();
                if (!text.isEmpty() && improvements.size() < 5 && !improvements.contains(text)) improvements.add(text);
            }
            if (lowValue(raw) && turn.get("questionId") != null) {
                try {
                    long id = Long.parseLong(String.valueOf(turn.get("questionId")));
                    if (!practice.contains(id)) practice.add(id);
                } catch (NumberFormatException e) {
                    // 非数字题号（如外部导入题）不伪造可跳转 ID。
                    log.debug("忽略非数字题号：{}", turn.get("questionId"));
                }
            }
        }

        List<String> missed = new ArrayList<>();
        Map<String, Integer> frequency = new LinkedHashMap<>();
        for (String gap : gaps) {
            frequency.merge(gap, 1, Integer::sum);
            if (!missed.contains(gap)) missed.add(gap);
        }

        List<Map<String, Object>> weaknesses = new ArrayList<>();
        for (String gap : missed) {
            int count = frequency.getOrDefault(gap, 1);
            weaknesses.add(weakness("KNOWLEDGE_GAP", gap, count >= 2 ? "P1" : "P2", count, null));
        }
        if (dimensionMeans != null) {
            for (Map.Entry<String, Object> entry : dimensionMeans.entrySet()) {
                if (!(entry.getValue() instanceof Number number)) continue;
                double score = number.doubleValue();
                if (score >= 75) continue;
                String priority = score < 50 ? "P0" : (score < 65 ? "P1" : "P2");
                weaknesses.add(weakness("DIMENSION", entry.getKey(), priority, null, score));
            }
        }
        weaknesses.sort(Comparator
                .comparingInt((Map<String, Object> item) -> "P0".equals(item.get("priority")) ? 0
                        : "P1".equals(item.get("priority")) ? 1 : 2)
                .thenComparing(item -> String.valueOf(item.get("title"))));
        if (weaknesses.size() > 12) weaknesses = weaknesses.subList(0, 12);

        return new Result(List.copyOf(weaknesses), List.copyOf(strengths), List.copyOf(improvements),
                List.copyOf(missed), List.copyOf(practice));
    }

    private static Map<String, Object> weakness(String type, String title, String priority, Integer count, Double score) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("title", title);
        item.put("priority", priority);
        if (count != null) item.put("occurrences", count);
        if (score != null) item.put("score", Math.round(score * 10) / 10.0);
        return item;
    }

    /** 维度均分低于 65 才视为需要补练；分数缺失时不凭猜测加入补练题。 */
    private static boolean lowValue(Map<?, ?> evaluation) {
        if (!(evaluation.get("dimensions") instanceof Map<?, ?> dims)) return false;
        List<Double> values = new ArrayList<>();
        for (Object value : dims.values()) {
            if (value instanceof Number number) values.add(number.doubleValue());
        }
        return !values.isEmpty() && values.stream().mapToDouble(d -> d).average().orElse(100) < 65;
    }
}
