package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.dto.PurchaseRequest;
import com.knowflow.store.EntitlementService;
import com.knowflow.store.ProductService;
import com.knowflow.store.PurchaseService;
import com.knowflow.vo.ProductVO;
import com.knowflow.vo.PurchaseResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 应用商店 REST 接口（§57）：商品列表/详情/已拥有/购买。
 * 购买只接收 productId（§56）；价格/userId 均由服务端决定（§38/§102）。
 */
@Tag(name = "应用商店接口")
@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StoreController {

    private final ProductService productService;
    private final EntitlementService entitlementService;
    private final PurchaseService purchaseService;

    @Operation(summary = "商品列表（含 owned）")
    @GetMapping("/products")
    public Result<List<ProductVO>> products(@RequestParam(required = false) String type) {
        return Result.success(productService.listProducts(type, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "商品详情")
    @GetMapping("/products/{id}")
    public Result<ProductVO> product(@PathVariable Long id) {
        return Result.success(productService.getProductVO(id, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "我拥有的商品")
    @GetMapping("/owned")
    public Result<List<ProductVO>> owned() {
        return Result.success(entitlementService.listOwned(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "购买（Coin 兑换）")
    @PostMapping("/purchase")
    public Result<PurchaseResultVO> purchase(@Valid @RequestBody PurchaseRequest req) {
        return Result.success(purchaseService.purchase(SecurityUtils.getCurrentUserId(), req.getProductId()));
    }
}
