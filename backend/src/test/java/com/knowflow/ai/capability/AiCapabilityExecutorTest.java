package com.knowflow.ai.capability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3H：AI 能力执行器故障注入单元测试（纯 JUnit，无 Spring 上下文、无真实网络）。
 *
 * <p>覆盖任务书故障注入场景中执行器层职责：401 不重试、429 有界重试（Retry-After）、
 * 5xx 重试、超时分类、熔断打开、并发限制、预算护栏、用户/系统关闭、非法响应不重试。</p>
 */
class AiCapabilityExecutorTest {

    private AiCapabilityProperties props;
    private AiCircuitBreaker breaker;
    private AiCapabilityStateService stateService;
    private AiCapabilityExecutor executor;

    @BeforeEach
    void setUp() {
        props = new AiCapabilityProperties();
        props.setEnabled(true);
        props.setMaxAttempts(3);
        props.setInitialBackoffMs(1);
        props.setMaxBackoffMs(2);
        props.setMaxConcurrent(8);
        props.setAcquireTimeoutMs(50);
        props.setCircuitWindowMs(60_000);
        props.setCircuitFailureThreshold(6);
        props.setCircuitOpenMs(30_000);
        props.setDailyTokenLimit(0);
        props.setTransientStateTtlSeconds(60);
        breaker = new AiCircuitBreaker(props);
        stateService = new AiCapabilityStateService(props, breaker);
        executor = new AiCapabilityExecutor(props, breaker, stateService);
        executor.init();
    }

    @AfterEach
    void tearDown() {
        breaker.reset();
        stateService.reset();
        executor.resetBudget();
    }

    private AiCapabilityException fail(AiFailureKind kind) {
        return new AiCapabilityException(kind, "inject");
    }

    private AiCapabilityException failRetryAfter(AiFailureKind kind, long retryAfterMs) {
        return new AiCapabilityException(kind, "inject", retryAfterMs);
    }

    @Test
    @DisplayName("成功调用返回结果并累计 token")
    void successReturnsAndCountsTokens() {
        AiRawResult r = executor.execute("p:m", false, () -> new AiRawResult("ok", 120));
        assertEquals("ok", r.content());
        assertEquals(120, executor.tokensUsedToday());
        assertFalse(breaker.isOpen("p:m"));
    }

    @Test
    @DisplayName("场景2：401 INVALID_KEY 不可重试，只调用 1 次")
    void invalidKeyNoRetry() {
        AtomicInteger calls = new AtomicInteger();
        AiCapabilityException ex = assertThrows(AiCapabilityException.class, () ->
                executor.execute("p:m", false, () -> {
                    calls.incrementAndGet();
                    throw fail(AiFailureKind.INVALID_KEY);
                }));
        assertEquals(AiFailureKind.INVALID_KEY, ex.getKind());
        assertEquals(1, calls.get(), "401/无效 Key 绝不能重试消耗额度");
    }

