package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("code_question")
/** 代码练习题目实体，承载题目描述、示例、测试用例与运行约束等信息。 */
public class CodeQuestion extends BaseEntity {

    /** 题目标题 */
    private String title;

    /** 题目描述（支持多行文本） */
    private String description;

    /** 难度：0 简单 / 1 中等 / 2 困难 */
    private Integer difficulty;

    /** 主语言标识：javascript / typescript / python / java / sql */
    private String language;

    /** 题目标签，逗号分隔（如：算法,数组,Promise） */
    private String tags;

    /** 题目提示 */
    private String hint;

    /** 输入示例 */
    private String exampleInput;

    /** 输出示例 */
    private String exampleOutput;

    /** 函数签名模板（代码编辑器初始内容） */
    private String codeTemplate;

    /** 测试用例（JSON 数组：[{input, expected}]） */
    private String testCases;

    /** 预期解法关键词，用于 AI 回答提示 */
    private String solutionHint;

    /** 做题时长（分钟） */
    private Integer duration;

    /** 排序值，越小越靠前 */
    private Integer sortOrder;

    /** 状态：0 草稿 / 1 已发布 */
    private Integer status;

    /** 通过次数（用于统计通过率） */
    private Integer passCount;

    /** 提交次数（用于统计通过率） */
    private Integer submitCount;
}
