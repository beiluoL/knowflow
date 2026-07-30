package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 概念图解缓存实体。
 * 按 (user_id, concept) 维度缓存 AI 生成的概念图解，避免重复调用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_concept_diagram")
public class AiConceptDiagram extends BaseEntity {

    /** 所属用户ID。 */
    private Long userId;

    /** 概念名称。 */
    private String concept;

    /** 图解类型：FLOWCHART / SEQUENCE / CLASS / ER / PIE。 */
    private String diagramType;

    /** Mermaid 语法源码。 */
    private String mermaidCode;

    /** 概念简要说明。 */
    private String description;

    /** AI 详细解释。 */
    private String explanation;

    /** 难度：1 入门 / 2 中等 / 3 进阶。 */
    private Integer difficulty;

    /** 关键知识点列表（JSON 数组字符串）。 */
    private String keyPoints;

    /** 关联概念列表（JSON 数组字符串）。 */
    private String relatedConcepts;

    /** 代码示例。 */
    private String codeExample;
}
