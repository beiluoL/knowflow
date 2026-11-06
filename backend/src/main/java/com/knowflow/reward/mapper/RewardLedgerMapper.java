package com.knowflow.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.reward.entity.RewardLedger;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 奖励流水 Mapper：reward_ledger 表的基础 CRUD，外加一条按事件去重的当日奖励计数 SQL。
 *
 * <p>调用方 RewardLedgerService：insert 追加流水、selectCount 做幂等预检、selectPage 分页、countRewardEvents 支撑
 * RewardRule#dailyLimit；表是 append-only，本接口没有 update 或 delete 语句。
 *
 * <p>实体继承 BaseEntity，BaseMapper 查询自动追加 deleted=0；原生 {@code @Select} 不经过该机制，需自己写 deleted = 0。唯一索引
 * uk_ledger_idempotent(user_id, source_type, source_id, currency) 是并发幂等的最终防线。
 */
@Mapper
public interface RewardLedgerMapper extends BaseMapper<RewardLedger> {
  /**
   * 统计某用户在指定区间内、某原因已发放的奖励「事件数」，供 RewardRule#dailyLimit 判定是否已达当日上限。
   *
   * <p>SQL 口径：COUNT(DISTINCT source_id)，只计 source_type = 'EVENT'、direction = 'CREDIT'、deleted = 0，且
   * create_time 落在 [start, end) 半开区间；一次奖励产生 XP + COIN 两行仍只算 1 次，PURCHASE 与 COMPENSATION 行不计入。
   *
   * <p>区间由 RewardLedgerService#dailyCount 按服务端本地自然日展开为当日 00:00 至次日 00:00；原生 SQL 不走
   * {@code @TableLogic}，故显式带 deleted = 0。
   *
   * @param userId 流水归属用户 ID
   * @param reason 事件类型名（如 CHECK_IN），不是用户可读文案
   * @param start 区间起点（含），通常为当日 00:00
   * @param end 区间终点（不含），通常为次日 00:00
   * @return 去重后的事件数，无记录为 0
   */
  @Select(
      """
      SELECT COUNT(DISTINCT source_id) FROM reward_ledger
      WHERE user_id = #{userId} AND reason = #{reason} AND source_type = 'EVENT'
        AND direction = 'CREDIT' AND deleted = 0
        AND create_time >= #{start} AND create_time < #{end}
      """)
  long countRewardEvents(
      @Param("userId") Long userId,
      @Param("reason") String reason,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);
}
