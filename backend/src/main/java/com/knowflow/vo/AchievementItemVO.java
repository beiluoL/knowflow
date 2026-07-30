package com.knowflow.vo;

import lombok.Data;

/**
 * 成就列表项 VO：包含是否解锁、当前进度、解锁时间等用户级信息。
 */
@Data
public class AchievementItemVO {

    private Long id;
    /** 成就编码 */
    private String code;
    /** 成就名称 */
    private String name;
    /** 成就描述 */
    private String description;
    /** 图标名 */
    private String icon;
    /** 分类：LEARNING/EXPLORATION/COMMUNITY/PERSISTENCE/SPECIAL */
    private String category;
    /** 是否已解锁 */
    private Boolean unlocked;
    /** 已有经验值奖励 */
    private Integer exp;
    /** 当前进度值 */
    private Integer current;
    /** 目标阈值 */
    private Integer target;
    /** 进度百分比 0-100 */
    private Integer percent;
    /** 解锁时间（unlocked 为 true 时） */
    private String unlockedTime;
    /** 奖励经验值 */
    private Integer rewardExp;
}
