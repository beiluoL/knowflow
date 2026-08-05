package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库工作台·复习日志（知识复习模块）。
 * 每次抽查的评分流水，用于遗忘曲线可视化与学习报告统计。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_review_log")
public class WbReviewLog extends BaseEntity {

    /** 所属用户ID */
    private Long userId;

    /** 复习卡片ID（逻辑外键 wb_review_card.id） */
    private Long cardId;

    /** 用户反馈评分：0 完全忘记 / 1 困难 / 2 一般 / 3 容易（映射 SM-2） */
    private Integer quality;

    /** 本次评分后计算出的新间隔（天） */
    private Integer intervalDay;

    /** 本次评分后的难度系数（放大100倍） */
    private Integer easeFactor;

    /** 本次作答耗时（毫秒） */
    private Long costMs;
}
