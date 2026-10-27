package com.knowflow.ai.capability;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 能力调用执行器（Facade 内核）：统一负责超时外的可靠性策略。
 *
 * <p>职责：系统开关 → 熔断准入 → 并发限流 → token 预算 → 有界重试（指数退避 + 抖动，
 * 尊重 429 Retry-After）→ 结果/失败记账。单次 HTTP 超时由 Provider 客户端自身保证，
 * 本执行器负责的是跨重试的总体可靠性与资源护栏。
 *
 * <p>硬规则：
 * <ul>
 *   <li>401/403/400/非法响应/额度不足永不重试；</li>
 *   <li>429/5xx/超时/网络错误最多重试 {@code maxAttempts-1} 次；</li>
 *   <li>熔断打开时不发起任何真实调用；</li>
 *   <li>任何失败都向上抛 {@link AiCapabilityException}，由业务层定义降级，
 *       执行器自身绝不生成假内容、假分数。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCapabilityExecutor {

    private final AiCapabilityProperties props;
    private final AiCircuitBreaker circuitBreaker;
    private final AiCapabilityStateService stateService;

    private Semaphore concurrency = new Semaphore(8);
    private final AtomicLong tokensUsedToday = new AtomicLong(0);
    private volatile String budgetDay = "";

    @PostConstruct
    void init() {
        this.concurrency = new Semaphore(Math.max(1, props.getMaxConcurrent()));
    }

    /**
     * 执行一次受保护的 AI 调用。
     *
     * @param scope       熔断/统计作用域，建议 provider:model
     * @param userDisabled 当前用户是否主动关闭了 AI（true 直接 USER_DISABLED）
     * @param call        真实单次调用（内部应已设置请求超时）
     */
    public AiRawResult execute(String scope, boolean userDisabled, AiCall call) {
        if (!props.isEnabled()) {
            throw new AiCapabilityException(AiFailureKind.SYSTEM_DISABLED, null);
        }
        if (userDisabled) {
            throw new AiCapabilityException(AiFailureKind.USER_DISABLED, null);
        }
        if (circuitBreaker.allow(scope) == AiCircuitBreaker.Decision.REJECT_OPEN) {
            throw new AiCapabilityException(AiFailureKind.CIRCUIT_OPEN, "scope=" + scope);
        }
        checkDailyBudget();

        AiCapabilityException last = null;
        int maxAttempts = Math.max(1, props.getMaxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean acquired;
            try {
                acquired = concurrency.tryAcquire(props.getAcquireTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiCapabilityException(AiFailureKind.CANCELLED, "等待并发槽位时被中断");
            }
            if (!acquired) {
                last = new AiCapabilityException(AiFailureKind.CONCURRENCY_LIMITED, null);
                if (!sleepBeforeRetry(scope, attempt, maxAttempts, last)) {
                    break;
                }
                continue;
            }
            try {
                // 排队或退避期间可能关停：每次真正调用前重新准入，不撤回已发出的请求。
                if (!props.isEnabled()) throw new AiCapabilityException(AiFailureKind.SYSTEM_DISABLED, null);
                AiRawResult result = call.invoke();
                circuitBreaker.recordSuccess(scope);
                if (result.totalTokens() > 0) {
                    tokensUsedToday.addAndGet(result.totalTokens());
                }
                if (attempt > 1) {
                    log.info("AI 调用重试成功 scope={} attempts={}", scope, attempt);
                }
                return result;
            } catch (AiCapabilityException e) {
                // 能力关停属于准入拒绝，不是供应商失败；finally 仍会释放并发槽位。
                if (e.getKind() == AiFailureKind.SYSTEM_DISABLED || e.getKind() == AiFailureKind.USER_DISABLED) throw e;
                last = e;
                circuitBreaker.recordFailure(scope);
                stateService.recordTransient(scope, e.getKind());
                log.warn("AI 调用失败 scope={} attempt={}/{} kind={} retryable={}",
                        scope, attempt, maxAttempts, e.getKind(), e.retryable());
                if (!e.retryable() || !sleepBeforeRetry(scope, attempt, maxAttempts, e)) {
                    break;
                }
            } catch (Exception e) {
                // 未分类异常：保守视为不可重试，不消耗额度
                last = new AiCapabilityException(AiFailureKind.UNKNOWN,
                        e.getClass().getSimpleName() + ": " + e.getMessage());
                circuitBreaker.recordFailure(scope);
                stateService.recordTransient(scope, AiFailureKind.UNKNOWN);
                log.warn("AI 调用未分类异常 scope={} attempt={}", scope, attempt, e);
                break;
            } finally {
                concurrency.release();
            }
        }
        throw last != null ? last : new AiCapabilityException(AiFailureKind.UNKNOWN, "执行器无结果");
    }

    private void checkDailyBudget() {
        long limit = props.getDailyTokenLimit();
        if (limit <= 0) {
            return;
        }
        String today = java.time.LocalDate.now().toString();
        if (!today.equals(budgetDay)) {
            synchronized (this) {
                if (!today.equals(budgetDay)) {
                    budgetDay = today;
                    tokensUsedToday.set(0);
                }
            }
        }
        if (tokensUsedToday.get() >= limit) {
            throw new AiCapabilityException(AiFailureKind.BUDGET_EXCEEDED,
                    "limit=" + limit);
        }
    }

    /**
     * @return true 表示已退避、可以继续下一次重试；false 表示应立即结束
     */
    private boolean sleepBeforeRetry(String scope, int attempt, int maxAttempts, AiCapabilityException cause) {
        if (attempt >= maxAttempts) {
            return false;
        }
        long wait;
        Long retryAfter = cause.getRetryAfterMs();
        if (retryAfter != null && retryAfter > 0) {
            wait = Math.min(retryAfter, props.getMaxBackoffMs());
        } else {
            long base = props.getInitialBackoffMs() * (1L << (attempt - 1));
            long capped = Math.min(base, props.getMaxBackoffMs());
            // ±20% 抖动，避免故障恢复瞬间重试风暴
            double jitter = 0.8 + Math.random() * 0.4;
            wait = (long) (capped * jitter);
        }
        try {
            Thread.sleep(wait);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiCapabilityException(AiFailureKind.CANCELLED, "退避等待时被中断");
        }
    }

    /** 监控/测试用：今日已用 token。 */
    public long tokensUsedToday() {
        return tokensUsedToday.get();
    }

    /** 测试辅助：重置预算计数。 */
    public void resetBudget() {
        tokensUsedToday.set(0);
    }

    /** 受保护的单次调用。 */
    @FunctionalInterface
    public interface AiCall {
        AiRawResult invoke() throws Exception;
    }
}
