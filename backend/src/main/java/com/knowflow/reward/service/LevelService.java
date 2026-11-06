package com.knowflow.reward.service;

import org.springframework.stereotype.Service;

/**
 * 等级换算工具：把累计成长值 XP 换算成等级、下一级阈值与当前等级内进度，是钱包等级的唯一公式来源。
 *
 * <p>公式 level = xp / 100 + 1（整数除法），沿用历史 UserServiceImpl.stats 的口径迁移而来，避免「stats 一套、wallet
 * 一套」。WalletService 在钱包初始化与每次加币后都用本类重算 wallet.level，因此 level 是派生值而非独立累加字段。
 *
 * <p>无状态、无事务、不访问数据库；入参按非负 XP 设计，wallet.xp 由非负数累加并经 Math.addExact 防溢出，本类不再重复校验负数。
 */
@Service
public class LevelService {

  /**
   * 由累计 XP 计算等级：每满 100 XP 升一级，1 级起点为 0 XP。
   *
   * @param xp 累计成长值，非负
   * @return 等级；xp=0..99 返回 1，xp=100..199 返回 2，xp=500 返回 6
   */
  public int levelOf(int xp) {
    return xp / 100 + 1;
  }

  /**
   * 返回「下一等级所需的累计 XP 阈值」，不是距离下一级还差多少。
   *
   * @param xp 当前累计成长值，非负
   * @return 下一级的起始累计 XP；xp=3200 时当前 33 级，返回 3300；xp=0 返回 100
   */
  public int nextLevelExp(int xp) {
    return levelOf(xp) * 100;
  }

  /**
   * 返回当前等级内已获得的 XP 进度，供前端进度条使用。
   *
   * <p>等价于 xp % 100：跨过整百时等级提升、进度归零，因此取值是 0–99，代码里 Math.min(100, ...) 的上限 100 实际不可达；Math.max(0, ...)
   * 用于兜底负 XP。当前生产代码没有调用点，仅 WalletVO 暴露 nextLevelExp。
   *
   * @param xp 累计成长值
   * @return 当前等级内进度，0（含）到 99（含）
   */
  public int progressInLevel(int xp) {
    int level = levelOf(xp);
    int floor = (level - 1) * 100;
    return Math.min(100, Math.max(0, xp - floor));
  }
}
