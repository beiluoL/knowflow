package com.knowflow.reward.vo;

import lombok.Data;

/**
 * 成就列表项 VO：把 achievement 模板的展示字段与当前用户的解锁状态合并成一条成就卡片。
 *
 * <p>由 AchievementServiceImpl#buildItem 复制模板字段，再由 getMyAchievements 填入 unlocked、current、percent、exp
 * 与 unlockedTime；GET /api/achievements 和 POST /api/achievements/trigger 共用该结构。
 *
 * <p>口径提醒：已解锁时 current 直接置为 target、percent 固定 100，不表示实时统计值；exp 只在解锁后有值。
 */
@Data
public class AchievementItemVO {

  /** 成就模板主键，直接取 achievement.id，不是解锁记录 user_achievement.id。 */
  private Long id;

  /** 成就编码，取 achievement.code（如 READ_1ST_DOC）；唯一索引 idx_ach_code 保证模板内不重复。 */
  private String code;

  /** 成就名称，直接取 achievement.name，用于卡片标题。 */
  private String name;

  /** 成就描述，直接取 achievement.description；DDL 允许为空。 */
  private String description;

  /** 图标名，取 achievement.icon；DDL 默认 trophy，Service 在值为 null 时也回退 trophy。 */
  private String icon;

  /**
   * 分类，取 achievement.category。schema 列注释列 LEARNING/EXPLORATION/COMMUNITY/PERSISTENCE/SPECIAL，当前
   * data.sql 种子只用到 LEARNING/EXPLORATION/PERSISTENCE。
   */
  private String category;

  /** 是否已解锁：true 表示 user_achievement 已有该成就的行，包含本次请求刚自动解锁的情形。 */
  private Boolean unlocked;

  /** 本条成就计入概览的经验值：已解锁或本次刚解锁取模板 rewardExp（null 按 0），未解锁固定 0。 */
  private Integer exp;

  /** 进度当前值：未解锁时由 computeCurrent 实时 COUNT 得到，已解锁时直接置为 target，不是真实累计值。 */
  private Integer current;

  /** 达标阈值，取 achievement.condition_value；非正数时未解锁条目的百分比按 0 处理。 */
  private Integer target;

  /**
   * 进度百分比 0-100：未解锁按 {@code Math.min(100, current * 100 / target)} 整数除法，已解锁固定 100，target 非正数时为 0。
   */
  private Integer percent;

  /**
   * 解锁时间字符串：已有解锁行取 user_achievement.create_time#toString，本次刚解锁取 LocalDateTime.now()#toString；未解锁为
   * null，无时区后缀，按服务端本地时间。
   */
  private String unlockedTime;

  /** 模板定义的达成奖励经验值，取 achievement.reward_exp（null 按 0）；无论是否解锁都有值，与 exp 的区别是 exp 只在解锁后计入。 */
  private Integer rewardExp;
}
