package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识实体：AI 从文档正文抽取的知识实体（概念/技术/术语/原理/工具）。
 * 按 name 全局去重合并，使同一概念在多篇文档中收敛为单一节点，形成真正知识图谱。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kg_entity")
public class KgEntity extends BaseEntity {

    /** 首次抽取来源文档ID（逻辑外键 doc_document.id）。 */
    private Long docId;

    /** 所属分类ID（逻辑外键 doc_category.id），取首次抽取文档的分类。 */
    private Long categoryId;

    /** 实体名称（全局唯一，合并同名实体）。 */
    private String name;

    /** 实体类型：CONCEPT/TECHNIQUE/TERM/PRINCIPLE/TOOL/OTHER。 */
    private String type;

    /** 实体说明。 */
    private String description;

    /** 重要度/被抽取次数，用于前端节点大小。 */
    private Integer weight;
}
