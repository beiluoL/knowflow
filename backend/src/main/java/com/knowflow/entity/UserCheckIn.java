package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_check_in")
/** 每日打卡记录实体，记录用户打卡日期、连续天数与奖励。 */
public class UserCheckIn extends BaseEntity {

    /** 用户ID（逻辑外键 sys_user.id） */
    private Long userId;

    /** 打卡日期（自然日） */
    private LocalDate checkDate;

    /** 当日累计的连续打卡天数 */
    private Integer continuousDays;

    /** 本次打卡奖励经验值 */
    private Integer rewardExp;

    /** 本次打卡奖励精力值 */
    private Integer rewardEnergy;
}
