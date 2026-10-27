package com.knowflow.coding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 代码题测试用例。HIDDEN 用例为判题机密，普通题目详情接口不得返回 input/expectedOutput。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("code_test_case")
public class CodeTestCase extends BaseEntity {

    private Long problemId;

    /** 入参 JSON：单值或参数数组 */
    private String input;

    /** 期望输出 JSON */
    private String expectedOutput;

    /** PUBLIC / HIDDEN */
    private String visibility;

    private Integer weight;

    private Integer orderNo;

    private String version;

    public static final String VIS_PUBLIC = "PUBLIC";
    public static final String VIS_HIDDEN = "HIDDEN";
}
