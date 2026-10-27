package com.knowflow.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 试卷模板：稳定 template_key 描述默认组卷规格。
 * 模板不是一次作答记录；按模板生成的试卷会在 exam_paper / exam_paper_question 保存快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exam_template")
public class ExamTemplate extends BaseEntity {

    /** 稳定模板标识，如 java-collections */
    private String templateKey;

    /** 模板名称 */
    private String name;

    /** 模板说明 */
    private String description;

    /** 领域：JAVA / AI / JAVA_AI */
    private String domain;

    /** 知识点（逗号分隔） */
    private String topics;

    /** 组卷规格 JSON：题型分布/难度/策略/多选评分 */
    private String spec;

    /** 考试时长（分钟） */
    private Integer durationMinutes;

    /** 总分 */
    private Integer totalScore;

    /** 及格分 */
    private Integer passScore;

    /** PRACTICE / EXAM */
    private String mode;

    /** 1 启用 / 0 停用 */
    private Integer status;

    /** 模板版本 */
    private String version;
}
