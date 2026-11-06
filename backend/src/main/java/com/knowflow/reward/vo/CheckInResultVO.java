package com.knowflow.reward.vo;

import lombok.Data;

/**
 * 每日打卡结果 VO：POST /api/checkin 的响应，包含本次打卡后的连续天数与展示奖励。
 *
 * <p>由 CheckInServiceImpl#checkIn 组装。成功打卡与「今日此前已打过卡」都会返回本 VO，且 checkedToday 都是 true，靠
 * alreadyChecked 区分；失败由异常体现，不会返回带 false 的结果。
 *
 * <p>奖励只写入 user_check_in 并用于展示，与 wallet.xp / wallet.coin 分开记账；钱包侧由 CHECK_IN 学习事件在独立事务里按 RewardRule
 * 发放。
 */
@Data
public class CheckInResultVO {

  /** 本次请求结束后当日是否已打卡：新打卡成功或今日此前已打卡均为 true。 */
  private Boolean checkedToday;

  /** true 表示今日此前已有打卡行，本次未插入记录、未触发事件、未发奖；false 表示本次新插入一行。 */
  private Boolean alreadyChecked;

  /** 打卡后的连续天数：新打卡按「昨日有记录则 +1，否则重置为 1」，重复打卡回读既有行的 continuous_days。 */
  private Integer continuousDays;

  /**
   * 本次展示奖励经验值：新打卡为基础 10 加里程碑加成（第 7/14/30/100 天分别为 100/200/500/1000），重复打卡为 0；写入
   * user_check_in.reward_exp，不进 wallet.xp。
   */
  private Integer rewardExp;

  /** 本次展示奖励精力值：新打卡固定 5，不随连续天数加成，重复打卡为 0；写入 user_check_in.reward_energy，当前没有对应的精力余额账户。 */
  private Integer rewardEnergy;
}
