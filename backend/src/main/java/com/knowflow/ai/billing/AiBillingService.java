package com.knowflow.ai.billing;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.knowflow.ai.capability.AiCapabilityException;
import com.knowflow.ai.capability.AiCapabilityProperties;
import com.knowflow.ai.capability.AiFailureKind;
import com.knowflow.ai.entity.AiBillingEntry;
import com.knowflow.ai.mapper.AiBillingEntryMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 按 UTC 自然日预留并结算同步模型尝试。 */
@Service
@RequiredArgsConstructor
public class AiBillingService {
  private final AiBillingEntryMapper entries;
  private final AiCapabilityProperties properties;

  /** 在供应商调用前原子检查用户与场景额度，任一拒绝则整笔回滚。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Reservation reserve(Long userId, String scene, String scope) {
    long userLimit = userId == null ? 0 : properties.getUserDailyHardTokenLimit();
    long sceneLimit = properties.getSceneDailyHardTokenLimits().getOrDefault(scene, 0L);
    long tokens = 0;
    if (userLimit > 0 || sceneLimit > 0) {
      tokens = properties.getModelAttemptTokenBounds().getOrDefault(scope, 0L);
      if (tokens <= 0) {
        throw new AiCapabilityException(AiFailureKind.BUDGET_EXCEEDED, "模型未配置单次 Token 上限");
      }
    }
    LocalDate day = LocalDate.now(ZoneOffset.UTC);
    String userKey = userLimit > 0 ? "user:" + userId : null;
    String sceneKey = sceneLimit > 0 ? "scene:" + scene : null;
    reserveDimension(day, userKey, userLimit, tokens);
    reserveDimension(day, sceneKey, sceneLimit, tokens);

    var entry = new AiBillingEntry();
    entry.setId(UUID.randomUUID().toString());
    entry.setUsageDay(day);
    entry.setUserId(userId);
    entry.setScene(scene);
    entry.setModelScope(scope);
    entry.setReservedTokens(tokens);
    entry.setStatus("RESERVED");
    var price = properties.getSplitPrices().get(scope);
    if (price != null) {
      entry.setInputUsdPerMillion(price.inputUsdPerMillion());
      entry.setOutputUsdPerMillion(price.outputUsdPerMillion());
    }
    entries.insert(entry);
    return new Reservation(
        entry.getId(),
        day,
        userKey,
        sceneKey,
        tokens,
        entry.getInputUsdPerMillion(),
        entry.getOutputUsdPerMillion());
  }

  /** 仅按供应商上报的输入输出结算，未知用量保留原额度预留。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void settle(Reservation reservation, Long input, Long output, boolean success) {
    boolean known = input != null && output != null && input >= 0 && output >= 0;
    BigDecimal cost = known ? price(reservation, input, output) : null;
    var update =
        new LambdaUpdateWrapper<AiBillingEntry>()
            .eq(AiBillingEntry::getId, reservation.id())
            .eq(AiBillingEntry::getStatus, "RESERVED")
            .set(
                AiBillingEntry::getStatus,
                known ? "SETTLED" : success ? "UNREPORTED" : "FAILED_UNKNOWN")
            .set(AiBillingEntry::getInputTokens, input)
            .set(AiBillingEntry::getOutputTokens, output)
            .set(AiBillingEntry::getCostUsd, cost);
    if (entries.update(null, update) != 1 || !known) {
      return;
    }
    long delta = Math.subtractExact(Math.addExact(input, output), reservation.tokens());
    adjust(reservation.day(), reservation.userKey(), delta);
    adjust(reservation.day(), reservation.sceneKey(), delta);
  }

  private void reserveDimension(LocalDate day, String key, long limit, long tokens) {
    if (key == null) return;
    entries.initializeQuota(day, key);
    if (tokens > limit || entries.adjustQuota(day, key, tokens, limit - tokens) != 1) {
      throw new AiCapabilityException(AiFailureKind.BUDGET_EXCEEDED, "每日额度不足");
    }
  }

  private void adjust(LocalDate day, String key, long delta) {
    if (key != null && delta != 0 && entries.adjustQuota(day, key, delta, Long.MAX_VALUE) != 1) {
      throw new IllegalStateException("Quota reservation row missing");
    }
  }

  private BigDecimal price(Reservation reservation, long input, long output) {
    var inputRate = reservation.inputRate();
    var outputRate = reservation.outputRate();
    if (inputRate == null
        || outputRate == null
        || inputRate.signum() < 0
        || outputRate.signum() < 0) {
      return null;
    }
    return inputRate
        .multiply(BigDecimal.valueOf(input))
        .add(outputRate.multiply(BigDecimal.valueOf(output)))
        .movePointLeft(6);
  }

  public record Reservation(
      String id,
      LocalDate day,
      String userKey,
      String sceneKey,
      long tokens,
      BigDecimal inputRate,
      BigDecimal outputRate) {}
}
