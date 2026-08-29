package com.knowflow.mastery;

/**
 * 掌握度信号维度（Knowledge Mastery Engine，Phase 2-B）。
 * 每个维度对应一种可解释的学习证据来源。
 */
public enum SignalType {

    /** 答题正确率（Quiz）。 */
    QUIZ("答题"),
    /** 编程通过率（Coding）。 */
    CODING("编程"),
    /** 主动回忆平均分（Recall）。 */
    RECALL("主动回忆"),
    /** 间隔复习稳定度（SM-2 Review）。 */
    REVIEW("复习"),
    /** 错题掌握比（Mistake）。 */
    MISTAKE("错题"),
    /** 阅读/学习曝光（Learning）。 */
    LEARNING("学习");

    private final String label;

    SignalType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 该维度的基础权重（来自 MasteryConfig）。 */
    public double getWeight() {
        switch (this) {
            case QUIZ:
                return MasteryConfig.QUIZ_WEIGHT;
            case CODING:
                return MasteryConfig.CODING_WEIGHT;
            case RECALL:
                return MasteryConfig.RECALL_WEIGHT;
            case REVIEW:
                return MasteryConfig.REVIEW_WEIGHT;
            case MISTAKE:
                return MasteryConfig.MISTAKE_WEIGHT;
            case LEARNING:
                return MasteryConfig.LEARNING_WEIGHT;
            default:
                return 0.0;
        }
    }
}
