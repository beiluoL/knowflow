package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识点掌握度视图对象（Knowledge Mastery Engine，Phase 2-B）。
 * 前端看板 / 知识图谱节点复用。
 */
@Data
public class KnowledgeMasteryVO {

    /** 知识点ID（kg_entity.id）。 */
    private Long knowledgeId;

    /** 知识点名称。 */
    private String name;

    /** 知识点类型（kg_entity.type）。 */
    private String type;

    /** 归属分类ID。 */
    private Long categoryId;

    /** 归属分类名称（可选）。 */
    private String categoryName;

    /** 掌握度 0~100。 */
    private Integer masteryScore;

    /** 置信度 0~100。 */
    private Integer confidenceScore;

    /** 学习状态：NOT_STARTED / LEARNING / WEAK / MASTERED / REVIEW_REQUIRED。 */
    private String learningStatus;

    /** 遗忘风险 0~100。 */
    private Integer forgettingRisk;

    /** 薄弱类型（仅薄弱/需复习时有值）。 */
    private List<String> weaknessTypes;

    /** 薄弱原因（人类可读）。 */
    private String reason;

    /** 最近学习/互动时间。 */
    private LocalDateTime lastLearnedAt;

    /** 最近复习时间。 */
    private LocalDateTime lastReviewedAt;

    /** 最近测评时间。 */
    private LocalDateTime lastAssessedAt;

    /** 下次应复习时间。 */
    private LocalDateTime nextReviewAt;
}
