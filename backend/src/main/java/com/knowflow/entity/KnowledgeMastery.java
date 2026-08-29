package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 知识点掌握度（Knowledge Mastery Engine，Phase 2-B）。
 * <p>
 * 以 {@code (user_id, knowledge_id)} 为唯一维度，knowledge_id 指向 {@code kg_entity.id}（仅可学习类型）。
 * 掌握度由多信号加权、有效权重归一化计算得到，整行重算保证幂等（不累计增量）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_mastery")
public class KnowledgeMastery extends BaseEntity {

    /** 所属用户ID（逻辑外键 sys_user.id） */
    private Long userId;

    /** 知识点ID（逻辑外键 kg_entity.id） */
    private Long knowledgeId;

    /** 掌握度 0~100 */
    private Integer masteryScore;

    /** 置信度 0~100（有效样本数饱和 N/(N+K)*100） */
    private Integer confidenceScore;

    /** 学习状态：NOT_STARTED / LEARNING / WEAK / MASTERED / REVIEW_REQUIRED */
    private String learningStatus;

    /** Quiz 答对累计 */
    private Integer correctCount;

    /** Quiz 答错累计 */
    private Integer wrongCount;

    /** Quiz 作答次数累计 */
    private Integer attemptCount;

    /** 复习（SM-2 抽查）次数累计 */
    private Integer reviewCount;

    /** 主动回忆会话次数累计 */
    private Integer recallCount;

    /** 主动回忆平均得分 0~100 */
    private Integer recallAvgScore;

    /** 代码提交次数累计 */
    private Integer codingAttemptCount;

    /** 代码完全通过次数累计 */
    private Integer codingPassCount;

    /** 关联错题数累计 */
    private Integer mistakeCount;

    /** 已掌握错题数累计 */
    private Integer mistakeMastered;

    /** 当前连续答对（Quiz 尾部游程） */
    private Integer consecutiveCorrect;

    /** 当前连续答错（Quiz 尾部游程） */
    private Integer consecutiveWrong;

    /** 最近学习/互动时间 */
    private LocalDateTime lastLearnedAt;

    /** 最近复习时间（复习/回忆/闪卡/阅读） */
    private LocalDateTime lastReviewedAt;

    /** 最近测评时间（Quiz/代码/错题） */
    private LocalDateTime lastAssessedAt;

    /** 下次应复习时间（来自 SM-2/回忆排程） */
    private LocalDateTime nextReviewAt;

    /** 遗忘风险 0~100 */
    private Integer forgettingRisk;
}
