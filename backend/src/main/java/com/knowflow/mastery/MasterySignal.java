package com.knowflow.mastery;

import lombok.Data;

/**
 * 单维度掌握度信号（Knowledge Mastery Engine，Phase 2-B）。
 * <p>strength 为 0~1 的强度；sampleCount 为该维度的有效样本数（用于置信度）。</p>
 */
@Data
public class MasterySignal {

    /** 信号维度。 */
    private SignalType type;

    /** 信号强度 0~1（如正确率、通过率、平均分/100）。 */
    private double strength;

    /** 有效样本数（参与该维度计算的真实记录条数）。 */
    private int sampleCount;

    /** 人类可读标签。 */
    private String label;

    public MasterySignal() {
    }

    public MasterySignal(SignalType type, double strength, int sampleCount) {
        this.type = type;
        this.strength = strength;
        this.sampleCount = sampleCount;
        this.label = type == null ? null : type.getLabel();
    }
}
