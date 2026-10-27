package com.knowflow.reward;

import com.knowflow.common.LearningEventType;
import lombok.Getter;

/**
 * 奖励规则（MVP 用枚举 + 静态配置，不建 reward_rule 表，§15/§20）。
 * 数值依据《Architecture-2-Reward-Matrix.md》（已审核）：XP / Coin / 每日上限。
 */
@Getter
public enum RewardRule {

    /** 章节完成 */
    CHAPTER_COMPLETE(LearningEventType.CHAPTER_COMPLETE, 30, 5, 0),
    /** 学习路径全部完成 */
    PATH_COMPLETED(LearningEventType.PATH_COMPLETED, 120, 20, 0),
    /** 答题（含正确/错误；高频，每日上限 50，防刷） */
    QUESTION_ANSWERED(LearningEventType.QUESTION_ANSWERED, 5, 1, 50),
    /** 错题复习完成 */
    MISTAKE_REVIEW(LearningEventType.MISTAKE_REVIEW, 8, 2, 30),
    /** 闪卡/复习卡复习 */
    FLASHCARD_REVIEWED(LearningEventType.FLASHCARD_REVIEWED, 4, 1, 60),
    /** 主动回忆会话完成 */
    RECALL_COMPLETED(LearningEventType.RECALL_COMPLETED, 15, 3, 10),
    /** 代码提交（仅 metadata.passed=true 奖励，引擎内校验） */
    CODE_SUBMITTED(LearningEventType.CODE_SUBMITTED, 10, 2, 20),
    /** 学习任务完成 */
    LEARNING_TASK_COMPLETE(LearningEventType.LEARNING_TASK_COMPLETE, 10, 2, 0),
    /** 每日签到（自然日 1 次） */
    CHECK_IN(LearningEventType.CHECK_IN, 10, 2, 1),
    ;

    private final LearningEventType eventType;
    private final int xp;
    private final int coin;
    private final int dailyLimit;

    RewardRule(LearningEventType eventType, int xp, int coin, int dailyLimit) {
        this.eventType = eventType;
        this.xp = xp;
        this.coin = coin;
        this.dailyLimit = dailyLimit;
    }

    /** 按事件类型取规则；无对应规则（不奖励事件）返回 null。 */
    public static RewardRule byEventType(LearningEventType type) {
        if (type == null) {
            return null;
        }
        for (RewardRule r : values()) {
            if (r.eventType == type) {
                return r;
            }
        }
        return null;
    }
}
