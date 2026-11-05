package com.knowflow.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/** 一次同步模型尝试的额度预留与计费快照。 */
@Data
@TableName("ai_billing_entry")
public class AiBillingEntry {
  @TableId(type = IdType.INPUT)
  private String id;

  private LocalDate usageDay;
  private Long userId;
  private String scene;
  private String modelScope;
  private long reservedTokens;
  private String status;
  private Long inputTokens;
  private Long outputTokens;
  private BigDecimal inputUsdPerMillion;
  private BigDecimal outputUsdPerMillion;
  private BigDecimal costUsd;
}
