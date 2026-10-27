package com.knowflow.coding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 正式代码提交记录。score 为 null 表示判题未完成（绝不默认 0）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("code_submission")
public class CodeSubmission extends BaseEntity {

    private Long userId;

    /** quiz_question.id（CODE 题） */
    private Long questionId;

    private String problemVersion;

    private String language;

    private String sourceCode;

    /** RUN 仅公开用例 / SUBMIT 全部用例 */
    private String submissionType;

    /** JudgeStatus 常量 */
    private String judgeStatus;

    /** 百分制；未完成判题为 null */
    private Integer score;

    private Integer passedCount;

    private Integer totalCount;

    private Integer executionTimeMs;

    private Integer memoryUsedKb;

    private String compileOutput;

    /** 逐用例结果 JSON；隐藏用例仅汇总，不含 input/expected */
    private String caseResults;

    /** 判题审计 JSON */
    private String judgeDetail;

    /** PRACTICE / EXAM */
    private String contextType;

    /** EXAM 时为 exam_answer.id */
    private Long contextRefId;

    public static final String TYPE_RUN = "RUN";
    public static final String TYPE_SUBMIT = "SUBMIT";
    public static final String CONTEXT_PRACTICE = "PRACTICE";
    public static final String CONTEXT_EXAM = "EXAM";
}
