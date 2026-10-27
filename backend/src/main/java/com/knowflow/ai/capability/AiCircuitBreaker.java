package com.knowflow.ai.capability;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 每作用域（provider:model）独立的轻量熔断器。
 *
 * <p>状态机：CLOSED（正常）→ 滑动窗口内失败达阈值 → OPEN（快速失败）→ 冷却到期 → HALF_OPEN（放一次探测）
 * → 成功回到 CLOSED / 失败重新 OPEN。
 *
 * <p>纯内存实现，不依赖 Resilience4j；面向单实例教学环境。多实例部署时各节点独立熔断，
 * 不影响正确性（供应商故障时每个节点都会独立打开），只影响粒度。
 */
@Component
@RequiredArgsConstructor
public class AiCircuitBreaker {

    private final AiCapabilityProperties props;
    private final ConcurrentHashMap<String, Breaker> breakers = new ConcurrentHashMap<>();

    /** 请求准入结果。 */
    public enum Decision { ALLOW, REJECT_OPEN }

    public Decision allow(String scope) {
        Breaker b = breakers.computeIfAbsent(scope, k -> new Breaker());
        synchronized (b) {
            long now = System.currentTimeMillis();
            if (b.openUntil > now) {
                return Decision.REJECT_OPEN;
            }
            if (b.openUntil > 0 && b.openUntil <= now) {
                // 冷却结束：进入半开，只放行这一次探测
                b.halfOpen = true;
            }
            return Decision.ALLOW;
        }
    }

    public void recordSuccess(String scope) {
        Breaker b = breakers.get(scope);
        if (b == null) {
            return;
        }
        synchronized (b) {
            b.windowStart = 0;
            b.failures = 0;
            b.openUntil = 0;
            b.halfOpen = false;
        }
    }

    public void recordFailure(String scope) {
        Breaker b = breakers.computeIfAbsent(scope, k -> new Breaker());
        synchronized (b) {
            long now = System.currentTimeMillis();
            if (b.halfOpen) {
                // 探测失败：重新熔断
                trip(b, now);
                return;
            }
            if (b.windowStart == 0 || now - b.windowStart > props.getCircuitWindowMs()) {
                b.windowStart = now;
                b.failures = 1;
            } else {
                b.failures++;
            }
            if (b.failures >= props.getCircuitFailureThreshold()) {
                trip(b, now);
            }
        }
    }

    private void trip(Breaker b, long now) {
        b.openUntil = now + props.getCircuitOpenMs();
        b.windowStart = 0;
        b.failures = 0;
        b.halfOpen = false;
    }

    /** 熔断是否处于打开状态（状态查询用）。 */
    public boolean isOpen(String scope) {
        Breaker b = breakers.get(scope);
        if (b == null) {
            return false;
        }
        synchronized (b) {
            return b.openUntil > System.currentTimeMillis();
        }
    }

    /** 测试辅助：清空全部熔断状态。 */
    public void reset() {
        breakers.clear();
    }

    private static class Breaker {
        long windowStart;
        int failures;
        long openUntil;
        boolean halfOpen;
    }
}
