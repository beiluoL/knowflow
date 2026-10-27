package com.knowflow.coding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** CODE 题型扩展（与 quiz_question 1:1）。敏感字段（参考解答/隐藏用例在 code_test_case）禁止经普通详情接口外泄。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("code_problem")
public class CodeProblem extends BaseEntity {

    /** quiz_question.id */
    private Long questionId;

    /** 支持语言逗号分隔，当前仅 java */
    private String supportedLanguages;

    private String functionSignature;

    private String starterCode;

    /** 约束条件 Markdown */
    private String constraints;

    /** 展示示例 JSON：[{input,output,explanation}] */
    private String examples;

    private Integer timeLimitMs;

    private Integer memoryLimitMb;

    /** FUNCTION 固定函数 Harness / STDOUT 标准输入输出 */
    private String judgeMode;

    /** EXACT / NUMERIC / UNORDERED / BOOLEAN */
    private String checker;

    /** NUMERIC 允许误差 */
    private Double toleranceValue;

    private String testCaseVersion;

    /** 参考解答（保密） */
    private String referenceSolution;

    /** 题解（作答通过后才对本人开放） */
    private String solutionExplanation;

    private String complexityAnalysis;

    /** 面试追问 JSON */
    private String followUps;

    /** 常见错误 JSON */
    private String commonMistakes;

    private String harnessClass;

    public static final String MODE_FUNCTION = "FUNCTION";
    public static final String CHECKER_EXACT = "EXACT";
}
