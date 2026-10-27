package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * Phase 3H 统一学习分析视图。
 *
 * <p><b>计算口径（与 LEARNING_ANALYTICS_DESIGN.md 一致）</b>：</p>
 * <ul>
 *   <li>练习正确率分母只含已判分记录（is_correct 非空）；未评分主观题进入 ungradedCount，绝不计入正确率。</li>
 *   <li>代码题 Judge 平台失败（SYSTEM_ERROR / JUDGE_UNAVAILABLE）进入 excludedPlatformFailure，
 *       既不算用户错误也不影响首次通过率。</li>
 *   <li>首次通过率 = 每题最早一条正式提交即为 ACCEPTED 的题数 / 有正式提交的题数。</li>
 * </ul>
 */
@Data
public class LearningAnalyticsVO {

    private Integer windowDays;

    private QuizSummary quiz;
    private CodeSummary code;
    private MistakeSummary mistake;
    private List<Bucket> byType;
    private List<Bucket> byTopic;
    private List<DayPoint> dailyTrend;

    @Data
    public static class QuizSummary {
        /** 窗口内作答总条数（含未评分）。 */
        private long totalAnswered;
        /** 已判分条数（正确率分母）。 */
        private long gradedCount;
        private long correctCount;
        /** 百分比 0-100，保留一位小数；无已判分记录为 null。 */
        private Double correctRate;
        /** 未评分（is_correct 为空）条数，不计入正确率。 */
        private long ungradedCount;
        /** 平均作答耗时（秒，仅统计 time_cost 非空记录）。 */
        private Double avgTimeCostSeconds;
    }

    @Data
    public static class CodeSummary {
        /** 正式提交（submission_type=SUBMIT）总数。 */
        private long totalSubmissions;
        private long acceptedSubmissionCount;
        /** 有正式提交的不同题目数。 */
        private long attemptedQuestions;
        /** 首次正式提交即通过的题目数。 */
        private long firstPassQuestions;
        /** 首次通过率（0-100）。 */
        private Double firstPassRate;
        /** 判题结果分布（只含用户侧终态：AC 与各类用户失败）。 */
        private List<Bucket> verdictDistribution;
        /** Judge 平台失败提交数（SYSTEM_ERROR/JUDGE_UNAVAILABLE），不计入用户失败。 */
        private long excludedPlatformFailure;
    }

    @Data
    public static class MistakeSummary {
        private long total;
        private long pending;
        private long mastered;
        private long dueToday;
        private long weeklyNew;
        private Double avgReviewCount;
        private Integer maxReviewCount;
    }

    @Data
    public static class Bucket {
        private String key;
        private String label;
        private long total;
        private long correct;
        private Double rate;

        public Bucket() {
        }

        public Bucket(String key, String label, long total, long correct) {
            this.key = key;
            this.label = label;
            this.total = total;
            this.correct = correct;
            this.rate = total == 0 ? null : Math.round(correct * 1000.0 / total) / 10.0;
        }
    }

    @Data
    public static class DayPoint {
        private String date;
        private long answered;
        private long correct;
        private Double rate;
    }
}
