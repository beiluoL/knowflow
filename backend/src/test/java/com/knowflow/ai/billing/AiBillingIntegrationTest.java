package com.knowflow.ai.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.knowflow.ai.capability.AiCapabilityException;
import com.knowflow.ai.capability.AiCapabilityProperties;
import com.knowflow.ai.capability.AiFailureKind;
import com.knowflow.ai.mapper.AiBillingEntryMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.knowflow.ai.service.billing.AiBillingService;
/**
 * 验证 AI 额度预留、并发限制、结算及取消用量处理。
 */
@SpringBootTest
@ActiveProfiles("local")
class AiBillingIntegrationTest {
  @DynamicPropertySource
  static void policy(DynamicPropertyRegistry registry) {
    registry.add("knowflow.ai.capability.user-daily-hard-token-limit", () -> 100);
    registry.add("knowflow.ai.capability.scene-daily-hard-token-limits.test", () -> 100);
    registry.add("knowflow.ai.capability.model-attempt-token-bounds.[p:m]", () -> 60);
    registry.add("knowflow.ai.capability.split-prices.[p:m].input-usd-per-million", () -> 2);
    registry.add("knowflow.ai.capability.split-prices.[p:m].output-usd-per-million", () -> 4);
  }

  @Autowired AiBillingService billing;
  @Autowired AiBillingEntryMapper entries;
  @Autowired AiCapabilityProperties properties;

  @Test
  void reservesBothDimensionsAndSettlesReportedUsageExactlyOnce() {
    assertEquals(60L, properties.getModelAttemptTokenBounds().get("p:m"));
    var first = billing.reserve(98701L, "test", "p:m");
    assertEquals(
        AiFailureKind.BUDGET_EXCEEDED,
        assertThrows(AiCapabilityException.class, () -> billing.reserve(98701L, "other", "p:m"))
            .getKind());
    assertEquals(
        AiFailureKind.BUDGET_EXCEEDED,
        assertThrows(AiCapabilityException.class, () -> billing.reserve(98702L, "test", "p:m"))
            .getKind());

    billing.settle(first, 10L, 20L, true);
    billing.settle(first, 0L, 0L, true);
    var row = entries.selectById(first.id());
    assertEquals("SETTLED", row.getStatus());
    assertEquals(10L, row.getInputTokens());
    assertEquals(20L, row.getOutputTokens());
    assertEquals(0, new BigDecimal("0.000100").compareTo(row.getCostUsd()));

    var second = billing.reserve(98702L, "test", "p:m");
    billing.settle(second, null, null, false);
    assertEquals("FAILED_UNKNOWN", entries.selectById(second.id()).getStatus());
    assertThrows(AiCapabilityException.class, () -> billing.reserve(98703L, "test", "p:m"));
  }

  @Test
  void onlyOneConcurrentAttemptFitsTheUserBudget() throws Exception {
    var start = new java.util.concurrent.CyclicBarrier(2);
    var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Callable<Boolean> reserve =
          () -> {
            start.await(5, java.util.concurrent.TimeUnit.SECONDS);
            try {
              billing.reserve(98711L, "concurrency", "p:m");
              return true;
            } catch (AiCapabilityException failure) {
              assertEquals(AiFailureKind.BUDGET_EXCEEDED, failure.getKind());
              return false;
            }
          };
      var first = workers.submit(reserve);
      var second = workers.submit(reserve);
      assertEquals(
          1,
          (first.get(15, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0)
              + (second.get(15, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0));
    } finally {
      workers.shutdownNow();
    }
  }

  @Test
  void cancelledUsageRemainsUnknownAndCanBeReadFromPersistentPage() {
    var reservation = billing.reserve(98721L, "cancelled", "p:m");
    billing.settle(reservation, null, null, false, true);
    var row =
        billing.page(1, 100).getRecords().stream()
            .filter(entry -> entry.id().equals(reservation.id()))
            .findFirst()
            .orElseThrow();
    assertEquals("CANCELLED_UNKNOWN", row.status());
    org.junit.jupiter.api.Assertions.assertNull(row.costUsd());
  }
}
