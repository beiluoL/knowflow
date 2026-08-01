package com.knowflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 学习报告视图对象：聚合指定周期内的签到、闪卡、错题、代码、阅读、测验等学习数据。
 * 周期 period 取值：week（最近7天）/ month（最近30天）/ all（不限，使用 2000-01-01 起算）。
 */
@Data
public class LearningReportVO implements Serializable {

    /** 周期标识：week / month / all */
    private String period;

    /** 起始日期（yyyy-MM-dd，含） */
    private String startDate;

    /** 结束日期（yyyy-MM-dd，含） */
    private String endDate;

    /** 签到天数 */
    private int checkinDays;

    /** 当前连续打卡天数 */
    private int continuousDays;

    /** 闪卡复习次数（已复习次数求和） */
    private int flashcardReviewed;

    /** 已掌握闪卡数（review_count>=3 且 next_review_time 在未来） */
    private int flashcardMastered;

    /** 错题总数 */
    private int mistakeCount;

    /** 已掌握错题数（mastered=1） */
    private int mistakeMastered;

    /** 代码提交次数 */
    private int codeSubmissions;

    /** 代码通过次数（passed=1） */
    private int codePassed;

    /** 阅读文档数（progress>0） */
    private int docsRead;

    /** 测验答题数 */
    private int quizAnswered;

    /** 测验答对数（is_correct=1） */
    private int quizCorrect;

    /** 学习时长（分钟，doc_read_progress.read_seconds 求和 /60） */
    private int studyMinutes;

    /** 每日学习活跃度（最近30天，用于柱状图） */
    private List<DailyItem> dailyActivity;

    /** 各知识库掌握度 Top 5 */
    private List<CategoryItem> categoryMastery;

    /** 最近8周趋势 */
    private List<WeeklyItem> weeklyTrend;

    /** 每日学习活跃度项。 */
    @Data
    public static class DailyItem implements Serializable {
        /** 日期（yyyy-MM-dd） */
        private String date;
        /** 当日学习分钟数 */
        private int minutes;
        /** 当日学习活动计数 */
        private int count;
    }

    /** 知识库掌握度项。 */
    @Data
    public static class CategoryItem implements Serializable {
        /** 分类名称 */
        private String categoryName;
        /** 总数 */
        private int total;
        /** 已掌握数 */
        private int mastered;
        /** 掌握百分比（0-100） */
        private int percent;
    }

    /** 周趋势项。 */
    @Data
    public static class WeeklyItem implements Serializable {
        /** 周起始日期（yyyy-MM-dd） */
        private String weekStart;
        /** 当周学习分钟数 */
        private int studyMinutes;
        /** 当周签到天数 */
        private int checkinDays;
    }
}
