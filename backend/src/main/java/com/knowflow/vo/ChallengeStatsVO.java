package com.knowflow.vo;

import lombok.Data;

/**
 * 当前用户编程挑战累计统计：用于挑战首页个人战绩卡片。
 */
@Data
public class ChallengeStatsVO {

    /** 参与的赛道数 */
    private Integer joinedChallenges;

    /** 已通关的赛道数 */
    private Integer completedChallenges;

    /** 累计通关关卡数 */
    private Integer clearedLevels;

    /** 累计获得积分 */
    private Integer totalPoints;

    /** 累计获得星星 */
    private Integer totalStars;

    /** 总榜名次（按累计积分，未上榜为 null） */
    private Integer myRank;
}
