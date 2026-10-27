package com.knowflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品（App = 能力，Product = 商品）。
 * 价格 price_coin 为服务端唯一可信来源；前端购买请求不得携带价格（§38/§103）。
 * 状态 ACTIVE / DISABLED：DISABLED 下架 = 老用户仍可用、新用户不可购买（§133）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {

    /** 商品唯一键（uk_product_key） */
    private String productKey;

    /** 商品名（如 Feynman Pro） */
    private String name;

    /** 商品描述 */
    private String description;

    /** 商品类型：APP / AI_CAPABILITY / CONTENT（MVP 只做 APP，§53） */
    private String type;

    /** 关联 AppDefinition.key（Architecture-1 前端静态 Registry） */
    private String appKey;

    /** 价格（Coin，整数，禁止 FLOAT/DOUBLE，§79/§142） */
    private Integer priceCoin;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 版本（第一版只保存版本信息，不做升级系统，§72） */
    private String version;
}
