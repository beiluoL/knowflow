package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户学习宠物实体。
 * 每个用户拥有一只陪伴宠物，通过喂食/玩耍/专注积累经验并升级。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_pet")
public class UserPet extends BaseEntity {
    /** 用户ID（逻辑外键 sys_user.id） */
    private Long userId;
    /** 宠物名称 */
    private String name;
    /** 等级 */
    private Integer level;
    /** 心情 */
    private String mood;
    /** 体力值 0-100 */
    private Integer energy;
    /** 当前经验值 */
    private Integer exp;
    /** 当前等级经验上限 */
    private Integer maxExp;
    /** 宠物头像标识 */
    private String avatar;
    /** 累计专注分钟 */
    private Integer totalFocusMinutes;
    /** 累计番茄数 */
    private Integer totalPomodoros;
}
