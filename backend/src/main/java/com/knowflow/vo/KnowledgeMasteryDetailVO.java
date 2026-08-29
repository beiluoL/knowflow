package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识点掌握度详情（Knowledge Mastery Engine，Phase 2-B）。
 * 在 {@link KnowledgeMasteryVO} 基础上补充可解释说明、信号明细与原始计数器。
 */
@Data
public class KnowledgeMasteryDetailVO {

    private Long knowledgeId;
    private String name;
    private String type;
    private String description;
    private Long categoryId;
    private String categoryName;

    private Integer masteryScore;
    private Integer confidenceScore;
    private String learningStatus;
    private Integer forgettingRisk;
    private List<String> weaknessTypes;
    private String reason;

    /** 结构化掌握度解释（人类可读）。 */
    private String explanation;

    /** 各维度信号贡献。 */
    private List<SignalContributionVO> signals;

    // ===== 原始计数器（便于前端/调试）=====
    private Integer correctCount;
    private Integer wrongCount;
    private Integer attemptCount;
    private Integer reviewCount;
    private Integer recallCount;
    private Integer recallAvgScore;
    private Integer codingAttemptCount;
    private Integer codingPassCount;
    private Integer mistakeCount;
    private Integer mistakeMastered;
    private Integer consecutiveCorrect;
    private Integer consecutiveWrong;

    private LocalDateTime lastLearnedAt;
    private LocalDateTime lastReviewedAt;
    private LocalDateTime lastAssessedAt;
    private LocalDateTime nextReviewAt;
}
