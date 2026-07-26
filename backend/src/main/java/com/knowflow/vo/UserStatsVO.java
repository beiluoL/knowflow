package com.knowflow.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户学习统计视图对象，聚合学习时长、阅读量与等级经验等数据。
 */
@Data
public class UserStatsVO {

    private Long userId;

    private BigDecimal totalStudyHours;

    private Integer readDocsCount;

    private Integer streakDays;

    private Integer favoriteCount;

    /** 用户等级 */
    private Integer level;

    /** 经验值 */
    private Integer exp;

    /** 精力值（能量） */
    private Integer energy;

    private Integer completedPaths;

    private Integer totalFlashcards;
}
