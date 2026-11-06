package com.knowflow.reward.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.knowflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 奖励流水（append-only 历史事实）：每一笔 XP / Coin 变动都有一行，可审计。
 *
 * <p>幂等由唯一索引 uk_ledger_idempotent(user_id, source_type, source_id, currency)
 * 保证，同一来源同一币种只会入账一次；错误奖励不 UPDATE 原行，而是追加 COMPENSATION 反向流水。流水只允许追加，普通更新语义不成立，因此 update_time
 * 实际不会随业务变化刷新。
 *
 * <p>本表与 Wallet 分工：Wallet 是当前余额，本表是变动明细；余额与流水的对账口径为 balance_after + 后续变动 = 当前余额。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reward_ledger")
public class RewardLedger extends BaseEntity {

  /** 流水归属用户 ID，逻辑外键 sys_user.id，DDL NOT NULL；与 source_type/source_id/currency 组成幂等唯一键。 */
  private Long userId;

  /** 币种：XP（成长值）/ COIN（消费积分），DDL VARCHAR(8) NOT NULL，统一大写；一笔变动只涉及一种币。 */
  private String currency;

  /**
   * 变动金额，DDL INT NOT NULL：正数表示收入、负数表示支出，符号与 direction 保持一致。
   *
   * <p>奖励按规则写正数（RewardEngine），消费写负数（PurchaseService 以 -price 记账），因此读取方不能假设 amount 恒为正，也不能只凭 amount
   * 判断业务类型。
   */
  private Integer amount;

  /** 方向：CREDIT 入账 / DEBIT 出账，DDL VARCHAR(8) NOT NULL；与 reason 一起决定流水在账单里的展示分组。 */
  private String direction;

  /**
   * 变动原因，DDL VARCHAR(50) NOT NULL。
   *
   * <p>奖励类写入事件类型名（CHAPTER_COMPLETE、CHECK_IN 等），消费写 PURCHASE_APP，纠错写 COMPENSATION；对用户的文案由
   * RewardLedgerService 的原因映射表翻译，未命中时前端不应直接展示本字段。
   */
  private String reason;

  /** 来源类型：EVENT（学习事件）/ PURCHASE（消费）/ COMPENSATION（补偿），DDL VARCHAR(20) NOT NULL。 */
  private String sourceType;

  /**
   * 来源 ID，DDL 允许为空：EVENT 存 learning_event 主键，PURCHASE 存订单 ID，COMPENSATION 可能为空。
   *
   * <p>它与 sourceType、currency 一起构成幂等键，因此不能为了「补全」而随意回填。
   */
  private Long sourceId;

  /**
   * 变动后的余额快照，DDL INT NOT NULL，由发奖方在行锁内读取钱包后写入。
   *
   * <p>XP 流水记录 wallet.xp，COIN 流水记录 wallet.coin；该值是审计依据，不随后续余额变化而更新。
   */
  private Integer balanceAfter;
}
