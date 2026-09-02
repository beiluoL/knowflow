package com.knowflow.apps;

import com.knowflow.entity.SysUser;
import com.knowflow.exception.BusinessException;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.mapper.UserAppMapper;
import com.knowflow.store.EconomyError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture-3 集成测试：UserApp 后端化（§374）。
 * 默认初始化 / 查询 / 更新 / 幂等 / 用户隔离 / 非法 appKey / 非法状态组合归一化。
 */
@SpringBootTest
@ActiveProfiles("local")
class UserAppIntegrationTest {

    @Autowired
    private UserAppService userAppService;
    @Autowired
    private UserAppMapper userAppMapper;
    @Autowired
    private SysUserMapper sysUserMapper;

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
    @DisplayName("默认初始化：新用户自动安装 flashcard/quiz/mistake/focus（幂等，§39-40/§412）")
    void defaultInit() {
        var apps = userAppService.getUserApps(uid);
        assertEquals(4, apps.size());
        assertTrue(apps.stream().anyMatch(a -> a.getAppKey().equals("flashcard") && a.getInstalled() && a.getEnabled()));
        assertTrue(apps.stream().anyMatch(a -> a.getAppKey().equals("focus") && a.getInstalled() && a.getEnabled()));
        // 幂等：重复调用不产生重复记录
        userAppService.getUserApps(uid);
        userAppService.getUserApps(uid);
        assertEquals(4, userAppMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.knowflow.entity.UserApp>()
                .eq(com.knowflow.entity.UserApp::getUserId, uid)));
    }

    @Test
    @DisplayName("更新幂等：同一 appKey 连续 PUT 只产生一条记录（§36-38/§332）")
    void upsertIdempotent() {
        for (int i = 0; i < 3; i++) {
            userAppService.upsert(uid, "feynman", true, true, 1);
        }
        assertEquals(1, userAppMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.knowflow.entity.UserApp>()
                .eq(com.knowflow.entity.UserApp::getUserId, uid)
                .eq(com.knowflow.entity.UserApp::getAppKey, "feynman")));
    }

    @Test
    @DisplayName("非法 appKey 拒绝（unknown-app / 路径穿越，§34-35/§345）")
    void invalidAppKey() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userAppService.upsert(uid, "unknown-app", true, true, 0));
        assertEquals(EconomyError.INVALID_APP, ex.getCode());
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
