package com.knowflow.mastery;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 单知识点（user, knowledge）的掌握度聚合计算结果（Knowledge Mastery Engine，Phase 2-B）。
 * <p>由 {@link com.knowflow.service.impl.MasteryEngine} 基于业务表聚合生成，
 * 经「有效权重归一化」得到 mastery/confidence/risk/status 后由 KnowledgeMasteryService 落盘。</p>
 */
@Data
public class MasteryComputation {

    /** 各维度信号（仅包含有真实样本的有效维度）。 */
    private List<MasterySignal> signals = new ArrayList<>();

    // ===== 计数器（整行重算，天然幂等）=====
    private int correctCount = 0;
    private int wrongCount = 0;
    private int attemptCount = 0;
    private int reviewCount = 0;
    private int recallCount = 0;
    private int recallAvgScore = 0;
    private int codingAttemptCount = 0;
    private int codingPassCount = 0;
    private int mistakeCount = 0;
    private int mistakeMastered = 0;
    private int consecutiveCorrect = 0;
    private int consecutiveWrong = 0;

    /** SM-2 累计遗忘次数（风险计算用）。 */
    private int lapseWeight = 0;

    // ===== 时间维度 =====
    private LocalDateTime lastLearnedAt;
    private LocalDateTime lastReviewedAt;
    private LocalDateTime lastAssessedAt;
    private LocalDateTime nextReviewAt;

    /** 是否存在任意有效信号（决定是否落盘为 NOT_STARTED 或跳过）。 */
    public boolean hasSignals() {
        return signals != null && !signals.isEmpty();
    }
}
