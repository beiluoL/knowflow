package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 词条与知识实体的稳定关联。
 * target_type = TERM 时表示词条之间的关系；其余目标必须在查询时二次校验访问权限。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tech_vocab_concept_link")
public class TechVocabConceptLink extends BaseEntity {

    /** 来源词条 ID */
    private Long termId;

    /** KNOWLEDGE / CHAPTER / DOCUMENT / NOTE / TERM */
    private String targetType;

    /** 目标实体稳定 ID */
    private Long targetId;

    /** EXPLAINS / RELATED / CONTRASTS / CONFUSABLE */
    private String relationType;
}
