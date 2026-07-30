package com.knowflow.vo;

import lombok.Data;

/**
 * 编程挑战赛道列表项：赛道基础信息 + 当前用户进度概览（未登录时进度字段为默认值）。
 */
@Data
public class ChallengeVO {

    private Long id;

    private String title;

    private String description;

    /** 主语言：javascript / typescript / python / java / sql */
    private String language;

    /** 难度：0 简单 / 1 中等 / 2 困难 */
    private Integer difficulty;

    /** 图标名（lucide 图标） */
    private String icon;

    /** 主题色（十六进制） */
    private String themeColor;

    /** 标签，逗号分隔 */
    private String tags;

    /** 关卡总数 */
    private Integer levelCount;

    /** 满分积分 */
    private Integer totalPoints;

    /** 参与人数 */
    private Integer playerCount;

    // ------ 以下为当前用户进度 ------

    /** 是否已参与（存在进度记录） */
    private Boolean joined;

    /** 已通关关卡数 */
    private Integer clearedLevels;

    /** 已获得积分 */
    private Integer earnedPoints;

    /** 已获得星星数 */
    private Integer earnedStars;

    /** 是否已通关整个赛道 */
    private Boolean completed;

    /** 进度百分比（0-100） */
    private Integer progressPercent;
}
