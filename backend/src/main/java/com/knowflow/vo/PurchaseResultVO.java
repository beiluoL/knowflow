package com.knowflow.vo;

import lombok.Data;

/** 购买结果（§174）。 */
@Data
public class PurchaseResultVO {

    private String orderNo;
    private String productName;
    private Integer priceCoin;
    /** 购买后 Coin 余额 */
    private Integer balanceAfter;
}
