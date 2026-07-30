package com.knowflow.vo;

import lombok.Data;

/**
 * 编程挑战关卡提交结果：通关判定、星级、积分与解锁信息。
 */
@Data
public class ChallengeSubmitResultVO {

    /** 本次是否通关（全部用例通过） */
    private Boolean passed;

    /** 是否首次通关本关（首通才计星级与积分） */
    private Boolean firstPass;

    /** 本关获得星级 0-3 */
    private Integer stars;

    /** 本关获得积分 */
    private Integer pointsEarned;

    /** 本关累计提交次数 */
    private Integer attempts;

    /** 通过用例数 / 用例总数 */
    private Integer passCount;

    private Integer total;

    // ------ 赛道整体进度 ------

    /** 赛道累计星星 */
    private Integer totalStars;

    /** 赛道累计积分 */
    private Integer totalPoints;

    /** 已通关关卡数 */
    private Integer clearedLevels;

    /** 是否解锁了下一关 */
    private Boolean unlockedNext;

    /** 下一关序号（已是最后一关则为 null） */
    private Integer nextLevelNo;

    /** 是否已通关整个赛道 */
    private Boolean challengeCompleted;
}
