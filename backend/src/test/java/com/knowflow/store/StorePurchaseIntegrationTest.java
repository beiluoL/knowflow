package com.knowflow.store;

import com.knowflow.entity.Entitlement;
import com.knowflow.entity.Order;
import com.knowflow.entity.Product;
import com.knowflow.entity.SysUser;
import com.knowflow.entity.Wallet;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.EntitlementMapper;
import com.knowflow.mapper.OrderItemMapper;
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

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture-2 集成测试：购买事务（成功 / 余额不足回滚 / 重复购买 / 商品下架）。
 * 关键验证：§69-70 严禁部分成功；§71 ALREADY_OWNED；§41 条件扣款。
 */
@SpringBootTest
@ActiveProfiles("local")
class StorePurchaseIntegrationTest {

    @Autowired
    private PurchaseService purchaseService;
    @Autowired
    private ProductService productService;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private WalletMapper walletMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private EntitlementMapper entitlementMapper;
    @Autowired
    private UserAppMapper userAppMapper;
    @Autowired
    private RewardLedgerMapper ledgerMapper;

    private Long uid;
    private Product product;

    @BeforeEach
    void setup() {
        SysUser u = new SysUser();
        u.setUsername("store_test_" + System.nanoTime());
        u.setPassword("");
        u.setNickname("商店测试");
        u.setRole("USER");
        u.setExp(0);
        sysUserMapper.insert(u);
        uid = u.getId();

        product = new Product();
        product.setProductKey("test_pro_" + System.nanoTime());
        product.setName("测试商品 Pro");
        product.setType("APP");
        product.setAppKey("feynman");
        product.setPriceCoin(50);
        product.setStatus("ACTIVE");
        product.setVersion("1.0.0");
        productMapper.insert(product);
    }

    private void walletWith(int coin) {
        Wallet w = new Wallet();
        w.setUserId(uid);
        w.setXp(0);
        w.setCoin(coin);
        w.setLevel(1);
        walletMapper.insert(w);
    }

    private long orderItemsForUser() {
        List<com.knowflow.entity.Order> list = orderMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, uid));
        if (list.isEmpty()) return 0;
        List<Long> ids = list.stream().map(com.knowflow.entity.Order::getId).collect(java.util.stream.Collectors.toList());
        return orderItemMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.knowflow.entity.OrderItem>()
                .in(com.knowflow.entity.OrderItem::getOrderId, ids));
    }

    private long ledgersForUser() {
        return ledgerMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.knowflow.entity.RewardLedger>()
                .eq(com.knowflow.entity.RewardLedger::getUserId, uid));
    }

    private long orders() {
        return orderMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, uid));
    }

    @Test
    @DisplayName("购买成功：Coin 减少 + Order + OrderItem + Entitlement + Ledger(DEBIT) 全创建（§67/§320）")
    void purchaseSuccess() {
        walletWith(100);
        var r = purchaseService.purchase(uid, product.getId());
        assertNotNull(r.getOrderNo());
        assertEquals(50, r.getBalanceAfter());
        Wallet w = walletMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Wallet>()
                .eq(Wallet::getUserId, uid));
        assertEquals(50, w.getCoin());
        assertEquals(1, orders());
        assertEquals(1, orderItemsForUser());
        assertEquals(1, entitlementMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Entitlement>()
                .eq(Entitlement::getUserId, uid).eq(Entitlement::getStatus, "ACTIVE")));
        // Architecture-3：购买成功后 UserApp installed+enabled=true（同事务，§270-275/§341）
        var userApp = userAppMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.knowflow.entity.UserApp>()
                .eq(com.knowflow.entity.UserApp::getUserId, uid)
                .eq(com.knowflow.entity.UserApp::getAppKey, "feynman"));
        assertNotNull(userApp);
        assertEquals(1, userApp.getInstalled());
        assertEquals(1, userApp.getEnabled());
        assertEquals(1, ledgersForUser()); // 一笔 PURCHASE_APP DEBIT
        // 商品详情 owned=true（§58/§284：来自 Entitlement）
        assertTrue(productService.getProductVO(product.getId(), uid).getOwned());
    }

    @Test
    @DisplayName("余额不足：整体回滚，钱包/订单/资格/流水全部不变（§41/§70/§321）")
    void insufficientBalanceRollback() {
        walletWith(10);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> purchaseService.purchase(uid, product.getId()));
        assertEquals(EconomyError.INSUFFICIENT_BALANCE, ex.getCode());
        Wallet w = walletMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Wallet>()
                .eq(Wallet::getUserId, uid));
        assertEquals(10, w.getCoin());
        assertEquals(0, orders());
        assertEquals(0, orderItemsForUser());
        assertEquals(0, entitlementMapper.selectCount(null));
        assertEquals(0, userAppMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.knowflow.entity.UserApp>()
                .eq(com.knowflow.entity.UserApp::getUserId, uid))); // 余额不足：UserApp 也不得创建（§342）
        assertEquals(0, ledgersForUser());
    }

    @Test
    @DisplayName("重复购买：ALREADY_OWNED，不重复扣款（§71/§94）")
    void alreadyOwned() {
        walletWith(200);
        purchaseService.purchase(uid, product.getId());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> purchaseService.purchase(uid, product.getId()));
        assertEquals(EconomyError.ALREADY_OWNED, ex.getCode());
        Wallet w = walletMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Wallet>()
                .eq(Wallet::getUserId, uid));
        assertEquals(150, w.getCoin()); // 只扣一次
        assertEquals(1, orders());
    }

    @Test
    @DisplayName("商品下架：PRODUCT_DISABLED 拒绝购买（§72/§133）")
    void disabledProduct() {
        walletWith(500);
        Product disabled = new Product();
        disabled.setProductKey("disabled_pro_" + System.nanoTime());
        disabled.setName("已下架商品");
        disabled.setType("APP");
        disabled.setAppKey("quiz");
        disabled.setPriceCoin(10);
        disabled.setStatus("DISABLED");
        productMapper.insert(disabled);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> purchaseService.purchase(uid, disabled.getId()));
        assertEquals(EconomyError.PRODUCT_DISABLED, ex.getCode());
        assertEquals(500, walletMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Wallet>()
                .eq(Wallet::getUserId, uid)).getCoin());
    }
}
