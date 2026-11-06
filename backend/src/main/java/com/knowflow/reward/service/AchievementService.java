package com.knowflow.reward.service;

import com.knowflow.reward.vo.AchievementPageVO;

/**
 * 成就业务契约：对外只暴露「取我的成就页」与「轻量触发检查」两个入口，由 AchievementServiceImpl 实现。
 *
 * <p>两个入口都不是纯查询：底层 getMyAchievements 会实时统计进度、插入解锁行并把奖励经验写入 sys_user.exp；Controller 的 userId 一律来自
 * SecurityUtils。
 *
 * <p>成就模板由 data.sql 种子写入 achievement 表，当前没有管理端 CRUD；解锁状态存在 user_achievement，一用户一成就一行。
 */
public interface AchievementService {

  /**
   * 取当前用户的成就页数据：列表（含进度 / 解锁状态）、概览统计、最近解锁时间线。
   *
   * <p>副作用：未解锁成就达标时自动解锁并发放经验，全部写操作与查询在同一事务内；实现层抛出的异常语义由实现类承担。
   *
   * @param userId 当前登录用户 ID，由 Controller 从 SecurityUtils 取得
   * @return 成就页 VO；模板为空时列表为空、总进度为 0
   */
  AchievementPageVO getMyAchievements(Long userId);

  /**
   * 轻量触发成就检查，丢弃页面数据，用于前端刷新进度。
   *
   * <p>尽力而为：实现类会捕获所有异常仅记告警，因此本方法正常返回不代表解锁一定成功；userId 为 null 时不执行任何查询。
   *
   * @param userId 当前登录用户 ID，由 Controller 从 SecurityUtils 取得
   */
  void triggerAchievementCheck(Long userId);
}
