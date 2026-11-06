package com.knowflow.reward.vo;

import lombok.Data;

/**
 * 钱包展示 VO：GET /api/wallet 返回的当前余额快照，由 WalletService#getWalletVO 组装，只读且不含流水明细。
 *
 * <p>虽是读接口，首次访问会因 getOrCreate 惰性创建 wallet 行（xp 以 sys_user.exp 为种子）；userId 一律来自
 * SecurityUtils，不接受请求参数。
 *
 * <p>xp / coin 是 wallet 表余额，level 与 nextLevelExp 是 LevelService 按 xp 派生的展示值；成就奖励写
 * sys_user.exp，不体现在这里。
 */
@Data
public class WalletVO {

  /** 钱包归属用户 ID，取 wallet.user_id，与当前登录用户一致。 */
  private Long userId;

  /** 累计成长值余额，取 wallet.xp；由 RewardEngine 按学习事件累加，不可购买、不可直接兑换。 */
  private Integer xp;

  /** 金币余额，取 wallet.coin；购买应用时由 WalletMapper#debitCoin 条件扣减，SQL 保证不会出现负余额。 */
  private Integer coin;

  /** 钱包等级，取 wallet.level 派生字段；LevelService#levelOf(xp) 为 xp / 100 + 1，每次加币后重算覆盖。 */
  private Integer level;

  /** 下一等级的累计 XP 阈值，LevelService#nextLevelExp(xp) = level * 100；不是「还差多少」，xp=0 时返回 100。 */
  private Integer nextLevelExp;

  /** 是否拥有不扣 Coin 的系统级无限消费能力；由 RewardAccountQuery 读取 sys_user.role，仅 ADMIN 为 true，用户不存在时为 false。 */
  private Boolean unlimited;
}
