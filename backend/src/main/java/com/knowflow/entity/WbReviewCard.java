package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 知识库工作台·间隔重复卡片（知识复习模块）。
 * 基于 SM-2 算法调度：ease_factor 难度系数（放大100倍存储）、repetitions 连续答对、
 * interval_day 间隔天数。next_review_time 驱动遗忘曲线提醒。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_review_card")
public class WbReviewCard extends BaseEntity {

    /** 所属用户ID */
    private Long userId;

    /** 来源收集箱条目ID（逻辑外键 wb_capture.id） */
    private Long captureId;

    /** 来源康奈尔笔记ID（逻辑外键 wb_note.id） */
    private Long noteId;

    /** 归属知识库/分类ID（逻辑外键 doc_category.id） */
    private Long categoryId;

    /** 卡片正面：问题/线索 */
    private String front;

    /** 卡片背面：答案/解释 */
    private String back;

    /** 卡片类型：BASIC 问答 / CLOZE 挖空 / RECALL 主动回忆 */
    private String cardType;

    /** SM-2 难度系数（放大100倍存储，默认250即2.5） */
    private Integer easeFactor;

    /** SM-2 连续答对次数，答错归零 */
    private Integer repetitions;

    /** 当前复习间隔（天） */
    private Integer intervalDay;

    /** 累计复习次数 */
    private Integer reviewCount;

    /** 遗忘次数（评分低于及格线） */
    private Integer lapseCount;

    /** 下次应复习时间（遗忘曲线提醒依据） */
    private LocalDateTime nextReviewTime;

    /** 上次复习时间 */
    private LocalDateTime lastReviewTime;

    /** 是否暂停复习：1 暂停 / 0 正常 */
    private Integer suspended;
}
