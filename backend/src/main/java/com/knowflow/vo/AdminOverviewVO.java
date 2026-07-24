package com.knowflow.vo;

import lombok.Data;

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
