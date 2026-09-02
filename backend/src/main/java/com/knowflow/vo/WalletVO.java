package com.knowflow.vo;

import lombok.Data;

/** 钱包展示（当前余额状态；只读给前端，§42/§43）。 */
@Data
public class WalletVO {

    private Long userId;
    /** 经验值（成长，不可购买） */
    private Integer xp;
    /** 金币（平台内消费积分） */
    private Integer coin;
    /** 等级 */
    private Integer level;
    /** 下一级所需累计 exp 阈值 */
    private Integer nextLevelExp;
}
