package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 购买资格：用户拥有某商品的使用资格（Entitlement = 拥有资格；UserApp = 启用状态，§45）。
 * 唯一约束（user_id, product_id）保证同一用户同一商品只有一个 ACTIVE 资格（§61/§282）。
 * 停用 App 只改 UserApp.enabled，绝不删除 Entitlement（§47/§88）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("entitlement")
public class Entitlement extends BaseEntity {

    /** 用户ID */
    private Long userId;

    /** 商品ID（逻辑外键 product.id） */
    private Long productId;

    /** 商品唯一键（快照） */
    private String productKey;

    /** 状态：ACTIVE / REVOKED */
    private String status;

    /** 授予时间 */
    private LocalDateTime grantedAt;
}
