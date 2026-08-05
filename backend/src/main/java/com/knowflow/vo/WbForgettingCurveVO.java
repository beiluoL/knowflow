package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 遗忘曲线可视化数据：按日期聚合复习日志，呈现每日复习量与遗忘率走势。
 * 遗忘率 = quality==0（完全忘记）的占比，用于直观反映记忆巩固效果。
 */
@Data
public class WbForgettingCurveVO {

    /** 统计起始日期（yyyy-MM-dd） */
    private String startDate;

    /** 统计结束日期（yyyy-MM-dd） */
    private String endDate;

    /** 每日数据点（按日期升序） */
    private List<Point> points;

    /** 复习总次数 */
    private Long totalReviews;

    /** 遗忘（quality==0）总次数 */
    private Long totalLapses;

    /** 整体遗忘率（0~1，保留分母避免除零） */
    private Double overallLapseRate;

    @Data
    public static class Point {
        /** 日期 yyyy-MM-dd */
        private String date;
        /** 当日复习次数 */
        private Integer reviews;
        /** 当日遗忘次数（quality==0） */
        private Integer lapses;
        /** 当日遗忘率 0~1 */
        private Double lapseRate;
        /** 当日新增复习卡片数（首次评分） */
        private Integer newCards;
    }
}
