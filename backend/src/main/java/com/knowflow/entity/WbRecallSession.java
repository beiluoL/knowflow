package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 学习工作台·主动回忆会话（知识复习模块扩展）。
 * 三轮闭卷默写流程：1 即时默写 / 2 补漏默写 / 3 1小时后复测。
 * source_text 为原文，roundX_text 为每轮默写内容，roundX_score 为比对得分（0-100）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_recall_session")
public class WbRecallSession extends BaseEntity {

    /** 所属用户ID */
    private Long userId;

    /** 关联笔记ID（逻辑外键 wb_note.id），可空 */
    private Long noteId;

    /** 关联复习卡ID（逻辑外键 wb_review_card.id），可空 */
    private Long cardId;

    /** 标题（冗余自笔记/卡片，便于列表展示） */
    private String title;

    /** 原文（默写对照基准） */
    private String sourceText;

    /** 第一轮：即时默写内容 */
    private String round1Text;

    /** 第一轮得分（0-100） */
    private Integer round1Score;

    /** 第二轮：补漏默写内容 */
    private String round2Text;

    /** 第二轮得分（0-100） */
    private Integer round2Score;

    /** 第三轮：1小时后复测内容 */
    private String round3Text;

    /** 第三轮得分（0-100） */
    private Integer round3Score;

    /** 当前轮次：1 / 2 / 3 */
    private Integer currentRound;

    /** 会话状态：IN_PROGRESS 进行中 / COMPLETED 已完成 */
    private String status;

    /** 第三轮预定复测时间（1小时后），用于提醒 */
    private LocalDateTime round3DueTime;

    /** 完成时间 */
    private LocalDateTime completedTime;
}
