package com.knowflow.reward.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 钱包流水展示 VO：GET /api/wallet/ledger 分页返回的账单条目，由 RewardLedgerService#toVO 从 reward_ledger 投影。
 *
 * <p>只暴露用户可理解字段，刻意不复制 sourceId、sourceType、eventId、rewardRuleId 等内部字段；流水是 append-only 明细，本 VO
 * 不提供修改入口。
 *
 * <p>同一事件最多产生 XP 与 COIN 各一行，因此「奖励次数」不能按返回行数统计；查询始终带 userId 条件，越权用户读不到他人流水。
 */
@Data
public class LedgerVO {

  /** 流水主键 reward_ledger.id，自增；分页按 create_time、id 倒序时的次级排序键。 */
  private Long id;

  /** 币种：XP（成长值）/ COIN（消费积分），DDL VARCHAR(8) 大写存储；可选过滤参数 currency 大小写不敏感。 */
  private String currency;

  /**
   * 变动金额，直接取 reward_ledger.amount：奖励写正数，消费写负数（PurchaseService 传 -price）；符号与 direction
   * 一致，不能只凭数值判断业务类型。
   */
  private Integer amount;

  /** 方向：CREDIT 入账 / DEBIT 出账，DDL VARCHAR(8)；纠错不是第三种方向，而是 reason=COMPENSATION 的反向金额流水。 */
  private String direction;

  /** 内部原因码：学习事件类型名（如 CHAPTER_COMPLETE）、PURCHASE_APP 或 COMPENSATION；不保证可直接展示给用户。 */
  private String reason;

  /** 用户可读文案，由 RewardLedgerService 的 REASON_LABELS 映射；未命中时回退原始 reason，因此仍可能是英文码。 */
  private String reasonLabel;

  /** 该笔变动后该币种的余额快照：XP 行记录 wallet.xp，COIN 行记录 wallet.coin；写入时固定，不随后续余额变化刷新。 */
  private Integer balanceAfter;

  /** 流水写入时间，取 BaseEntity.createTime（DDL 默认 CURRENT_TIMESTAMP）；服务端本地时区，分页按该字段倒序。 */
  private LocalDateTime createTime;
}
