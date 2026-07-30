package com.knowflow.vo;

import lombok.Data;

/**
 * 编程挑战排行榜条目：按积分（同分按星星）排序。
 */
@Data
public class ChallengeRankVO {

    /** 名次，从 1 开始 */
    private Integer rank;

    private Long userId;

    private String nickname;

    private String avatar;

    /** 累计积分 */
    private Integer totalPoints;

    /** 累计星星 */
    private Integer totalStars;

    /** 已通关关卡数 */
    private Integer clearedLevels;
}