    @Test
    @DisplayName("场景7：INVALID_RESPONSE 非法返回结构不可重试")
    void invalidResponseNoRetry() {
        AtomicInteger calls = new AtomicInteger();
        AiCapabilityException ex = assertThrows(AiCapabilityException.class, () ->
                executor.execute("p:m", false, () -> {
                    calls.incrementAndGet();
                    throw fail(AiFailureKind.INVALID_RESPONSE);
                }));
        assertEquals(AiFailureKind.INVALID_RESPONSE, ex.getKind());
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("场景3：429 RATE_LIMITED 按 Retry-After 有界重试，最多 3 次后抛出")
    void rateLimitedBoundedRetry() {
        AtomicInteger calls = new AtomicInteger();
        AiCapabilityException ex = assertThrows(AiCapabilityException.class, () ->
                executor.execute("p:m", false, () -> {
                    calls.incrementAndGet();
                    throw failRetryAfter(AiFailureKind.RATE_LIMITED, 1L);
                }));
        assertEquals(AiFailureKind.RATE_LIMITED, ex.getKind());
        assertEquals(3, calls.get(), "重试必须有界（maxAttempts=3）");
    }

    @Test
    @DisplayName("场景4：供应商 5xx 指数退避重试 3 次")
    void providerErrorBoundedRetry() {
        AtomicInteger calls = new AtomicInteger();
        AiCapabilityException ex = assertThrows(AiCapabilityException.class, () ->
                executor.execute("p:m", false, () -> {
                    calls.incrementAndGet();
                    throw fail(AiFailureKind.PROVIDER_ERROR);
                }));
        assertEquals(AiFailureKind.PROVIDER_ERROR, ex.getKind());
        assertEquals(3, calls.get());
    }

    @Test
    @DisplayName("场景5：TIMEOUT 可重试，且第二次成功后熔断恢复正常")
    void timeoutRetryThenSuccess() {
        AtomicInteger calls = new AtomicInteger();
        AiRawResult r = executor.execute("p:m", false, () -> {
            int n = calls.incrementAndGet();
            if (n == 1) {
                throw fail(AiFailureKind.TIMEOUT);
            }
            return new AiRawResult("recovered", 0);
        });
        assertEquals(2, calls.get());
        assertEquals("recovered", r.content());
        assertFalse(breaker.isOpen("p:m"), "重试成功应关闭熔断窗口");
    }

    @Test
    @DisplayName("熔断：窗口内失败达阈值后 OPEN，后续请求快速失败且不调用模型")
    void circuitOpensAfterThreshold() {
        props.setMaxAttempts(1);
        String scope = "p:m-cb";
        for (int i = 0; i < props.getCircuitFailureThreshold(); i++) {
            assertThrows(AiCapabilityException.class, () ->
                    executor.execute(scope, false, () -> {
                        throw fail(AiFailureKind.PROVIDER_ERROR);
                    }));
        }
        assertTrue(breaker.isOpen(scope));
        AtomicInteger calls = new AtomicInteger();
        AiCapabilityException ex = assertThrows(AiCapabilityException.class, () ->
                executor.execute(scope, false, () -> {
                    calls.incrementAndGet();
                    return new AiRawResult("should-never-happen", 0);
                }));
        assertEquals(AiFailureKind.CIRCUIT_OPEN, ex.getKind());
        assertEquals(0, calls.get(), "熔断打开时禁止发起真实付费调用");
    }

    @Test
    @DisplayName("场景11：用户主动关闭 AI → USER_DISABLED，不调用模型")
    void userDisabledShortCircuit() {
        AtomicInteger calls = new AtomicInteger();
        AiCapabilityException ex = assertThrows(AiCapabilityException.class, () ->
                executor.execute("p:m", true, () -> {
                    calls.incrementAndGet();
                    return new AiRawResult("x", 0);
                }));
        assertEquals(AiFailureKind.USER_DISABLED, ex.getKind());
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("系统开关关闭 → SYSTEM_DISABLED，不调用模型")
    void systemDisabledShortCircuit() {
        props.setEnabled(false);
        AtomicInteger calls = new AtomicInteger();
        AiCapabilityException ex = assertThrows(AiCapabilityException.class, () ->
                executor.execute("p:m", false, () -> {
                    calls.incrementAndGet();
                    return new AiRawResult("x", 0);
                }));
        assertEquals(AiFailureKind.SYSTEM_DISABLED, ex.getKind());
        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("token 日预算超限 → BUDGET_EXCEEDED 快速失败")
    void dailyBudgetGuards() {
        props.setDailyTokenLimit(100);
        executor.execute("p:m", false, () -> new AiRawResult("first", 200));
        AiCapabilityException ex = assertThrows(AiCapabilityException.class, () ->
                executor.execute("p:m", false, () -> new AiRawResult("second", 1)));
        assertEquals(AiFailureKind.BUDGET_EXCEEDED, ex.getKind());
    }

    @Test
    @DisplayName("并发槽位占满时第二次调用得到 CONCURRENCY_LIMITED（有界尝试后失败）")
    void concurrencyLimit() throws Exception {
        props.setMaxConcurrent(1);
        props.setAcquireTimeoutMs(30);
        props.setMaxAttempts(1);
        executor.init();
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger outerResult = new AtomicInteger();
        Thread holder = new Thread(() -> {
            try {
                executor.execute("p:m", false, () -> {
                    inside.countDown();
                    release.await(5, TimeUnit.SECONDS);
                    return new AiRawResult("held", 0);
                });
                outerResult.set(1);
            } catch (Exception e) {
                outerResult.set(2);
            }
        });
        holder.start();
        assertTrue(inside.await(2, TimeUnit.SECONDS));
        try {
            AiCapabilityException ex = assertThrows(AiCapabilityException.class, () ->
                    executor.execute("p:m", false, () -> new AiRawResult("blocked", 0)));
            assertEquals(AiFailureKind.CONCURRENCY_LIMITED, ex.getKind());
        } finally {
            release.countDown();
            holder.join(2000);
        }
    }

    @Test
    @DisplayName("未分类异常保守归为 UNKNOWN 且不重试")
    void unknownExceptionNoRetry() {
        AtomicInteger calls = new AtomicInteger();
        AiCapabilityException ex = assertThrows(AiCapabilityException.class, () ->
                executor.execute("p:m", false, () -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("boom");
                }));
        assertEquals(AiFailureKind.UNKNOWN, ex.getKind());
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("能力状态：未配置/关闭/熔断/瞬时限流 与 TTL 过期恢复 AVAILABLE")
    void statusAggregationWithoutPaidProbe() throws Exception {
        assertEquals(AiCapabilityStatus.UNCONFIGURED,
                stateService.currentStatus(false, false, "s1"));
        assertEquals(AiCapabilityStatus.DISABLED,
                stateService.currentStatus(true, true, "s1"));
        props.setEnabled(false);
        assertEquals(AiCapabilityStatus.DISABLED,
                stateService.currentStatus(true, false, "s1"));
        props.setEnabled(true);

        breaker.recordFailure("s2");
        breaker.recordFailure("s2");
        assertEquals(AiCapabilityStatus.AVAILABLE,
                stateService.currentStatus(true, false, "s2"),
                "未达熔断阈值且无瞬时失败时应 AVAILABLE");

        stateService.recordTransient("s3", AiFailureKind.RATE_LIMITED);
        assertEquals(AiCapabilityStatus.RATE_LIMITED,
                stateService.currentStatus(true, false, "s3"));
        stateService.recordTransient("s4", AiFailureKind.TIMEOUT);
        assertEquals(AiCapabilityStatus.TIMEOUT,
                stateService.currentStatus(true, false, "s4"));

        props.setTransientStateTtlSeconds(-1);
        // 负 TTL 模拟记录早已过期（age 恒大于阈值）
        assertEquals(AiCapabilityStatus.AVAILABLE,
                stateService.currentStatus(true, false, "s3"),
                "瞬时状态必须按 TTL 自动恢复，不做付费健康检查");
        props.setTransientStateTtlSeconds(60);
    }
    @Test
    void disablingBetweenRetryAttemptsPreventsAnotherProviderCall() {
        AtomicInteger calls = new AtomicInteger();
        var ex = assertThrows(AiCapabilityException.class, () -> executor.execute("gate:retry", false, () -> {
            calls.incrementAndGet(); props.setEnabled(false);
            throw new AiCapabilityException(AiFailureKind.NETWORK_ERROR, "local failure");
        }));
        assertEquals(AiFailureKind.SYSTEM_DISABLED, ex.getKind());
        assertEquals(1, calls.get());
    }
    @Test
    void gateRefusalDoesNotOpenCircuitAndReenableRecovers() {
        props.setCircuitFailureThreshold(1);
        // 模拟初次准入后、拿到执行槽位前关闭开关；不需要真实网络或线程时序。
        var changingProps = org.mockito.Mockito.spy(props);
        org.mockito.Mockito.when(changingProps.isEnabled()).thenReturn(true, false);
        var gated = new AiCapabilityExecutor(changingProps, breaker, stateService);
        gated.init();
        AtomicInteger calls = new AtomicInteger();
        var ex = assertThrows(AiCapabilityException.class, () -> gated.execute("gate:queued", false, () -> {
            calls.incrementAndGet();
            return new AiRawResult("ok", 0);
        }));
        assertEquals(AiFailureKind.SYSTEM_DISABLED, ex.getKind());
        assertEquals(0, calls.get());
        assertFalse(breaker.isOpen("gate:queued"), "关停拒绝不是供应商故障");
        org.mockito.Mockito.when(changingProps.isEnabled()).thenReturn(true);
        assertEquals("reopened", gated.execute("gate:queued", false, () -> new AiRawResult("reopened", 0)).content());
    }

    @Test
    void gateRefusalInsideProviderBoundaryDoesNotCountAsProviderFailure() {
        props.setCircuitFailureThreshold(1);
        var ex = assertThrows(AiCapabilityException.class, () -> executor.execute("gate:boundary", false, () -> {
            // AiService 在最终网络边界也可能发现刚发生的关停。
            throw new AiCapabilityException(AiFailureKind.SYSTEM_DISABLED, null);
        }));
        assertEquals(AiFailureKind.SYSTEM_DISABLED, ex.getKind());
        assertFalse(breaker.isOpen("gate:boundary"));
    }

}
