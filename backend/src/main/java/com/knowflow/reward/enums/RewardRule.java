package com.knowflow.reward.enums;

import com.knowflow.learning.contract.LearningEventType;
import lombok.Getter;

/**
 * 奖励规则（MVP 用枚举 + 静态配置，不建 reward_rule 表）：学习事件类型 → 单次 XP / Coin 与每日上限。
 *
 * <p>数值依据《Architecture-2-Reward-Matrix.md》（已审核）。本枚举是唯一发奖口径，RewardEngine 只按 {@link #byEventType}
 * 查到的规则入账；没有对应规则的普通学习事件自动跳过，不产生流水。
 */
@Getter
public enum RewardRule {

  /** 章节完成：30 XP / 5 Coin，不限次数（同一章节的重复完成由事件侧控制）。 */
  CHAPTER_COMPLETE(LearningEventType.CHAPTER_COMPLETE, 30, 5, 0),
  /** 学习路径全部完成：120 XP / 20 Coin，不限次数；属于低频里程碑奖励。 */
  PATH_COMPLETED(LearningEventType.PATH_COMPLETED, 120, 20, 0),
  /** 答题（含正确/错误）：5 XP / 1 Coin，每日上限 50 次，用上限抑制高频刷分。 */
  QUESTION_ANSWERED(LearningEventType.QUESTION_ANSWERED, 5, 1, 50),
  /** 错题复习完成：8 XP / 2 Coin，每日上限 30 次。 */
  MISTAKE_REVIEW(LearningEventType.MISTAKE_REVIEW, 8, 2, 30),
  /** 闪卡/复习卡复习：4 XP / 1 Coin，每日上限 60 次，是单次收益最低、上限最高的高频行为。 */
  FLASHCARD_REVIEWED(LearningEventType.FLASHCARD_REVIEWED, 4, 1, 60),
  /** 主动回忆会话完成：15 XP / 3 Coin，每日上限 10 次。 */
  RECALL_COMPLETED(LearningEventType.RECALL_COMPLETED, 15, 3, 10),
  /** 代码提交：10 XP / 2 Coin，每日上限 20 次；仅 metadata.passed=true 才发奖，由 RewardEngine 校验。 */
  CODE_SUBMITTED(LearningEventType.CODE_SUBMITTED, 10, 2, 20),
  /** 学习任务完成：10 XP / 2 Coin，不限次数；撤销后重做的事件带 rewardEligible=false，不再重复奖励。 */
  LEARNING_TASK_COMPLETE(LearningEventType.LEARNING_TASK_COMPLETE, 10, 2, 0),
  /** 每日签到：10 XP / 2 Coin，每日上限 1 次；与 user_check_in 记录的展示奖励是两条独立口径。 */
  CHECK_IN(LearningEventType.CHECK_IN, 10, 2, 1),
  ;

  /** 规则对应的学习事件类型，RewardEngine 用它把落库事件反查为规则；无匹配即为「不奖励事件」。 */
  private final LearningEventType eventType;

  /** 单次事件发放的成长值（XP），写入 wallet.xp 并追加一条 XP 流水；<= 0 时该币种不发。 */
  private final int xp;

  /** 单次事件发放的金币（Coin），写入 wallet.coin 并追加一条 COIN 流水；<= 0 时该币种不发。 */
  private final int coin;

  /**
   * 每自然日最多发放次数，按流水 reason + create_time 聚合计数。
   *
   * <p>0 表示不限次数；大于 0 时一旦当日已达上限，事件直接跳过，不做延迟补发，也不回补历史。
   */
  private final int dailyLimit;

  /**
   * 声明一条奖励规则。
   *
   * @param eventType 对应的学习事件类型，不能为空
   * @param xp 单次发放的成长值，0 表示不发 XP
   * @param coin 单次发放的金币，0 表示不发 Coin
   * @param dailyLimit 每自然日发放上限，0 表示不限
   */
  RewardRule(LearningEventType eventType, int xp, int coin, int dailyLimit) {
    this.eventType = eventType;
    this.xp = xp;
    this.coin = coin;
    this.dailyLimit = dailyLimit;
  }

  /** 按事件类型取规则；null 或无对应规则（不奖励事件）返回 null，调用方据此跳过入账。 */
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
