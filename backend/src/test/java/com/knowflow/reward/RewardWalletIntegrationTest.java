package com.knowflow.reward;

import com.knowflow.entity.LearningEvent;
import com.knowflow.entity.SysUser;
import com.knowflow.entity.Wallet;
import com.knowflow.mapper.RewardLedgerMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.mapper.WalletMapper;
import com.knowflow.vo.WalletVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture-2 集成测试：钱包惰性初始化 + 奖励引擎（幂等 / 每日上限 / 不奖励事件）。
 * 遵循项目既有 @SpringBootTest + @ActiveProfiles("local")（H2）测试风格。
 */
@SpringBootTest
@ActiveProfiles("local")
class RewardWalletIntegrationTest {

    @Autowired
    private WalletService walletService;
    @Autowired
    private RewardLedgerService ledgerService;
    @Autowired
    private RewardEngine rewardEngine;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private WalletMapper walletMapper;
    @Autowired
    private RewardLedgerMapper ledgerMapper;

    private Long uid;

    @BeforeEach
    void setup() {
        SysUser u = new SysUser();
        u.setUsername("reward_test_" + System.nanoTime());
        u.setPassword("");
        u.setNickname("奖励测试");
        u.setRole("USER");
        u.setExp(500); // 模拟老用户已有入账经验
        sysUserMapper.insert(u);
        uid = u.getId();
    }

    @Test
    @DisplayName("老用户钱包惰性初始化：xp = sys_user.exp，coin = 0，level 由公式计算（§7/§223）")
    void lazyInitFromSysUserExp() {
        Wallet w = walletService.getOrCreate(uid);
        assertEquals(500, w.getXp());
        assertEquals(0, w.getCoin());
        assertEquals(6, w.getLevel()); // 500/100+1
        // 幂等：再次获取同一钱包
        Wallet again = walletService.getOrCreate(uid);
        assertEquals(w.getId(), again.getId());
    }

    @Test
    @DisplayName("钱包能力由服务端角色决定：ADMIN 无限，普通用户有限")
    void walletUnlimitedCapabilityComesFromPersistedRole() {
        WalletVO regular = walletService.getWalletVO(uid);
        assertFalse(regular.getUnlimited());
        assertEquals(0, regular.getCoin());

        SysUser admin = new SysUser();
        admin.setUsername("reward_admin_" + System.nanoTime());
        admin.setPassword("");
        admin.setNickname("无限钱包管理员");
        admin.setRole("ADMIN");
        admin.setExp(0);
        sysUserMapper.insert(admin);

        WalletVO unlimited = walletService.getWalletVO(admin.getId());
        assertTrue(unlimited.getUnlimited());
        assertEquals(0, unlimited.getCoin());
        assertNotNull(unlimited.getLevel());
    }

    @Test
    @DisplayName("同一事件重复 3 次只奖励一次（幂等，§29/§207）")
    void rewardIdempotent() {
        walletService.getOrCreate(uid);
        LearningEvent ev = new LearningEvent();
        ev.setId(9001L);
        ev.setUserId(uid);
        ev.setEventType("CHAPTER_COMPLETE");
        for (int i = 0; i < 3; i++) {
            rewardEngine.onEvent(ev);
        }
        Wallet w = walletService.getOrCreate(uid);
        assertEquals(530, w.getXp());   // 500 + 30（一次）
        assertEquals(5, w.getCoin());   // 5（一次）
        assertEquals(2, ledgersForUser()); // 一笔奖励 = XP + COIN 两行流水（幂等后仍只有一次）
    }

    @Test
    @DisplayName("不奖励事件：文档阅读/AI对话/页面查看 不产生任何流水（§18/§209）")
    void noRewardForViewEvents() {
        walletService.getOrCreate(uid);
        rewardEngine.onEvent(event(8001L, "DOCUMENT_READ"));
        rewardEngine.onEvent(event(8002L, "AI_CHAT"));
        rewardEngine.onEvent(event(8003L, "KNOWLEDGE_VIEWED"));
        Wallet w = walletService.getOrCreate(uid);
        assertEquals(500, w.getXp());
        assertEquals(0, w.getCoin());
        assertEquals(0, ledgersForUser());
    }

    @Test
    @DisplayName("CHECK_IN 每日上限 1：同一天第二个签到事件不再奖励（§33/§208）")
    void dailyLimitForCheckIn() {
        walletService.getOrCreate(uid);
        rewardEngine.onEvent(event(7001L, "CHECK_IN"));
        rewardEngine.onEvent(event(7002L, "CHECK_IN")); // 同日第二次，超限
        Wallet w = walletService.getOrCreate(uid);
        assertEquals(510, w.getXp());   // 500 + 10（仅第一次）
        assertEquals(2, w.getCoin());   // 2（仅第一次）
    }

    @Test
    @DisplayName("CODE_SUBMITTED 仅 passed=true 才奖励（§17/§28）")
    void codeSubmittedOnlyWhenPassed() {
        walletService.getOrCreate(uid);
        LearningEvent fail = event(6001L, "CODE_SUBMITTED");
        fail.setMetadata("{\"passed\":false}");
        rewardEngine.onEvent(fail);
        assertEquals(0, ledgersForUser());

        LearningEvent pass = event(6002L, "CODE_SUBMITTED");
        pass.setMetadata("{\"passed\":true}");
        rewardEngine.onEvent(pass);
        Wallet w = walletService.getOrCreate(uid);
        assertEquals(510, w.getXp());
        assertEquals(2, w.getCoin());
    }

    private long ledgersForUser() {
        return ledgerMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.knowflow.entity.RewardLedger>()
                .eq(com.knowflow.entity.RewardLedger::getUserId, uid));
    }

    private LearningEvent event(Long id, String type) {
        LearningEvent ev = new LearningEvent();
        ev.setId(id);
        ev.setUserId(uid);
        ev.setEventType(type);
        return ev;
    }
}
