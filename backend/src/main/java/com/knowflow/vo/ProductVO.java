package com.knowflow.vo;

import lombok.Data;

/** 商品展示（App = 能力，Product = 商品，§34/§260）。 */
@Data
public class ProductVO {

    private Long id;
    private String productKey;
    private String name;
    private String description;
    /** 类型：APP / AI_CAPABILITY / CONTENT */
    private String type;
    /** 关联 AppDefinition.key（Architecture-1） */
    private String appKey;
    /** 价格（Coin，服务端唯一可信） */
    private Integer priceCoin;
    /** ACTIVE / DISABLED */
    private String status;
    private String version;
    /** 当前用户是否已拥有（来自 Entitlement，§58/§284） */
    private Boolean owned;
}
