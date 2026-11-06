package com.knowflow.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.reward.entity.Wallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 钱包 Mapper：wallet 表的基础 CRUD，外加一条条件扣款的原生更新 SQL。
 *
 * <p>调用方 WalletService 用 selectOne / insert / updateById 维护余额快照，用 debitCoin 支撑购买路径的条件扣 Coin；加币走
 * Service 层 FOR UPDATE 行锁后的 updateById，不在本接口另写 SQL。
 *
 * <p>唯一索引 uk_wallet_user(user_id) 不含 deleted；实体继承 BaseEntity，BaseMapper 查询自动追加 deleted=0，但原生
 * {@code @Update} 必须自己在 SQL 里写 deleted = 0。
 */
@Mapper
public interface WalletMapper extends BaseMapper<Wallet> {
  /**
   * 条件扣减金币：仅当目标行未逻辑删除且 coin 不小于扣款额时，才把 coin 减去 amount 并刷新 update_time。
   *
   * <p>「余额不为负」由单条 UPDATE 的 WHERE 条件原子保证，不依赖应用层先读后写；调用方 WalletService 把影响行数 0 解释为余额不足并转成业务异常。
   *
   * <p>本语句只改余额，不追加 reward_ledger 流水；流水与订单由 PurchaseService 在同一购买事务内写入。原生 SQL 不走
   * {@code @TableLogic}，所以 SQL 显式带 deleted = 0。
   *
   * @param userId 钱包归属用户 ID，由调用方从认证上下文取得
   * @param amount 扣减的金币数量，调用方保证为正数
   * @return 受影响行数：1 表示已扣款，0 表示行不存在、已软删或余额不足，此时余额未变化
   */
  @Update(
      """
      UPDATE wallet SET coin = coin - #{amount}, update_time = CURRENT_TIMESTAMP
      WHERE user_id = #{userId} AND deleted = 0 AND coin >= #{amount}
      """)
  int debitCoin(@Param("userId") Long userId, @Param("amount") int amount);
}
