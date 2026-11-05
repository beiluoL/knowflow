package com.knowflow.techenglish.entity;



import com.knowflow.common.persistence.BaseEntity;

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

    /** 关联类型：KNOWLEDGE（知识点）/ CHAPTER（章节）/ DOCUMENT（文档）/ NOTE（笔记）/ TERM（术语）。 */
    private String targetType;

    /** 目标实体稳定 ID */
    private Long targetId;

    /** 关系类型：EXPLAINS（解释）/ RELATED（相关）/ CONTRASTS（对比）/ CONFUSABLE（易混淆）。 */
    private String relationType;
}
