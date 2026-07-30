package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 编程挑战赛道详情：赛道信息 + 全部关卡（含用户各关状态）+ 用户整体进度。
 */
@Data
public class ChallengeDetailVO {

    private Long id;

    private String title;

    private String description;

    private String language;

    private Integer difficulty;

    private String icon;

    private String themeColor;

    private String tags;

    private Integer levelCount;

    private Integer totalPoints;

    // ------ 当前用户整体进度 ------

    /** 是否已参与 */
    private Boolean joined;

    /** 已通关关卡数 */
    private Integer clearedLevels;

    /** 当前解锁到的关卡序号 */
    private Integer currentLevel;

    /** 已获得积分 */
    private Integer earnedPoints;

    /** 已获得星星数 */
    private Integer earnedStars;

    /** 是否已通关整个赛道 */
    private Boolean completed;

    /** 关卡列表（按 levelNo 升序） */
    private List<ChallengeLevelVO> levels;
}
