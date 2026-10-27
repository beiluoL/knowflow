package com.knowflow.vo;

import lombok.Data;

/**
 * Architecture-4.2：知识点简引用（章节 → 知识点的解析结果项）。
 *
 * <p>只承载「真实存在」的知识实体引用：id 指向 {@code kg_entity.id}，
 * name 为知识实体真实名称（禁止前端用「知识点 123」这类占位文案兜底，§19）。</p>
 */
@Data
public class KnowledgeRefVO {

    /** 知识点 ID（逻辑外键 kg_entity.id） */
    private Long id;

    /** 知识点真实名称（kg_entity.name；实体已删时后端不返回该条） */
    private String name;

    /** 实体类型：CONCEPT/TECHNIQUE/TERM/PRINCIPLE/TOOL/OTHER */
    private String type;

    /**
     * 来源说明：DOC=由章节关联文档的资源→知识点映射（ACCEPTED）推导；
     * EXPLICIT=调用方显式指定。用于前端排障，不参与任何评分。
     */
    private String source;
}
