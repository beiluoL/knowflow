package com.knowflow.exam.engine;

import com.knowflow.entity.QuizQuestion;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 客观题规则判分引擎（纯函数、确定性，可单测）。
 *
 * <p>规则：
 * <ul>
 *   <li>单选/判断：归一化后精确匹配</li>
 *   <li>多选 ALL_OR_NOTHING：集合完全一致得满分，否则 0；
 *       PARTIAL_CREDIT：错选任一 0 分；少选按 选中正确数/正确总数 比例给分（四舍五入）</li>
 *   <li>填空：trim + 忽略大小写，支持以 | 分隔多个等价答案</li>
 *   <li>简答/主观题：不做关键词匹配冒充高级评分，返回 {@link GradeOutcome#pending()}，交 AI/人工</li>
 * </ul>
 */
public final class ExamGrader {

    public static final String POLICY_ALL_OR_NOTHING = "ALL_OR_NOTHING";
    public static final String POLICY_PARTIAL_CREDIT = "PARTIAL_CREDIT";

    private ExamGrader() {
    }

    /** 判分结果。pending=true 表示规则无法评分，score 必须为 null（不能记 0）。 */
    public record GradeOutcome(boolean pending, boolean correct, Integer score, Map<String, Object> detail) {

        public static GradeOutcome subjective() {
            return new GradeOutcome(true, false, null, Map.of("reason", "SUBJECTIVE_REQUIRES_AI"));
        }

        public static GradeOutcome rule(boolean correct, int score, Map<String, Object> detail) {
            return new GradeOutcome(false, correct, score, detail);
        }
    }

    /** 题型是否可由后端规则直接判分。 */
    public static boolean isObjective(String questionType) {
        return QuizQuestion.TYPE_SINGLE_CHOICE.equals(questionType)
                || QuizQuestion.TYPE_MULTIPLE_CHOICE.equals(questionType)
                || QuizQuestion.TYPE_TRUE_FALSE.equals(questionType)
                || QuizQuestion.TYPE_FILL_BLANK.equals(questionType);
    }

    /** CODE 题走代码判题网关（CodeJudgeGateway），既非规则判分也非 AI 评卷。 */
    public static boolean isCode(String questionType) {
        return QuizQuestion.TYPE_CODE.equals(questionType);
    }

    public static boolean requiresAi(String questionType) {
        // Phase 3G：CODE 由判题服务处理，必须显式排除，不能被当成 AI 主观题
        return QuizQuestion.TYPE_SHORT_ANSWER.equals(questionType);
    }

    /**
     * 规则判分。
     *
     * @param maxScore 本题满分（&gt;0）
     */
    public static GradeOutcome grade(String type, String standardAnswer, String userAnswerRaw,
                                     String scoringRule, int maxScore) {
        if (isCode(type)) {
            // 防御：CODE 题必须由代码判题网关处理，绝不允许退化成字符串精确匹配
            return new GradeOutcome(true, false, null, Map.of("reason", "CODE_REQUIRES_JUDGE"));
        }
        if (requiresAi(type)) {
            return GradeOutcome.subjective();
        }
        String userAnswer = userAnswerRaw == null ? "" : userAnswerRaw.trim();
        if (userAnswer.isEmpty()) {
            return GradeOutcome.rule(false, 0, Map.of("reason", "EMPTY_ANSWER"));
        }
        if (standardAnswer == null || standardAnswer.isBlank()) {
            // 题库缺陷：不能把脏数据当成用户答错
            return GradeOutcome.subjective();
        }
        return switch (type) {
            case QuizQuestion.TYPE_MULTIPLE_CHOICE -> gradeMultiple(standardAnswer, userAnswer, scoringRule, maxScore);
            case QuizQuestion.TYPE_FILL_BLANK -> gradeFill(standardAnswer, userAnswer, maxScore);
            // 单选、判断：索引/布尔精确匹配（忽略大小写与空白）
            default -> gradeExact(standardAnswer, userAnswer, maxScore);
        };
    }

    private static GradeOutcome gradeExact(String standard, String user, int maxScore) {
        boolean correct = standard.trim().equalsIgnoreCase(user.trim());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("rule", "EXACT_MATCH");
        detail.put("standard", standard.trim());
        return GradeOutcome.rule(correct, correct ? maxScore : 0, detail);
    }

    private static GradeOutcome gradeFill(String standard, String user, int maxScore) {
        Set<String> accepted = Arrays.stream(standard.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
        boolean correct = accepted.stream().anyMatch(a -> a.equalsIgnoreCase(user.trim()));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("rule", "FILL_NORMALIZED_MATCH");
        detail.put("acceptedCount", accepted.size());
        return GradeOutcome.rule(correct, correct ? maxScore : 0, detail);
    }

    private static GradeOutcome gradeMultiple(String standard, String user, String policy, int maxScore) {
        TreeSet<String> std = indexSet(standard);
        TreeSet<String> usr = indexSet(user);
        if (std.isEmpty()) {
            return GradeOutcome.subjective();
        }
        TreeSet<String> correctHits = new TreeSet<>(usr);
        correctHits.retainAll(std);
        TreeSet<String> wrongPicks = new TreeSet<>(usr);
        wrongPicks.removeAll(std);

        boolean exact = std.equals(usr);
        boolean partial = POLICY_PARTIAL_CREDIT.equals(policy);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("rule", partial ? "MULTI_PARTIAL_CREDIT" : "MULTI_ALL_OR_NOTHING");
        detail.put("correctHits", correctHits.size());
        detail.put("wrongPicks", wrongPicks.size());
        detail.put("required", std.size());

        if (exact) {
            detail.put("reason", "EXACT_SET");
            return GradeOutcome.rule(true, maxScore, detail);
        }
        // 错选：按规约一律 0 分（无论是否部分分策略）
        if (!wrongPicks.isEmpty()) {
            detail.put("reason", "WRONG_OPTION_SELECTED");
            return GradeOutcome.rule(false, 0, detail);
        }
        if (!partial) {
            detail.put("reason", "NOT_ALL_SELECTED");
            return GradeOutcome.rule(false, 0, detail);
        }
        if (correctHits.isEmpty()) {
            detail.put("reason", "NO_CORRECT_HIT");
            return GradeOutcome.rule(false, 0, detail);
        }
        // 少选部分得分：四舍五入，且不得因部分分超过满分
        int score = (int) Math.round(maxScore * (correctHits.size() * 1.0 / std.size()));
        score = Math.max(0, Math.min(maxScore - 1, score));
        detail.put("reason", "PARTIAL_CREDIT");
        return GradeOutcome.rule(false, score, detail);
    }

    /** "0, 2" → [0,2]，忽略空白与非法项。 */
    static TreeSet<String> indexSet(String s) {
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
