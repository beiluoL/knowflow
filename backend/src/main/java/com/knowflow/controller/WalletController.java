package com.knowflow.controller;

import com.knowflow.common.Result;
import com.knowflow.common.SecurityUtils;
import com.knowflow.reward.RewardLedgerService;
import com.knowflow.reward.WalletService;
import com.knowflow.vo.LedgerVO;
import com.knowflow.vo.WalletVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 钱包 REST 接口（§43-45）：只读查询；禁止 POST /wallet、POST /reward（§45/§69/§328）。
 * userId 一律来自 SecurityContext（§77-78）。
 */
@Tag(name = "钱包接口")
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final RewardLedgerService ledgerService;

    @Operation(summary = "我的钱包（xp/coin/level）")
    @GetMapping
    public Result<WalletVO> wallet() {
        return Result.success(walletService.getWalletVO(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "我的流水（只读，append-only）")
    @GetMapping("/ledger")
    public Result<List<LedgerVO>> ledger(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String currency) {
        return Result.success(ledgerService.page(SecurityUtils.getCurrentUserId(), currency, page, size));
    }
}
