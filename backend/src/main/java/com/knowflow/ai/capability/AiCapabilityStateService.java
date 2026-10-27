package com.knowflow.ai.capability;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 能力状态聚合服务。
 *
 * <p>状态证据来源（不发起任何付费健康检查）：
 * <ol>
 *   <li>系统/用户配置是否存在（UNCONFIGURED / DISABLED）；</li>
 *   <li>熔断器实时状态（UNAVAILABLE）；</li>
 *   <li>执行器记录的近期瞬时失败（RATE_LIMITED / TIMEOUT / DEGRADED），带 TTL 自动过期。</li>
 * </ol>
 * 前端轮询本状态是免费的——状态接口有秒级缓存，且底层不调用模型。
 */
@Service
@RequiredArgsConstructor
public class AiCapabilityStateService {

    private final AiCapabilityProperties props;
    private final AiCircuitBreaker circuitBreaker;

    /** scope → 最近一次瞬时失败（带时间戳）。 */
    private final ConcurrentHashMap<String, TransientFailure> recent = new ConcurrentHashMap<>();

    public void recordTransient(String scope, AiFailureKind kind) {
        if (scope == null || kind == null) {
            return;
        }
        recent.put(scope, new TransientFailure(kind, System.currentTimeMillis()));
    }

    /**
     * 计算当前能力状态。
     *
     * @param configured 当前生效配置是否有效（系统级或用户级 Key）
     * @param userDisabled 用户是否主动关闭 AI
     * @param scope      熔断/失败统计作用域
     */
    public AiCapabilityStatus currentStatus(boolean configured, boolean userDisabled, String scope) {
        if (!props.isEnabled()) {
            return AiCapabilityStatus.DISABLED;
        }
        if (userDisabled) {
            return AiCapabilityStatus.DISABLED;
        }
        if (!configured) {
            return AiCapabilityStatus.UNCONFIGURED;
        }
        if (scope != null && circuitBreaker.isOpen(scope)) {
            return AiCapabilityStatus.UNAVAILABLE;
        }
        TransientFailure f = recent.get(scope);
        if (f != null) {
            long age = System.currentTimeMillis() - f.at;
            if (age > (long) props.getTransientStateTtlSeconds() * 1000) {
                recent.remove(scope, f);
            } else {
                return switch (f.kind) {
                    case RATE_LIMITED, CONCURRENCY_LIMITED -> AiCapabilityStatus.RATE_LIMITED;
                    case TIMEOUT, NETWORK_ERROR -> AiCapabilityStatus.TIMEOUT;
                    case QUOTA_EXHAUSTED, INVALID_KEY, INVALID_REQUEST, INVALID_RESPONSE,
                         UNSUPPORTED_CAPABILITY, PROVIDER_ERROR, UNKNOWN,
                         CIRCUIT_OPEN, BUDGET_EXCEEDED -> AiCapabilityStatus.DEGRADED;
                    default -> AiCapabilityStatus.AVAILABLE;
                };
            }
        }
        return AiCapabilityStatus.AVAILABLE;
    }

    /** 最近失败原因（仅枚举名，供监控/调试，不下发内部细节）。 */
    public String recentFailureKind(String scope) {
        TransientFailure f = recent.get(scope);
        if (f == null) {
            return null;
        }
        if (System.currentTimeMillis() - f.at > (long) props.getTransientStateTtlSeconds() * 1000) {
            return null;
        }
        return f.kind.name();
    }

    /** 测试辅助。 */
    public void reset() {
        recent.clear();
    }

    private record TransientFailure(AiFailureKind kind, long at) {
    }
}
