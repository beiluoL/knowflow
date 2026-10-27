package com.knowflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 第一性原理推导提交结果（Architecture-4.1-§14~17）。
 * <p>
 * completeness = 填写合格的环节数 / 6 × 100（结构完整度）。
 * 注意：这是「你把推导过程写全了」的度量，不是「你推对了」的度量——
 * UI 与文档中必须如实表述，禁止包装成语义正确性评分。
 */
@Data
public class FirstPrinciplesSubmitResultVO {

    /** 推导主题（回显） */
    private String topic;

    /** 结构完整度 0-100 */
    private int completeness;

    /** 填写合格的环节数 */
    private int filled;

    /** 环节总数（固定 6） */
    private int total;

    /** 未填写 / 填写过短的环节 key */
    private List<String> missing;

    /** 逐环节是否填写合格（顺序：facts/assumptions/constraints/mechanism/causality/derivation） */
    private List<FirstPrinciplesStepVO> steps;

    /** 确定性分档点评（阈值驱动，非 AI 生成） */
    private String comment;

    /** 是否已落 FIRST_PRINCIPLES_DERIVED 事件 */
    private Boolean recorded;
}
