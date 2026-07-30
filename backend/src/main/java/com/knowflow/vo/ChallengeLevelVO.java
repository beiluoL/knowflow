package com.knowflow.vo;

import lombok.Data;

/**
 * 编程挑战关卡视图：关卡题目信息 + 当前用户在该关卡的通关状态（锁定/星级/尝试次数）。
 */
@Data
public class ChallengeLevelVO {

    private Long id;

    /** 关卡序号，从 1 开始 */
    private Integer levelNo;

    private String title;

    private String description;

    /** 难度：0 简单 / 1 中等 / 2 困难 */
    private Integer difficulty;

    private String language;

    /** 关卡提示 */
    private String hint;

    private String exampleInput;

    private String exampleOutput;

    /** 代码模板（编辑器初始内容） */
    private String codeTemplate;

    /** 测试用例 JSON（[{input, expected}]），由前端执行判题 */
    private String testCases;

    /** 通关积分（满星可得） */
    private Integer points;

    // ------ 以下为当前用户状态 ------

    /** 是否锁定（上一关未通关） */
    private Boolean locked;

    /** 是否已通关 */
    private Boolean passed;

    /** 已获得星级 0-3 */
    private Integer stars;

    /** 提交次数 */
    private Integer attempts;

    /** 已获得积分 */
    private Integer pointsEarned;

    /** 最近一次提交的代码（用于恢复编辑器内容） */
    private String lastCode;
}
