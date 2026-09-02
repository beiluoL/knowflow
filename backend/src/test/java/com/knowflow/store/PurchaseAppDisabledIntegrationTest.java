package com.knowflow.store;

import com.knowflow.entity.Product;
import com.knowflow.entity.SysUser;
import com.knowflow.entity.Wallet;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.EntitlementMapper;
import com.knowflow.mapper.OrderMapper;
import com.knowflow.mapper.ProductMapper;
import com.knowflow.mapper.RewardLedgerMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.mapper.UserAppMapper;
import com.knowflow.mapper.WalletMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture-3 集成测试：App 可用性购买门禁（§72/§151-153/§335-336）。
 * 使用真实 OfficialAppRegistry：product.app_key 指向「非 ACTIVE」的 App（未知/停用）→ APP_DISABLED，
 * Coin/Order/Entitlement/UserApp/Ledger 全部不变（§342）。不用 @MockBean，避免新建 Spring 上下文。
 */
@SpringBootTest
@ActiveProfiles("local")
class PurchaseAppDisabledIntegrationTest {

    @Autowired
    private PurchaseService purchaseService;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private WalletMapper walletMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private EntitlementMapper entitlementMapper;
    @Autowired
    private RewardLedgerMapper ledgerMapper;
    @Autowired
    private UserAppMapper userAppMapper;

    private Long uid;
    private Product product;

    @BeforeEach
    void setup() {
        SysUser u = new SysUser();
        u.setUsername("pad_test_" + System.nanoTime());
        u.setPassword("");
        u.setNickname("门禁测试");
        u.setRole("USER");
        sysUserMapper.insert(u);
        uid = u.getId();

        Wallet w = new Wallet();
        w.setUserId(uid);
        w.setXp(0);
        w.setCoin(500);
        w.setLevel(1);
        walletMapper.insert(w);

        product = new Product();
        product.setProductKey("pad_pro_" + System.nanoTime());
        product.setName("门禁测试商品");
        product.setType("APP");
        product.setAppKey("unknown-app"); // 不在官方注册表 → App 不可用（§72/§336）
        product.setPriceCoin(50);
        product.setStatus("ACTIVE"); // Product 可用，但 App 不可用
        productMapper.insert(product);
    }

    @Test
    @DisplayName("App 不可用：即使 Product ACTIVE 也拒绝购买（APP_DISABLED），且全部状态不变（§336/§342）")
    void appDisabledBlocksPurchase() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> purchaseService.purchase(uid, product.getId()));
        assertEquals(EconomyError.APP_DISABLED, ex.getCode());
        // 完整回滚：Coin 不变、无订单、无资格、无 UserApp、无流水（§69-70/§342）
        Wallet w = walletMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Wallet>()
                .eq(Wallet::getUserId, uid));
        assertEquals(500, w.getCoin());
        assertEquals(0, orderMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.knowflow.entity.Order>()
                .eq(com.knowflow.entity.Order::getUserId, uid)));
        assertEquals(0, entitlementMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.knowflow.entity.Entitlement>()
                .eq(com.knowflow.entity.Entitlement::getUserId, uid)));
        assertEquals(0, userAppMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.knowflow.entity.UserApp>()
                .eq(com.knowflow.entity.UserApp::getUserId, uid)
                .eq(com.knowflow.entity.UserApp::getAppKey, "unknown-app")));
        assertEquals(0, ledgerMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.knowflow.entity.RewardLedger>()
                .eq(com.knowflow.entity.RewardLedger::getUserId, uid)));
    }
}
