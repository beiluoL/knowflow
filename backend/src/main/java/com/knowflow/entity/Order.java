package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单（order 为数据库保留字，表名用 order_）。
 * 保留购买时价格快照（order_item），未来接真实支付时无需解释历史价格（§42-43）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_")
public class Order extends BaseEntity {

    /** 订单号（uk_order_no 唯一） */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 订单总额（Coin） */
    private Integer totalCoin;

    /** 状态：PAID / REFUNDED（MVP 仅 PAID，§42 预留扩展） */
    private String status;
}
