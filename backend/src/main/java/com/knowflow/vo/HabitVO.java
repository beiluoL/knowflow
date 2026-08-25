package com.knowflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 习惯视图对象：含今日打卡次数、连续天数、最佳记录与近期进度（用于可视化）。
 */
@Data
public class HabitVO {

    private Long id;

    private String name;

    private String description;

    private String icon;

    private String color;

    /** daily 每日 / weekly 每周 */
    private String frequency;

    private Integer targetCount;

    /** 提醒时间 HH:mm */
    private String reminderTime;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    private Integer active;

    private Integer sortOrder;

    /** 今日已打卡次数 */
    private Integer todayCount;

    /** 今日是否已达标 */
    private Boolean completedToday;

    /** 当前连续达标天数/周数 */
    private Integer streak;

    /** 历史最佳连续天数/周数 */
    private Integer bestStreak;

    /** 累计达标天数 */
    private Integer totalDays;

    /** 近 7 天每日打卡情况（进度可视化） */
    private List<DayProgress> weekly;

    /** 近 30 天每日打卡情况（热力图） */
    private List<DayProgress> monthly;

    /** 单日打卡进度（当日次数 / 目标） */
    @Data
    public static class DayProgress {
        /** 日期 yyyy-MM-dd */
        private String date;
        /** 当日打卡次数 */
        private Integer count;
        /** 是否达标 */
        private Boolean completed;
    }
}
