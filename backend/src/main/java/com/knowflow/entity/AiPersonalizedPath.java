package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 个性化学习路径缓存实体。
 * 按 (user_id, goal, level, daily_minutes) 维度缓存，避免重复调用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_personalized_path")
public class AiPersonalizedPath extends BaseEntity {

    /** 所属用户ID。 */
    private Long userId;

    /** 学习目标。 */
    private String goal;

    /** 当前水平：入门/进阶/高级。 */
    private String level;

    /** 每日学习时长（分钟）。 */
    private Integer dailyMinutes;

    /** 推荐路径标题。 */
    private String title;

    /** 推荐理由。 */
    private String reason;

    /** 预计总时长（分钟）。 */
    private Integer totalDuration;

    /** 学习目标列表（JSON 数组字符串）。 */
    private String goalsText;

    /** 章节规划（JSON 数组字符串）。 */
    private String chaptersText;

    /** AI 学习建议。 */
    private String advice;

    /** 关联已有路径ID（可为空）。 */
    private Long relatedPathId;
}
