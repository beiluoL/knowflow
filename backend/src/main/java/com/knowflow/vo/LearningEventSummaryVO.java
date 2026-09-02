package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 学习事件统计摘要（Phase 1）：一次性聚合「今日事件数 + 近 7 日各维度统计」，
 * 供学习看板 / AI 诊断 / 学习计划消费。统计口径与 learning_event 统一，不再各模块自算。
 */
@Data
public class LearningEventSummaryVO {

    /** 今日学习事件数 */
    private int todayEventCount;

    /** 近 7 日答题数 */
    private int quizAnswered7d;

    /** 近 7 日答对数 */
    private int quizCorrect7d;

    /** 近 7 日答题正确率（0~1，无答题时为 0） */
    private double quizAccuracy7d;

    /** 近 7 日闪卡复习数 */
    private int flashcardReviewed7d;

    /** 近 7 日代码提交数 */
    private int codeSubmitted7d;

    /** 近 7 日错题复习数（标记已掌握） */
    private int mistakesReviewed7d;

    /** 近 7 日学习任务完成数 */
    private int tasksCompleted7d;

    /** 最近 10 条事件类型（按最近发生去重，用于行为流展示） */
    private List<String> recentTypes;
}
