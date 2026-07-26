package com.knowflow.vo;

import lombok.Data;

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
}
