package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 成就页面整体 VO：成就列表 + 概览统计 + 最近解锁时间线。
 */
@Data
public class AchievementPageVO {

    /** 成就列表 */
    private List<AchievementItemVO> achievements;
    /** 已解锁数 */
    private Integer unlockedCount;
    /** 成就总数 */
    private Integer totalCount;
    /** 总进度百分比 */
    private Integer totalPercent;
    /** 成就系统累计获得 EXP */
    private Integer totalAchievementExp;
    /** 最近解锁时间线 */
    private List<RecentUnlockVO> recentUnlocks;

    /** 最近解锁条目 */
    @Data
    public static class RecentUnlockVO {
        private Long achievementId;
        private String name;
        private String description;
        private String icon;
        private String category;
        private Integer exp;
        /** 相对时间描述，如「2 小时前」「昨天」 */
        private String timeAgo;
    }
}
