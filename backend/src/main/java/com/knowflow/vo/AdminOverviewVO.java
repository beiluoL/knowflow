package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 管理后台概览视图对象，聚合平台总量与当日新增统计数据。
 */
@Data
public class AdminOverviewVO {

    private Long totalUsers;

    private Long totalDocs;

    private Long totalCategories;

    private Long totalConversations;

    private Long totalLearningPaths;

    private Long todayActiveUsers;

    private Long todayNewUsers;

    private Long todayNewDocs;

    /** 平台最早注册用户的日期（yyyy-MM-dd），用于展示运营起始时间。 */
    private String firstUserDate;

    /** 最近 7 天用户增长趋势（含当日新增与累计）。 */
    private List<UserGrowthPoint> userGrowth;

    /** 基于真实业务数据计算的内容健康度指标。 */
    private List<HealthMetric> healthMetrics;

    /** 平台最近活动流（社区发帖与用户注册等真实动态）。 */
    private List<RecentActivity> recentActivities;
}
