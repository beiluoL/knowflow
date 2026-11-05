package com.knowflow.store;

import com.knowflow.store.entity.Product;
import com.knowflow.identity.entity.SysUser;
import com.knowflow.reward.entity.Wallet;
import com.knowflow.exception.BusinessException;
import com.knowflow.store.mapper.ProductMapper;
import com.knowflow.identity.mapper.SysUserMapper;
import com.knowflow.reward.mapper.WalletMapper;
import com.knowflow.utils.JwtUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 验证商店映射注册、购买归属、并发扣款及事务边界。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StoreBoundaryIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtUtils jwt;
    @Autowired SysUserMapper users;
    @Autowired WalletMapper wallets;
    @Autowired ProductMapper products;
    @Autowired PurchaseService purchases;
    @Autowired JdbcTemplate jdbc;
    @Autowired SqlSessionFactory sessions;
    @Autowired ApplicationContext context;

    @Test
    void storeMapperBeansUseOnlyTheMovedNamespaces() {
        assertMapperNamespace("ProductMapper");
        assertMapperNamespace("EntitlementMapper");
        assertMapperNamespace("OrderMapper");
        assertMapperNamespace("OrderItemMapper");
    }

    private void assertMapperNamespace(String name) {
        String namespace = "com.knowflow.store.mapper." + name;
        var configuration = sessions.getConfiguration();
        var mapper = configuration.getMapperRegistry().getMappers().stream()
                .filter(type -> type.getSimpleName().equals(name)).toList();
        assertEquals(1, mapper.size());
        assertEquals(namespace, mapper.get(0).getName());
        assertEquals(1, context.getBeansOfType(mapper.get(0)).size());
        assertTrue(configuration.hasStatement(namespace + ".selectById"));
        assertFalse(configuration.hasStatement("com.knowflow.mapper." + name + ".selectById"));
    }

    @Test
    void storeHttpContractKeepsOwnerIsolationAndLegacyErrors() throws Exception {
        long buyer = userWithBalance(100);
        long other = userWithBalance(0);
        Product product = product();
        mvc.perform(get("/api/store/products")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/api/store/products").param("type", "app").header("Authorization", token(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[?(@.productKey == '" + product.getProductKey() + "')]").isNotEmpty());
        String path = "/api/store/products/" + product.getId();
        mvc.perform(get(path).header("Authorization", token(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.owned").value(false));
        mvc.perform(get("/api/store/products/" + Long.MAX_VALUE).header("Authorization", token(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
        var purchase = post("/api/store/purchase").header("Authorization", token(buyer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":" + product.getId() + "}");
        mvc.perform(purchase).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.balanceAfter").value(50))
                .andExpect(jsonPath("$.data.orderNo").isString());
        mvc.perform(purchase).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.message").value("该应用已拥有"));
        mvc.perform(get(path).header("Authorization", token(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.owned").value(false));
        mvc.perform(get("/api/store/owned").header("Authorization", token(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/store/owned").header("Authorization", token(buyer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].productKey").value(product.getProductKey()));
    }

    @Test
    void concurrentSameProductCommitsOneCompletePurchase() throws Exception {
        long buyer = userWithBalance(100);
        long productId = product().getId();
        assertTrue(AopUtils.isAopProxy(purchases));
        List<Integer> outcomes = race(buyer, productId, productId);
        assertTrue(outcomes.contains(2002) || outcomes.contains(-1), outcomes.toString());
        assertPurchaseCounts(buyer, 50);
    }

    @Test
    void concurrentDifferentProductsCannotOverdrawWallet() throws Exception {
        long buyer = userWithBalance(50);
        assertEquals(List.of(200, 2001), race(buyer, product().getId(), product().getId()));
        assertPurchaseCounts(buyer, 0);
    }

    private List<Integer> race(long buyer, long firstProduct, long secondProduct) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> attempt(buyer, firstProduct, ready, start));
            var second = executor.submit(() -> attempt(buyer, secondProduct, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
                    .stream().sorted().toList();
            assertEquals(1, outcomes.stream().filter(code -> code == 200).count());
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private int attempt(long buyer, long productId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            purchases.purchase(buyer, productId);
            return 200;
        } catch (BusinessException error) {
            return error.getCode();
        } catch (DuplicateKeyException collision) {
            return -1;
        }
    }

    private void assertPurchaseCounts(long buyer, int balance) {
        assertEquals(balance, jdbc.queryForObject("SELECT coin FROM wallet WHERE user_id=?", Integer.class, buyer));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_ WHERE user_id=?", Integer.class, buyer));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_item i JOIN order_ o ON o.id=i.order_id WHERE o.user_id=?",
                Integer.class, buyer));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM entitlement WHERE user_id=?", Integer.class, buyer));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reward_ledger WHERE user_id=?", Integer.class, buyer));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_app WHERE user_id=? AND installed=1 AND enabled=1", Integer.class, buyer));
    }

    private long userWithBalance(int balance) {
        var user = new SysUser();
        user.setUsername(UUID.randomUUID().toString());
        user.setPassword("fixture");
        user.setRole("USER");
        users.insert(user);
        var wallet = new Wallet();
        wallet.setUserId(user.getId());
        wallet.setCoin(balance);
        wallet.setXp(0);
        wallet.setLevel(1);
        wallets.insert(wallet);
        return user.getId();
    }

    private Product product() {
        var product = new Product();
        product.setProductKey("fixture-" + UUID.randomUUID());
        product.setName("Store fixture");
        product.setType("APP");
        product.setAppKey("feynman");
        product.setStatus("ACTIVE");
        product.setPriceCoin(50);
        products.insert(product);
        return product;
    }

    private String token(long userId) {
        return "Bearer " + jwt.generateToken(userId, "store-fixture", "USER");
    }
}
