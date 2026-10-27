package com.knowflow.exam.engine;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 确定性智能练习规则配置（Phase 3H，{@code knowflow.smart-practice.*}）。
 *
 * <p>规则引擎版本固定为 {@link PaperComposer#RULE_ENGINE_VERSION}；
 * 这里只放可调权重与到期窗口，调整参数不需要改代码。权重仅决定各优先级桶的目标配额，
 * 实际选取仍严格遵守“可解释优先级 + 真实证据”，不会把“没做过”当成“不会”。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "knowflow.smart-practice")
public class SmartPracticeProperties {

    /** 错题复习到期窗口（天）：未掌握且（从未复习或上次复习早于该窗口）视为到期。默认 1 与错题 dueToday 口径一致。 */
    private int reviewDueDays = 1;

    /** REVIEW 策略各优先级桶的相对权重（会归一化），顺序即优先级。 */
    private Weights weights = new Weights();

    @Data
    public static class Weights {
        /** 1. 到期复习题 */
        private int dueReview = 30;
        /** 2. 高频错题（错题出现/复习次数多） */
        private int highFreqMistake = 25;
        /** 3. 未掌握知识点（需 ≥3 真实作答样本） */
        private int weakTopic = 20;
        /** 4. 当前目标知识点 */
        private int targetTopic = 15;
        /** 5. 未做新题 */
        private int newUnanswered = 10;
    }
}
