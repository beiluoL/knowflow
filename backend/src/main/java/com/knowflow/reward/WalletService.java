package com.knowflow.reward;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.knowflow.entity.SysUser;
import com.knowflow.entity.Wallet;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.mapper.WalletMapper;
import com.knowflow.vo.WalletVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 钱包服务：余额读取 + 内部 credit + 内部条件 debit（§38-39）。
 * 写操作只允许 RewardEngine / PurchaseService 调用（§92-95）——本类方法均为内部语义，
 * Controller 只调用只读的 getWalletVO / 查询。
 * Coin 不允许为负：扣款用条件更新 coin >= ?（§27/§40-41）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletMapper walletMapper;
    private final SysUserMapper sysUserMapper;
    private final LevelService levelService;

    /**
     * 获取或惰性初始化钱包（老用户迁移，§7/§222-223）：
     * 首次访问时 wallet.xp = sys_user.exp（种子），coin = 0，level = LevelService(xp)。幂等。
     */
    @Transactional
    public Wallet getOrCreate(Long userId) {
        Wallet wallet = walletMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Wallet>()
                .eq(Wallet::getUserId, userId));
        if (wallet != null) {
            return wallet;
        }
        SysUser user = sysUserMapper.selectById(userId);
        int seedXp = (user != null && user.getExp() != null) ? user.getExp() : 0;
        Wallet created = new Wallet();
        created.setUserId(userId);
        created.setXp(seedXp);
        created.setCoin(0);
        created.setLevel(levelService.levelOf(seedXp));
        try {
            walletMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            // 并发初始化：唯一索引兜底，重查
            return walletMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Wallet>()
                    .eq(Wallet::getUserId, userId));
        }
    }

    /** 钱包展示 VO（含下一级阈值）。 */
    public WalletVO getWalletVO(Long userId) {
        Wallet w = getOrCreate(userId);
        WalletVO vo = new WalletVO();
        vo.setUserId(w.getUserId());
        vo.setXp(w.getXp());
        vo.setCoin(w.getCoin());
        vo.setLevel(w.getLevel());
        vo.setNextLevelExp(levelService.nextLevelExp(w.getXp()));
        vo.setUnlimited(isUnlimited(userId));
        return vo;
    }

    /** 无限消费能力只由数据库中的有效 ADMIN 角色决定，前端无法授予。 */
    public boolean isUnlimited(Long userId) {
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        return user != null && "ADMIN".equals(user.getRole());
    }

    /**
     * 内部加币（仅 RewardEngine 调用，§93/§165）。
     * 返回更新后的 Wallet（供 Ledger 记录 balance_after）。
     */
    @Transactional
    public Wallet credit(Long userId, int xp, int coin) {
        Wallet wallet = getOrCreate(userId);
        int newXp = wallet.getXp() + xp;
        int newCoin = wallet.getCoin() + coin;
        wallet.setXp(newXp);
        wallet.setCoin(newCoin);
        wallet.setLevel(levelService.levelOf(newXp));
        walletMapper.updateById(wallet);
        return wallet;
    }

    /**
     * 内部条件扣款（仅 PurchaseService 调用，§40-41/§105）。
     * 条件更新 coin >= coin，影响行数 0 表示余额不足（由调用方抛 INSUFFICIENT_BALANCE）。
     */
    @Transactional
    public boolean debitCoin(Long userId, int coin) {
        getOrCreate(userId);
        return walletMapper.update(null, new LambdaUpdateWrapper<Wallet>()
                .eq(Wallet::getUserId, userId)
                .ge(Wallet::getCoin, coin)
                .setSql("coin = coin - " + coin)) > 0;
    }
}
