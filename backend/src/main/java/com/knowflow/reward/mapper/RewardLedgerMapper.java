package com.knowflow.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.reward.entity.RewardLedger;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** RewardLedger 表数据访问层。 */
@Mapper
public interface RewardLedgerMapper extends BaseMapper<RewardLedger> {
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
