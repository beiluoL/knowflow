package com.knowflow.dto;

import lombok.Data;

/**
 * AI 学习内容生成请求（Architecture-4.4 Learning Content Generator）。
 *
 * <p>以一条知识单元（kg_entity.id）为输入，生成 Flashcard / Feynman / Scenario Draft。
 * Draft 是「内存 DTO」，默认不落任何库；只有用户审核通过后经 accept 才写入既有正式实体。
 * goalId / learningNodeId 是可选上下文：只用于内容主题对齐与（flashcard 的）章节挂载，
 * 若无法真实取得前端不应传值（禁止猜测）。</p>
 */
@Data
public class LearningContentGenerateRequest {

    /** 知识单元 ID（kg_entity.id），必填 */
    private Long knowledgeId;

    /** 学习节点 ID（learning_chapter.id），可选：生成内容主题对齐 + 接受闪卡时挂章节 */
    private Long learningNodeId;

    /** 学习目标 ID（learning_goal.id，须属当前用户），可选：仅作内容主题上下文 */
    private Long goalId;

    /** 是否生成闪卡 Draft（默认 true） */
    private Boolean generateFlashcards = Boolean.TRUE;

    /** 闪卡数量：默认 5，范围 3~10（服务端强制收敛，禁止一次生成上百张） */
    private Integer flashcardCount;

    /** 是否生成费曼 Draft（默认 true） */
    private Boolean generateFeynman = Boolean.TRUE;

    /** 是否生成场景 Draft（默认 true） */
    private Boolean generateScenario = Boolean.TRUE;
}
