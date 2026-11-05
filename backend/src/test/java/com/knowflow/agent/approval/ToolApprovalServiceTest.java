package com.knowflow.agent.approval;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 验证工具审批的归属、一次消费及过期取消状态。
 */
class ToolApprovalServiceTest {
  private final ToolApprovalService approvals = new ToolApprovalService();

  @Test
  void onlyOwnerCanDecideAndApprovalCanBeConsumedOnce() {
    try (var scope = approvals.openScope(7)) {
      String id = scope.request(Duration.ofSeconds(30));
      assertFalse(approvals.decide(8, id, true));
      assertTrue(approvals.decide(7, id, true));
      assertFalse(approvals.decide(7, id, false));
      assertTrue(scope.await(id));
      assertFalse(scope.await(id));
      assertFalse(approvals.decide(7, id, true));
    }
  }

  @Test
  void rejectsExpiredCancelledAndDeniedRequests() {
    try (var scope = approvals.openScope(7)) {
      String expired = scope.request(Duration.ZERO);
      assertFalse(approvals.decide(7, expired, true));
      assertFalse(scope.await(expired));
      String denied = scope.request(Duration.ofSeconds(30));
      assertTrue(approvals.decide(7, denied, false));
      assertFalse(scope.await(denied));
      String cancelled = scope.request(Duration.ofSeconds(30));
      scope.close();
      assertFalse(approvals.decide(7, cancelled, true));
      assertFalse(scope.await(cancelled));
      assertNull(scope.request(Duration.ofSeconds(30)));
    }
  }
}
