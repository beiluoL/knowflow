package com.knowflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 购买请求（§56/§220）：只允许 productId。
 * 禁止携带 userId（SecurityContext 获取，§102）与 priceCoin（价格服务端唯一可信，§38/§330）。
 */
@Data
public class PurchaseRequest {

    @NotNull(message = "商品ID不能为空")
    private Long productId;
}
