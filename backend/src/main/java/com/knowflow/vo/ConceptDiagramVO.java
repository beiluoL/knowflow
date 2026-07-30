package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 概念可视化图解视图对象。
 */
@Data
public class ConceptDiagramVO {

    /** 概念名称。 */
    private String concept;

    /** 图解类型：FLOWCHART / SEQUENCE / CLASS / ER / PIE。 */
    private String diagramType;

    /** Mermaid 语法源码。 */
    private String mermaidCode;

    /** 概念简要说明。 */
    private String description;

    /** AI 解释，帮助理解该概念。 */
    private String explanation;

    /** 概念难度：1 入门 / 2 中等 / 3 进阶。 */
    private Integer difficulty;

    /** 关键知识点列表。 */
    private List<String> keyPoints;

    /** 关联概念列表，用于拓展学习。 */
    private List<String> relatedConcepts;

    /** 代码示例（可为空）。 */
    private String codeExample;
}
