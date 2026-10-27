package com.knowflow.dto;

import lombok.Data;

/**
 * Architecture-4.3：知识处理请求（只读编排，不写库）。
 *
 * <p>{@code mode} 取值：
 * <ul>
 *   <li>{@code KNOWLEDGE_EXTRACTION}（默认）：一次完成「摘要 + 概念抽取 + 关系/前置候选」；</li>
 *   <li>{@code SUMMARY}：只生成面向人的摘要（不产出知识候选）。</li>
 * </ul>
 * 其他模式第一版不支持，后端明确报错（400），不静默降级。</p>
 */
@Data
public class KnowledgeProcessRequestDTO {

    /** 资料（doc_document）ID，必填。 */
    private Long docId;

    /** 可选：目标学习节点（learning_chapter.id）。提供后结果会携带该章节上下文，接受时做节点关联。 */
    private Long learningNodeId;

    /** 处理模式，见类注释。 */
    private String mode;
}
