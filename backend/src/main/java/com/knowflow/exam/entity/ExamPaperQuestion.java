package com.knowflow.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 试卷题目快照：题号、分值、评分策略与题干内容。
 * answer/explanation 只存在服务端快照，答题接口不下发，交卷后解析接口才返回。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exam_paper_question")
public class ExamPaperQuestion extends BaseEntity {

    private Long paperId;
    /** quiz_question.id；AI 个人快照题为 NULL */
    private Long questionId;
    /** 题号，从 1 开始 */
    private Integer ordinal;
    private Integer score;
    private String questionType;
    /** 知识点（tags 首标签） */
    private String topic;

    /** 多选评分：ALL_OR_NOTHING / PARTIAL_CREDIT */
    private String scoringRule;

    /** 题目快照 JSON：title/content/options/answer/explanation/difficulty */
    private String snapshot;
}
