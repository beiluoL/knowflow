package com.knowflow.coding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 题目知识点分类（两级树）。题目归属以 topicCode 显式关联，tags 仅作检索标签。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_topic")
public class QuestionTopic extends BaseEntity {

    /** 稳定编码，如 ALGO_ARRAY / ALGO_DP */
    private String code;

    private String name;

    /** 父级编码；null 为一级分类 */
    private String parentCode;

    /** 1 一级 / 2 二级 */
    private Integer level;

    private Integer sortOrder;

    /** 1 启用 / 0 停用 */
    private Integer status;
}
