package com.knowflow.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UserStatsVO {

    private Long userId;

    private BigDecimal totalStudyHours;

    private Integer readDocsCount;

    private Integer streakDays;

    private Integer favoriteCount;

    private Integer level;

    private Integer exp;

    private Integer energy;

    private Integer completedPaths;

    private Integer totalFlashcards;
}
