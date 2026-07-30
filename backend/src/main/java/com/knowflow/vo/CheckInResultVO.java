package com.knowflow.vo;

import lombok.Data;

/**
 * 每日打卡结果视图对象：本次打卡的连续天数与奖励。
 */
@Data
public class CheckInResultVO {

    /** 本次打卡后是否已打卡（true 表示成功或今日已打过） */
    private Boolean checkedToday;

    /** 本次是否为重复打卡（今日此前已打过卡） */
    private Boolean alreadyChecked;

    /** 打卡后的连续天数 */
    private Integer continuousDays;

    /** 本次奖励经验值 */
    private Integer rewardExp;

    /** 本次奖励精力值 */
    private Integer rewardEnergy;
}
