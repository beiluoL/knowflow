package com.knowflow.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 流水展示（§113-114：只暴露用户可理解的字段，不暴露 eventId/sourceType/rewardRuleId 等内部字段）。 */
@Data
public class LedgerVO {

    private Long id;
    /** 币种：XP / COIN */
    private String currency;
    /** 变动金额：正=收入，负=支出 */
    private Integer amount;
    /** 方向：CREDIT / DEBIT */
    private String direction;
    /** 原因码（内部），前端一般只用 reasonLabel */
    private String reason;
    /** 用户可读原因文案（如「完成章节」「兑换应用」） */
    private String reasonLabel;
    /** 变动后余额 */
    private Integer balanceAfter;
    private LocalDateTime createTime;
}
