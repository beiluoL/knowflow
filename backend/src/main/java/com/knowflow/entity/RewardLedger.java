package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 奖励流水（append-only 历史事实）：每一笔 XP/Coin 变动都有一行，可审计。
 * 幂等键（user_id, source_type, source_id, currency）保证同一事件只入账一次。
 * 错误奖励不使用 UPDATE，而是追加 COMPENSATION 反向流水（§23）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reward_ledger")
public class RewardLedger extends BaseEntity {

    /** 用户ID */
    private Long userId;

    /** 币种：XP / COIN（统一大写） */
    private String currency;

    /** 变动金额：正=收入，负=支出（与 direction 一致） */
    private Integer amount;

    /** 方向：CREDIT / DEBIT */
    private String direction;

    /** 原因：CHAPTER_COMPLETE / PATH_COMPLETED / PURCHASE_APP / CHECK_IN / COMPENSATION 等 */
    private String reason;

    /** 来源类型：EVENT / PURCHASE / COMPENSATION */
    private String sourceType;

    /** 来源 ID：事件 ID（learning_event.id）或订单 ID（order_.id） */
    private Long sourceId;

    /** 变动后余额（审计快照，服务端计算） */
    private Integer balanceAfter;
}
