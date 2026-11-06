package com.knowflow.reward.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户钱包：当前 XP / Coin / Level 状态（Wallet 存当前值，RewardLedger 存历史流水）。
 *
 * <p>映射 wallet 表，一人一个，唯一索引 uk_wallet_user(user_id) 保证；该索引不含 deleted，若钱包行被逻辑删除，同一 user_id
 * 再次插入会撞唯一键，而 WalletService#getOrCreate 会捕获 DuplicateKeyException 回读既有行。
 *
 * <p>写入只允许 RewardEngine / PurchaseService 经 WalletService 完成（行锁 + 条件更新），读取走 getOrCreate；
 * 实体本身不承载并发控制，也没有乐观锁版本字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wallet")
public class Wallet extends BaseEntity {

  /** 钱包所属用户 ID，逻辑外键 sys_user.id，DDL NOT NULL；唯一索引列，也是所有余额查询的过滤条件。 */
  private Long userId;

  /**
   * 成长值（XP），只增不减：首次创建时以 sys_user.exp 的历史值作为种子，之后按奖励规则累加。
   *
   * <p>累加使用 Math.addExact，接近 int 上限时抛 ArithmeticException 而不是静默回绕；扣款只作用于 coin。
   */
  private Integer xp;

  /**
   * 金币（平台内消费积分，不可提现/转账），默认 0，只由奖励入账与消费扣减改变。
   *
   * <p>「不允许为负」由 WalletMapper#debitCoin 的条件更新 coin >= #{amount} 保证，不是数据库约束；影响行数为 0
   * 即视为余额不足，由调用方转成业务异常。
   */
  private Integer coin;

  /** 等级，由 LevelService#levelOf(xp) 推导，不是独立累加值；每次加币都会重算并覆盖写回。 */
  private Integer level;
}
