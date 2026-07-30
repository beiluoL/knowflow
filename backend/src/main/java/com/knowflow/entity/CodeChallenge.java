package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编程挑战赛道实体：一个赛道由若干关卡组成（如「JavaScript 十题闯关」）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("code_challenge")
public class CodeChallenge extends BaseEntity {

    /** 挑战标题 */
    private String title;

    /** 挑战简介 */
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

    /** 满分积分（各关卡积分之和） */
    private Integer totalPoints;

    /** 排序值，越小越靠前 */
    private Integer sortOrder;

    /** 状态：0 草稿 / 1 已发布 */
    private Integer status;
}
