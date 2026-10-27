package com.knowflow.exam.engine;

/**
 * 规则组卷“选题原因”（Phase 3H）。每道被选中的题都带可解释原因，前端/审计可直接展示。
 *
 * <p>顺序即 REVIEW 策略的优先级；新增原因只能追加，不能改变既有语义。
 */
public final class SelectionReason {

    private SelectionReason() {
    }

    /** 到期复习：未掌握错题且到达复习窗口。 */
    public static final String DUE_REVIEW = "DUE_REVIEW";
    /** 高频错题：未掌握但尚未到期，按错题复习次数优先。 */
    public static final String HIGH_FREQ_MISTAKE = "HIGH_FREQ_MISTAKE";
    /** 薄弱知识点：该知识点真实作答样本 ≥3 且错误率高。 */
    public static final String WEAK_TOPIC = "WEAK_TOPIC";
    /** 目标知识点：用户当前指定的知识点题目。 */
    public static final String TARGET_TOPIC = "TARGET_TOPIC";
    /** 未做新题：用户从未作答过（注意：不代表不会，仅保证新鲜题量）。 */
    public static final String NEW_UNANSWERED = "NEW_UNANSWERED";
    /** 常规题池：已作答、非错题、非薄弱点的兜底题。 */
    public static final String GENERAL_POOL = "GENERAL_POOL";
    /** 高频考点策略（FREQUENCY）标记。 */
    public static final String FREQUENCY = "FREQUENCY";
}
