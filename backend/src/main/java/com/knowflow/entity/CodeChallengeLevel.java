package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编程挑战关卡实体：内嵌题目信息，使赛道自包含（不依赖 code_question）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("code_challenge_level")
public class CodeChallengeLevel extends BaseEntity {

    /** 所属挑战 ID（逻辑外键 code_challenge.id） */
    private Long challengeId;

    /** 关卡序号，从 1 开始递增 */
    private Integer levelNo;

    /** 关卡标题 */
    private String title;

    /** 题目描述（支持多行文本） */
    private String description;

    /** 难度：0 简单 / 1 中等 / 2 困难 */
    private Integer difficulty;

    /** 语言标识 */
    private String language;

    /** 关卡提示 */
    private String hint;

    /** 输入示例 */
    private String exampleInput;

    /** 输出示例 */
    private String exampleOutput;

    /** 代码模板（编辑器初始内容） */
    private String codeTemplate;

    /** 测试用例（JSON 数组：[{input, expected}]） */
    private String testCases;

    /** 通关积分 */
    private Integer points;

    /** 状态：0 草稿 / 1 已发布 */
    private Integer status;
}
