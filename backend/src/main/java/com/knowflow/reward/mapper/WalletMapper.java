package com.knowflow.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.reward.entity.Wallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Wallet 表数据访问层。 */
@Mapper
public interface WalletMapper extends BaseMapper<Wallet> {
  @Update(
      """
      UPDATE wallet SET coin = coin - #{amount}, update_time = CURRENT_TIMESTAMP
      WHERE user_id = #{userId} AND deleted = 0 AND coin >= #{amount}
      """)
  int debitCoin(@Param("userId") Long userId, @Param("amount") int amount);
}
