package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户钱包：当前 XP/Coin/Level 状态（Wallet = 当前状态，RewardLedger = 历史事实）。
 * 一人一个（user_id 唯一）；xp 为成长值、coin 为平台内消费积分（不可提现/转账）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wallet")
public class Wallet extends BaseEntity {

    /** 用户ID（逻辑外键 sys_user.id） */
    private Long userId;

    /** 经验值（成长，不可购买） */
    private Integer xp;

    /** 金币（平台内消费积分，禁止为负） */
    private Integer coin;

    /** 等级（由 LevelService 按 xp 计算） */
    private Integer level;
}
