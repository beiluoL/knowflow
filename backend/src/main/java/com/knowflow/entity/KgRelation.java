package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 实体关系：知识实体之间的语义关系，记录抽取来源文档以支持溯源。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kg_relation")
public class KgRelation extends BaseEntity {

    /** 源实体ID（逻辑外键 kg_entity.id）。 */
    private Long sourceEntityId;

    /** 目标实体ID（逻辑外键 kg_entity.id）。 */
    private Long targetEntityId;

    /** 关系类型：RELATED_TO/PREREQUISITE/IS_A/PART_OF/USES/CONTRASTS。 */
    private String relation;

    /** 关系说明。 */
    private String description;

    /** 抽取来源文档ID（逻辑外键 doc_document.id）。 */
    private Long docId;
}
