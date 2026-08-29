package com.knowflow.vo;

import lombok.Data;

/**
 * 单维度信号对掌握度的贡献（Knowledge Mastery Engine，Phase 2-B）。
 * 用于「掌握度解释」结构化展示。
 */
@Data
public class SignalContributionVO {

    /** 信号维度。 */
    private String type;

    /** 维度标签（答题/编程/主动回忆/复习/错题/学习）。 */
    private String label;

    /** 信号强度 0~100（strength × 100）。 */
    private Integer strength;

    /** 基础权重（0~1，如 0.25）。 */
    private Double weight;

    /** 该维度对 mastery 的贡献 0~100。 */
    private Integer contribution;

    /** 有效样本数。 */
    private Integer sampleCount;
}
