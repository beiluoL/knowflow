package com.knowflow.reward.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.identity.contract.RewardAccountQuery;
import com.knowflow.reward.entity.Wallet;
import com.knowflow.reward.mapper.WalletMapper;
import com.knowflow.reward.vo.WalletVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 钱包服务：维护 wallet 表的当前余额快照（xp / coin / level），并对外提供只读展示与余额查询。
 *
 * <p>与 RewardLedgerService 分工：本类存「现在是多少」，流水存「怎么变的」；余额写入必须伴随流水追加，由调用方在同一事务内保证。
 *
 * <p>生产写入路径只有两条：RewardEngine 走 lockForReward + creditLocked 加币，PurchaseService 走 debitCoin
 * 条件扣币；WalletController 只暴露 GET 查询。userId 由 Controller 从 SecurityUtils 取得后传入，本类不自行解析身份。
 *
 * <p>并发约束：uk_wallet_user(user_id) 不含 deleted；惰性初始化用 DuplicateKeyException 兜底回读；加币路径对钱包行 FOR
 * UPDATE，扣币路径用 SQL 条件 coin >= ? 保证余额不为负，二者都不依赖应用层版本号。
 */
@Service
@RequiredArgsConstructor
public class WalletService {

  private final WalletMapper walletMapper;
  private final RewardAccountQuery accounts;
  private final LevelService levelService;

  /**
   * 读取用户钱包，不存在时惰性初始化，用于兼容钱包上线前已有经验值的老用户。
   *
   * <p>初始化口径：xp 取 identity 契约返回的 sys_user.exp（用户不存在或 exp 为 null 时按 0），coin 固定 0，level 由
   * LevelService#levelOf(xp) 推导；不写任何流水。
   *
   * <p>幂等与并发：uk_wallet_user 保证一人一行；并发首次访问时后插入者捕获 DuplicateKeyException，再用 FOR UPDATE
   * 回读既有行，只有回读仍为空才把异常抛出。
   *
   * @param userId 钱包归属用户 ID，由调用方从认证上下文取得
   * @return 已存在或刚创建的钱包实体；重复调用返回同一行
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

  /**
   * 组装钱包展示 VO：xp / coin / level 直接取当前钱包行，nextLevelExp 由 LevelService 按累计 xp 推导，unlimited 取服务端角色。
   *
   * <p>虽是读接口，但会调用 getOrCreate，首次访问有创建钱包行的副作用；RewardWalletIntegrationTest 已验证老用户 xp 种子与 ADMIN
   * 无限能力口径。
   *
   * @param userId 当前登录用户 ID，来自 Controller 的 SecurityUtils
   * @return 不含流水明细的余额视图，unlimited 为 true 表示购买时不扣 Coin
   */
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

  /** 返回当前金币余额；钱包缺失时会先按 getOrCreate 规则初始化，因此不是无副作用的纯读取。 */
  public int coinBalance(Long userId) {
    return getOrCreate(userId).getCoin();
  }

  /**
   * 返回是否拥有不扣 Coin 的无限消费能力；取值来自 RewardAccountQuery 读取的 sys_user.role，仅 ADMIN 为 true，用户不存在时为 false。
   */
  public boolean isUnlimited(Long userId) {
    return accounts.find(userId).unlimited();
  }

  /**
   * 加币便捷入口：先锁钱包行再按增量更新，返回更新后的 Wallet 供调用方写 balance_after。
   *
   * <p>当前生产代码没有本方法的调用点：RewardEngine 为保障限额检查与流水追加同处一个临界区，直接组合 lockForReward 与
   * creditLocked；本方法保留给一次性入账场景，调用方仍须自行追加流水。
   *
   * @param userId 钱包归属用户 ID
   * @param xp 成长值增量，必须 >= 0
   * @param coin 金币增量，必须 >= 0
   * @return 更新后的钱包实体
   * @throws IllegalArgumentException xp 或 coin 为负时抛出
   */
  @Transactional
  public Wallet credit(Long userId, int xp, int coin) {
    return creditLocked(lockForReward(userId), xp, coin);
  }

  /**
   * 在调用方事务内锁定用户钱包行，供 RewardEngine 在同一临界区完成限额统计、幂等判定与余额更新。
   *
   * <p>先 getOrCreate 保证行存在，再以 SELECT ... FOR UPDATE 重新读取最新余额；行锁持续到调用方事务结束，不是到本方法返回。
   *
   * @return 已加锁且含最新余额的钱包行
   * @throws IllegalStateException 初始化后仍取不到钱包行时抛出
   */
  Wallet lockForReward(Long userId) {
    getOrCreate(userId);
    Wallet wallet = selectForUpdate(userId);
    if (wallet == null) {
      throw new IllegalStateException("Wallet missing after initialization");
    }
    return wallet;
  }

  /** 以 SELECT ... FOR UPDATE 读取钱包行；MyBatis-Plus 按 @TableLogic 追加 deleted = 0，行锁由数据库在事务结束时释放。 */
  private Wallet selectForUpdate(Long userId) {
    return walletMapper.selectOne(
        new LambdaQueryWrapper<Wallet>().eq(Wallet::getUserId, userId).last("FOR UPDATE"));
  }

  /**
   * 更新调用方已加锁的钱包行：xp / coin 用 Math.addExact 累加防溢出，level 由新 xp 重新推导并覆盖写回。
   *
   * <p>要求 updateById 恰好影响 1 行，否则抛异常让整个发奖事务回滚；本方法只改余额，不追加流水。
   *
   * @param wallet 已由 lockForReward 锁定的钱包实体，方法会就地修改其字段
   * @param xp 成长值增量，必须 >= 0
   * @param coin 金币增量，必须 >= 0
   * @return 同一个 wallet 实例，字段已更新为余额快照
   * @throws IllegalArgumentException 增量为负时抛出
   * @throws ArithmeticException 累加超过 int 上限时由 Math.addExact 抛出
   * @throws IllegalStateException 更新行数不为 1 时抛出
   */
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
   * 条件扣款：用 SQL 的 coin >= ? 保证余额不为负，返回是否扣款成功。
   *
   * <p>只允许正数金额；钱包缺失时先初始化。影响行数 0 即余额不足，由 PurchaseService 转成 INSUFFICIENT_BALANCE 业务异常；本方法不追加 DEBIT
   * 流水，流水与订单在同一购买事务内由 PurchaseService 写入。
   *
   * @param userId 钱包归属用户 ID
   * @param coin 扣减的金币数量，必须 > 0
   * @return true 表示已扣减一行，false 表示余额不足且余额未变化
   * @throws IllegalArgumentException coin <= 0 时抛出
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
