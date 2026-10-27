package com.knowflow.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 逐题作答与评分证据。
 *
 * <p>score 为 NULL 表示尚未评分（绝不默认 0 分计入成绩）；
 * grade_detail 保存规则判分依据或 AI 五维评分结构，支持追溯。
 * 唯一约束 (attempt_id, paper_question_id) 保证交卷幂等。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exam_answer")
public class ExamAnswer extends BaseEntity {

    public static final String GRADE_RULE = "RULE";
    public static final String GRADE_AI = "AI";
    public static final String GRADE_PENDING = "PENDING";
    /** Phase 3G：CODE 题由代码判题网关评分（graded=0 + JUDGE 表示等待判题，不会流向 AI 评卷）。 */
    public static final String GRADE_JUDGE = "JUDGE";

    private Long attemptId;
    private Long paperQuestionId;
    private Long questionId;
    private Integer ordinal;
    private String topic;

    private String userAnswer;
    private Integer isCorrect;
    /** 未评分为 null */
    private Integer score;
    private Integer maxScore;

    /** RULE / AI / PENDING */
    private String gradeType;
    /** 评分证据 JSON */
    private String gradeDetail;

    private Integer timeCost;
    /** 0 未评 / 1 已评 */
    private Integer graded;
}
