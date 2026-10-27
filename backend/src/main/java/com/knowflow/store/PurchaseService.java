package com.knowflow.store;

import com.knowflow.entity.Order;
import com.knowflow.entity.Product;
import com.knowflow.entity.Wallet;
import com.knowflow.exception.BusinessException;
import com.knowflow.apps.OfficialAppRegistry;
import com.knowflow.apps.UserAppService;
import com.knowflow.reward.RewardLedgerService;
import com.knowflow.reward.WalletService;
import com.knowflow.vo.PurchaseResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 购买服务：唯一购买事务入口（§66/§90/§161）。
 *
 * 事务内严格顺序（§67/§214）：Product → App 可用性 → Entitlement 校验 → 条件扣 Coin
 * → Order → OrderItem → Entitlement 授予 → UserApp 安装启用 → Ledger（DEBIT）。任一步失败整体回滚（§69-70）。
 * Architecture-3：购买前校验 App.status==ACTIVE（APP_DISABLED 门禁，§72/§150-153）；
 * 购买成功后同事务同步 UserApp installed=true enabled=true（§270-274），绝不出现「购买成功但 App 不可启动」（§275）。
 * 价格一律服务端读取（§38/§103）；userId 由 SecurityContext 传入（§83/§102）。
 */
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final ProductService productService;
    private final EntitlementService entitlementService;
    private final WalletService walletService;
    private final OrderService orderService;
    private final RewardLedgerService ledgerService;
    private final OfficialAppRegistry officialAppRegistry;
    private final UserAppService userAppService;

    @Transactional
    public PurchaseResultVO purchase(Long userId, Long productId) {
        Product product = productService.getProduct(productId);
        if (product == null) {
            throw new BusinessException(EconomyError.INVALID_PRODUCT, "商品不存在");
        }
        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
            throw new BusinessException(EconomyError.PRODUCT_DISABLED, "该应用暂时无法购买");
        }
        // Architecture-3：App 平台可用性门禁（§72/§151-153）——先校验再扣款，绝不「扣了 Coin 才发现 App 停用」
        if (product.getAppKey() != null && !product.getAppKey().isBlank()
                && !officialAppRegistry.isActive(product.getAppKey())) {
            throw new BusinessException(EconomyError.APP_DISABLED, "该应用暂不可用");
        }
        if (entitlementService.hasActive(userId, productId)) {
            throw new BusinessException(EconomyError.ALREADY_OWNED, "该应用已拥有");
        }
        boolean unlimited = walletService.isUnlimited(userId);
        if (!unlimited) {
            // 条件扣款：coin >= price，影响行数 0 → 余额不足（回滚，§41/§75）
            boolean debited = walletService.debitCoin(userId, product.getPriceCoin());
            if (!debited) {
                Wallet w = walletService.getOrCreate(userId);
                int missing = Math.max(0, product.getPriceCoin() - w.getCoin());
                throw new BusinessException(EconomyError.INSUFFICIENT_BALANCE,
                        "余额不足，还差 " + missing + " Coin，去学习赚取更多金币");
            }
        }
        Order order = orderService.create(userId, product, product.getPriceCoin());
        entitlementService.grant(userId, product);
        // Architecture-3：购买成功 → UserApp installed+enabled（同事务，§270-276）
        if (product.getAppKey() != null && !product.getAppKey().isBlank()) {
            userAppService.markInstalledAndEnabled(userId, product.getAppKey());
        }
        Wallet after = walletService.getOrCreate(userId);
        if (!unlimited) {
            ledgerService.append(userId, "COIN", -product.getPriceCoin(), "DEBIT",
                    "PURCHASE_APP", "PURCHASE", order.getId(), after.getCoin());
        }

        PurchaseResultVO vo = new PurchaseResultVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setProductName(product.getName());
        vo.setPriceCoin(product.getPriceCoin());
        vo.setBalanceAfter(after.getCoin());
        vo.setUnlimited(unlimited);
        return vo;
    }
}
