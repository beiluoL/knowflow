package com.knowflow.reward;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.identity.contract.RewardAccountQuery;
import com.knowflow.reward.entity.Wallet;
import com.knowflow.reward.mapper.WalletMapper;
import com.knowflow.vo.WalletVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 钱包服务：余额读取 + 内部 credit + 内部条件 debit（§38-39）。 写操作只允许 RewardEngine / PurchaseService
 * 调用（§92-95）——本类方法均为内部语义， Controller 只调用只读的 getWalletVO / 查询。 Coin 不允许为负：扣款用条件更新 coin >=
 * ?（§27/§40-41）。
 */
@Service
@RequiredArgsConstructor
public class WalletService {

  private final WalletMapper walletMapper;
  private final RewardAccountQuery accounts;
  private final LevelService levelService;

  /**
   * 获取或惰性初始化钱包（老用户迁移，§7/§222-223）： 首次访问时 wallet.xp = sys_user.exp（种子），coin = 0，level =
   * LevelService(xp)。幂等。
   */
  @Transactional
  public Wallet getOrCreate(Long userId) {
    Wallet wallet =
        walletMapper.selectOne(new LambdaQueryWrapper<Wallet>().eq(Wallet::getUserId, userId));
    if (wallet != null) {
      return wallet;
    }
    int seedXp = accounts.find(userId).legacyXp();
    Wallet created = new Wallet();
    created.setUserId(userId);
    created.setXp(seedXp);
    created.setCoin(0);
    created.setLevel(levelService.levelOf(seedXp));
    try {
      walletMapper.insert(created);
      return created;
    } catch (DuplicateKeyException e) {
      Wallet existing = selectForUpdate(userId);
      if (existing == null) {
        throw e;
      }
      return existing;
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

  /** 返回当前钱包金币余额，缺失时按既有规则初始化。 */
  public int coinBalance(Long userId) {
    return getOrCreate(userId).getCoin();
  }

  /** 无限消费能力只由数据库中的有效 ADMIN 角色决定，前端无法授予。 */
  public boolean isUnlimited(Long userId) {
    return accounts.find(userId).unlimited();
  }

  /** 内部加币（仅 RewardEngine 调用，§93/§165）。 返回更新后的 Wallet（供 Ledger 记录 balance_after）。 */
  @Transactional
  public Wallet credit(Long userId, int xp, int coin) {
    return creditLocked(lockForReward(userId), xp, coin);
  }

  /** 锁持续至调用方事务结束，包含限额检查与流水追加。 */
  Wallet lockForReward(Long userId) {
    getOrCreate(userId);
    Wallet wallet = selectForUpdate(userId);
    if (wallet == null) {
      throw new IllegalStateException("Wallet missing after initialization");
    }
    return wallet;
  }

  private Wallet selectForUpdate(Long userId) {
    return walletMapper.selectOne(
        new LambdaQueryWrapper<Wallet>().eq(Wallet::getUserId, userId).last("FOR UPDATE"));
  }

  /** 更新调用方已锁定的钱包。 */
  Wallet creditLocked(Wallet wallet, int xp, int coin) {
    if (xp < 0 || coin < 0) {
      throw new IllegalArgumentException("Credit amounts must be nonnegative");
    }
    int newXp = Math.addExact(wallet.getXp(), xp);
    int newCoin = Math.addExact(wallet.getCoin(), coin);
    wallet.setXp(newXp);
    wallet.setCoin(newCoin);
    wallet.setLevel(levelService.levelOf(newXp));
    if (walletMapper.updateById(wallet) != 1) {
      throw new IllegalStateException("Wallet credit did not update one row");
    }
    return wallet;
  }

  /**
   * 内部条件扣款（仅 PurchaseService 调用，§40-41/§105）。 条件更新 coin >= coin，影响行数 0 表示余额不足（由调用方抛
   * INSUFFICIENT_BALANCE）。
   */
  @Transactional
  public boolean debitCoin(Long userId, int coin) {
    if (coin <= 0) {
      throw new IllegalArgumentException("Debit amount must be positive");
    }
    getOrCreate(userId);
    return walletMapper.debitCoin(userId, coin) == 1;
  }
}
