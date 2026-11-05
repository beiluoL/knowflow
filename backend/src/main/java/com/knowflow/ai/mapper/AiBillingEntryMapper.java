package com.knowflow.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowflow.ai.entity.AiBillingEntry;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiBillingEntryMapper extends BaseMapper<AiBillingEntry> {
  @Insert(
      """
      INSERT INTO ai_quota_usage (usage_day, dimension_key, reserved_tokens)
      VALUES (#{day}, #{key}, 0)
      ON DUPLICATE KEY UPDATE reserved_tokens = reserved_tokens
      """)
  void initializeQuota(@Param("day") LocalDate day, @Param("key") String key);

  @Update(
      """
      UPDATE ai_quota_usage SET reserved_tokens = reserved_tokens + #{delta}
      WHERE usage_day = #{day} AND dimension_key = #{key}
        AND reserved_tokens <= #{ceiling}
      """)
  int adjustQuota(
      @Param("day") LocalDate day,
      @Param("key") String key,
      @Param("delta") long delta,
      @Param("ceiling") long ceiling);
}
