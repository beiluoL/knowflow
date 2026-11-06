package com.knowflow.reward.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 每日打卡记录实体：user_check_in 表的一行代表「某用户某个自然日已打卡」及其连续天数与奖励快照。
 *
 * <p>幂等由唯一索引 uk_uci_user_date(user_id, check_date) 保证：CheckInServiceImpl#checkIn
 * 先查当日记录，已存在则直接返回既有状态，不重复计数、不重复发奖。唯一键不含 deleted，若某日记录被逻辑删除，再次打卡插入相同 (user_id, check_date)
 * 会撞唯一键——当前没有复活软删行的写入逻辑。
 *
 * <p>奖励口径分两条：本表 rewardExp 记录「基础 10 + 连续天数里程碑加成」，仅用于打卡展示；钱包侧的签到奖励由 LearningEvent →
 * RewardRule.CHECK_IN（10 XP / 2 Coin，每日 1 次上限）另行入账，两者互不抵扣。
 *
 * <p>继承自 BaseEntity 的 id/createTime/updateTime/deleted 中，deleted 由 {@code @TableLogic} 自动过滤。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_check_in")
public class UserCheckIn extends BaseEntity {

  /** 打卡用户 ID，逻辑外键 sys_user.id，DDL NOT NULL；与打卡日期组成唯一键，也是本表全部查询的过滤列。 */
  private Long userId;

  /**
   * 打卡日期（自然日），DDL NOT NULL，DATE 精度、不含时区。
   *
   * <p>服务端用 LocalDate.now() 取当天，因此「今天」以服务端本地时区为准；连续天数判定与本月日历统计都基于该列。
   */
  private LocalDate checkDate;

  /**
   * 当日打卡后的连续天数：昨日存在打卡记录时取昨日值 + 1，否则重置为 1。
   *
   * <p>该值同时写入 sys_user.streak_days 作为用户侧展示口径；里程碑加成按 7/14/30/100 精确匹配，超过 100 天后不再有额外加成。
   */
  private Integer continuousDays;

  /** 本次打卡记录的奖励经验值 = 基础 10 + 里程碑加成（7 天 +90、14 天 +190、30 天 +490、100 天 +990），仅作记录展示。 */
  private Integer rewardExp;

  /** 本次打卡记录的奖励精力值，固定为基础值 5，没有里程碑加成。 */
  private Integer rewardEnergy;
}
