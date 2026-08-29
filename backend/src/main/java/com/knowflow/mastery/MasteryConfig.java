package com.knowflow.mastery;

import java.math.BigDecimal;

/**
 * 掌握度引擎全局配置（Knowledge Mastery Engine，Phase 2-B）。
 * <p>
 * 集中管理所有权重、阈值与默认置信度，禁止在业务代码中散落 magic number。
 * 算法与阈值定义详见 {@code docs/开发计划/Phase2_知识掌握度引擎设计.md} §4 / §6。
 * </p>
 */
public final class MasteryConfig {

    private MasteryConfig() {
    }

    // ============================================================
    // 可学习知识点类型（kg_entity.type）
    // ============================================================
    /** 进入 Mastery 的可学习类型；TOOL 默认不进，留开关。 */
    public static final String[] LEARNABLE_TYPES = {"CONCEPT", "TECHNIQUE", "TERM", "PRINCIPLE"};

    /** TOOL 是否进入 Mastery（默认 false）。 */
    public static final boolean TOOL_ENABLED = false;

    // ============================================================
    // 信号基础权重（合成 mastery 时按「有效信号」归一化，缺失信号不计入分母）
    // ============================================================
    public static final double QUIZ_WEIGHT = 0.25;
    public static final double CODING_WEIGHT = 0.25;
    public static final double RECALL_WEIGHT = 0.20;
    public static final double REVIEW_WEIGHT = 0.10;
    public static final double MISTAKE_WEIGHT = 0.10;
    public static final double LEARNING_WEIGHT = 0.10;

    // ============================================================
    // 学习状态机阈值
    // ============================================================
    /** mastery < 该值 → WEAK。 */
    public static final int WEAK_THRESHOLD = 60;
    /** mastery ≥ 该值（且 conf/risk 达标）→ 候选 MASTERED。 */
    public static final int MASTERED_THRESHOLD = 80;
    /** MASTERED 要求的置信度下限。 */
    public static final int MASTERED_CONFIDENCE_THRESHOLD = 50;
    /** MASTERED 要求的遗忘风险上限（risk ≥ 该值则降级为 LEARNING）。 */
    public static final int MASTERED_RISK_THRESHOLD = 60;
    /** 遗忘风险 ≥ 该值（或 nextReviewAt 已过）→ REVIEW_REQUIRED。 */
    public static final int REVIEW_REQUIRED_RISK_THRESHOLD = 70;

    // ============================================================
    // 资源 → 知识点映射：置信度与状态阈值
    // ============================================================
    public static final BigDecimal CONF_MANUAL = BigDecimal.valueOf(1.00);
    public static final BigDecimal CONF_AI = BigDecimal.valueOf(0.92);
    public static final BigDecimal CONF_AUTO_HIGH = BigDecimal.valueOf(0.85);
    public static final BigDecimal CONF_AUTO_MID = BigDecimal.valueOf(0.60);
    /** 分类极低置信兜底。 */
    public static final BigDecimal CATEGORY_FALLBACK_CONFIDENCE = BigDecimal.valueOf(0.20);

    /** AUTO 匹配得分 ≥ 该值 → ACCEPTED。 */
    public static final double AUTO_THRESHOLD = 0.60;
    /** AUTO 匹配得分落在 [AUTO_PENDING_THRESHOLD, AUTO_THRESHOLD) → PENDING。 */
    public static final double AUTO_PENDING_THRESHOLD = 0.30;

    // ============================================================
    // 置信度饱和
    // ============================================================
    /** conf = N / (N + K) × 100，K 为样本饱和常数。 */
    public static final int CONFIDENCE_K = 10;

    // ============================================================
    // 时间衰减
    // ============================================================
    /** 衰减周期（天）：超过该天数未复习开始衰减。 */
    public static final int DECAY_PERIOD = 30;
    /** 衰减因子：DECAY_PERIOD 天未复习时 effective 衰减至该比例（0.7）。 */
    public static final double DECAY_FACTOR = 0.70;
    /** 遗忘风险中「未复习天数」的权重：daysSinceReview/DECAY_PERIOD × 该值。 */
    public static final double DECAY_RISK_WEIGHT = 60.0;

    // ============================================================
    // 拉普拉斯平滑（解决稀疏样本）
    // ============================================================
    public static final double LAPLACE_ALPHA = 1.0;

    // ============================================================
    // 复习（REVIEW）信号合成权重
    // ============================================================
    /** SM-2 稳定度（repetitions/lapses）占比。 */
    public static final double REVIEW_STABILITY_WEIGHT = 0.70;
    /** 复习间隔（intervalDay）占比。 */
    public static final double REVIEW_INTERVAL_WEIGHT = 0.30;
    /** 间隔天数上限（超过按上限计）。 */
    public static final int REVIEW_INTERVAL_CAP_DAYS = 30;
    /** 闪卡复习次数上限（用于无 SM-2 记录的代理稳定度）。 */
    public static final int FLASHCARD_REVIEW_CAP = 5;

    // ============================================================
    // 映射来源 / 状态 / 学习状态 常量
    // ============================================================
    public static final String MAP_SOURCE_MANUAL = "MANUAL";
    public static final String MAP_SOURCE_AI = "AI";
    public static final String MAP_SOURCE_IMPORT = "IMPORT";
    public static final String MAP_SOURCE_AUTO = "AUTO";
    public static final String MAP_SOURCE_CATEGORY = "CATEGORY_FALLBACK";

    public static final String MAP_STATUS_ACCEPTED = "ACCEPTED";
    public static final String MAP_STATUS_PENDING = "PENDING";
    public static final String MAP_STATUS_REJECTED = "REJECTED";

    public static final String STATUS_NOT_STARTED = "NOT_STARTED";
    public static final String STATUS_LEARNING = "LEARNING";
    public static final String STATUS_WEAK = "WEAK";
    public static final String STATUS_MASTERED = "MASTERED";
    public static final String STATUS_REVIEW_REQUIRED = "REVIEW_REQUIRED";

    // ============================================================
    // 参与 Mastery 的资源类型（与 learning_event.resource_type 对齐）
    // ============================================================
    public static final String RES_QUIZ = "QUIZ";
    public static final String RES_CODE_QUESTION = "CODE_QUESTION";
    public static final String RES_MISTAKE = "MISTAKE";
    public static final String RES_REVIEW_CARD = "REVIEW_CARD";
    public static final String RES_RECALL_SESSION = "RECALL_SESSION";
    public static final String RES_FLASHCARD = "FLASHCARD";
    public static final String RES_DOC = "DOC";

    /**
     * 判断 kg_entity.type 是否可学习。
     */
    public static boolean isLearnable(String type) {
        if (type == null) {
            return false;
        }
        if ("TOOL".equals(type)) {
            return TOOL_ENABLED;
        }
        for (String t : LEARNABLE_TYPES) {
            if (t.equals(type)) {
                return true;
            }
        }
        return false;
    }
}
