package com.knowflow.apps;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.apps.entity.UserApp;
import com.knowflow.apps.mapper.UserAppMapper;
import com.knowflow.identity.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.identity.mapper.SysUserMapper;
import com.knowflow.utils.JwtUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Architecture-3 集成测试：UserApp 后端化（§374）。
 * 默认初始化 / 查询 / 更新 / 幂等 / 用户隔离 / 非法 appKey / 非法状态组合归一化。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class UserAppIntegrationTest {

    @Autowired
    private UserAppService userAppService;
    @Autowired
    private UserAppMapper userAppMapper;
    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtils jwt;

    @Test
    void appsHttpContract() throws Exception {
        mvc.perform(get("/api/apps/user-apps"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        String bearer = "Bearer " + jwt.generateToken(uid, "apps-fixture", "USER");
        mvc.perform(put("/api/apps/user-apps/unknown-app")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installed\":true,\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2006))
                .andExpect(jsonPath("$.message").value("无效的应用标识"));
        for (boolean enabled : new boolean[]{true, false, false, true}) {
            mvc.perform(put("/api/apps/user-apps/flashcard")
                            .header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"installed\":true,\"enabled\":" + enabled + ",\"sortOrder\":99}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.enabled").value(enabled))
                    .andExpect(jsonPath("$.data.sortOrder").value(99));
        }
        mvc.perform(get("/api/apps/user-apps").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(5));
        assertTrue(userAppService.getUserApps(uidB).stream().noneMatch(app -> app.getSortOrder() == 99));
    }

    @Autowired
    private ApplicationContext context;
    @Autowired
    private SqlSessionFactory sqlSessionFactory;
    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void mapperRegistersOnceWithNewStatementNamespace() {
        assertEquals(java.util.Set.of("userAppMapper"), context.getBeansOfType(UserAppMapper.class).keySet());
        var configuration = sqlSessionFactory.getConfiguration();
        assertTrue(configuration.hasStatement(UserAppMapper.class.getName() + ".selectList"));
        assertFalse(configuration.hasStatement("com.knowflow.mapper.UserAppMapper.selectList"));
    }

    @Test
    void concurrentInitializationAndUpdatesKeepOneRowPerApp() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            var first = pool.submit(() -> {
                start.await();
                assertEquals(5, userAppService.getUserApps(uid).size());
                return null;
            });
            var second = pool.submit(() -> {
                start.await();
                assertEquals(5, userAppService.getUserApps(uid).size());
                return null;
            });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertEquals(5, userAppMapper.selectCount(new LambdaQueryWrapper<UserApp>()
                    .eq(UserApp::getUserId, uid)));

            var update = new CountDownLatch(1);
            var enable = pool.submit(() -> {
                update.await();
                return userAppService.upsert(uid, "flashcard", true, true, 51);
            });
            var disable = pool.submit(() -> {
                update.await();
                return userAppService.upsert(uid, "flashcard", true, false, 52);
            });
            update.countDown();
            assertTrue(enable.get(10, TimeUnit.SECONDS).getEnabled());
            assertFalse(disable.get(10, TimeUnit.SECONDS).getEnabled());
            var persisted = userAppMapper.selectList(new LambdaQueryWrapper<UserApp>()
                    .eq(UserApp::getUserId, uid).eq(UserApp::getAppKey, "flashcard"));
            assertEquals(1, persisted.size());
            assertEquals(persisted.get(0).getSortOrder() == 51 ? 1 : 0, persisted.get(0).getEnabled());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void initializationParticipatesInCallerRollback() {
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            assertEquals(5, userAppService.getUserApps(uid).size());
            transaction.setRollbackOnly();
        });
        assertEquals(0, userAppMapper.selectCount(new LambdaQueryWrapper<UserApp>()
                .eq(UserApp::getUserId, uid)));
    }

    private Long uid;
    private Long uidB;

    @BeforeEach
    void setup() {
        uid = createUser("ua_test_a");
        uidB = createUser("ua_test_b");
    }

    private Long createUser(String prefix) {
        SysUser u = new SysUser();
        u.setUsername(prefix + "_" + System.nanoTime());
        u.setPassword("");
        u.setNickname(prefix);
        u.setRole("USER");
        sysUserMapper.insert(u);
        return u.getId();
    }

    @Test
    @DisplayName("默认初始化：新用户自动安装 flashcard/quiz/mistake/focus/mock-interview（幂等，§39-40/§412）")
    void defaultInit() {
        var apps = userAppService.getUserApps(uid);
        assertEquals(5, apps.size());
        assertTrue(apps.stream().anyMatch(a -> a.getAppKey().equals("flashcard") && a.getInstalled() && a.getEnabled()));
        assertTrue(apps.stream().anyMatch(a -> a.getAppKey().equals("focus") && a.getInstalled() && a.getEnabled()));
        // Phase 3H：模拟面试基础模式随新用户默认安装
        assertTrue(apps.stream().anyMatch(a -> a.getAppKey().equals("mock-interview") && a.getInstalled() && a.getEnabled()));
        // 幂等：重复调用不产生重复记录
        userAppService.getUserApps(uid);
        userAppService.getUserApps(uid);
        assertEquals(5, userAppMapper.selectCount(new LambdaQueryWrapper<UserApp>()
                .eq(UserApp::getUserId, uid)));
    }

    @Test
    @DisplayName("更新幂等：同一 appKey 连续 PUT 只产生一条记录（§36-38/§332）")
    void upsertIdempotent() {
        for (int i = 0; i < 3; i++) {
            userAppService.upsert(uid, "feynman", true, true, 1);
        }
        assertEquals(1, userAppMapper.selectCount(new LambdaQueryWrapper<UserApp>()
                .eq(UserApp::getUserId, uid)
                .eq(UserApp::getAppKey, "feynman")));
    }

    @Test
    @DisplayName("非法 appKey 拒绝（unknown-app / 路径穿越，§34-35/§345）")
    void invalidAppKey() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userAppService.upsert(uid, "unknown-app", true, true, 0));
        assertEquals(2006, ex.getCode());
        assertThrows(BusinessException.class,
                () -> userAppService.upsert(uid, "../../admin", true, true, 0));
    }

    @Test
    @DisplayName("非法状态组合归一化：installed=false → enabled 强制 false（§266-267）")
    void normalizeInvalidState() {
        var vo = userAppService.upsert(uid, "coding", false, true, 0);
        assertFalse(vo.getInstalled());
        assertFalse(vo.getEnabled());
    }

    @Test
    @DisplayName("用户隔离：A 的启用不影响 B（§327/§344）")
    void userIsolation() {
        userAppService.upsert(uid, "feynman", true, true, 0);
        userAppService.upsert(uidB, "feynman", true, false, 0);
        var a = userAppService.getUserApps(uid).stream()
                .filter(v -> v.getAppKey().equals("feynman")).findFirst().orElseThrow();
        var b = userAppService.getUserApps(uidB).stream()
                .filter(v -> v.getAppKey().equals("feynman")).findFirst().orElseThrow();
        assertTrue(a.getEnabled());
        assertFalse(b.getEnabled());
    }
}
