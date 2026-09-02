package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单明细：购买时的商品价格快照（product 改价不影响历史订单，§64/§80）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_item")
public class OrderItem extends BaseEntity {

    /** 订单ID（逻辑外键 order_.id） */
    private Long orderId;

    /** 商品ID（逻辑外键 product.id） */
    private Long productId;

    /** 商品唯一键（快照） */
    private String productKey;

    /** 商品名（快照） */
    private String productName;

    /** 购买时价格（Coin，快照） */
    private Integer priceCoin;
}
